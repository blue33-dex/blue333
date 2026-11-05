# 对象存储技术概要 · SeaweedFS S3 Gateway

> **适用范围**：本地部署（单机 Docker Compose）
> **替代对象**：原任务文档 P1-T02 / P1-T14 中的 MinIO
> **决策日期**：2026-11-04

## 1. 为什么替换 MinIO

2026-11-02 实测：`minio/minio:latest` 拉取被拒绝（pull access denied），`quay.io/minio/minio:latest` 返回 401。MinIO 上游已不再提供可匿名拉取的社区镜像，原 Compose 配置无法启动。

LocalStack 也被排除：2026 年之后的镜像需要账号 Token，且它是测试模拟器，不适合存放业务文件。

SeaweedFS 提供兼容 S3 的网关，镜像可匿名拉取，单机即可运行，并能扩展到多节点。后端只依赖 S3 协议，所以订单、附件和后续的设计版本代码都不需要改动。

## 2. 部署结构

```text
浏览器 ── 预签名 PUT/GET ──▶ SeaweedFS S3 Gateway :8333
   │                              ▲
   └── /api ──▶ Nginx :80 ──▶ Spring Boot :8080 ── 内部 S3 调用 ──┘
                                   │
                                   └──▶ MySQL :3306
```

| 容器 | 镜像 | 作用 | 生命周期 |
|---|---|---|---|
| `seaweed-s3-config` | `alpine:3.20` | 生成 S3 身份文件 `s3.json` | 一次性，执行后退出 |
| `seaweedfs` | `chrislusf/seaweedfs:3.94` | `weed server -s3`：master、volume、filer、S3 网关合一 | 常驻 |
| `seaweed-s3-init` | `amazon/aws-cli:2.17.0` | 创建 Bucket（已存在则跳过） | 一次性，执行后退出 |
| `backend` | 本地构建 | 业务服务，签发预签名 URL | 常驻 |

启动顺序：`seaweed-s3-config` 完成 → `seaweedfs` 启动 → `seaweed-s3-init` 完成 → `backend` 启动。

数据卷：

- `ordermgmt-seaweed-data`：文件数据，需要纳入备份。
- `ordermgmt-seaweed-s3-config`：身份配置，每次启动时重新生成。

所有镜像都固定版本号，不使用 `latest`。

## 3. 访问控制

S3 网关只定义两个身份，没有匿名身份，未签名请求一律拒绝。

| 身份 | 使用者 | 权限 |
|---|---|---|
| `ordermgmt-admin` | 仅 `seaweed-s3-init` 初始化容器 | Admin、Read、List、Write、Tagging |
| `ordermgmt-backend` | Spring Boot 后端 | Read、List、Write、Tagging；不能创建或删除 Bucket |

浏览器从不接触任何密钥，只拿到后端签发的短期预签名 URL：

- 上传 PUT URL 有效期 15 分钟。
- 下载 GET URL 有效期 5 分钟。

下载接口先校验调用者对订单的访问权限，再签发 URL，然后返回 302 跳转。

## 4. 两个地址：内部地址与公开地址

| 变量 | 本地默认值 | 用途 |
|---|---|---|
| `S3_ENDPOINT` | `http://seaweedfs:8333` | 后端在容器内访问，用于确认对象是否存在、大小是否一致 |
| `S3_PUBLIC_ENDPOINT` | `http://localhost:8333` | 写进预签名 URL，浏览器必须能访问 |

预签名 URL 的签名包含主机名，所以必须用浏览器能访问的地址来签名。后端为此创建了两个客户端：

- `s3InternalClient`：执行 statObject 等服务端调用。
- `s3PresignClient`：生成预签名 URL。

两个客户端都固定了区域 `S3_REGION`（默认 `us-east-1`）。否则客户端会在签名前用公开地址查询 Bucket 所在区域，而容器内访问不到 `localhost:8333`。

部署到局域网服务器时，`S3_PUBLIC_ENDPOINT` 要改成员工电脑能访问的地址，建议使用 HTTPS 域名，例如 `https://storage.company.local`。

## 5. CORS

浏览器直传属于跨域请求，必须配置 CORS。SeaweedFS 3.94 不支持 `PutBucketCors`（返回 `NotImplemented`），所以 CORS 不在 Bucket 上配置，而是通过网关启动参数 `-s3.allowedOrigins` 设置：

- 允许来源：`S3_CORS_ALLOWED_ORIGINS`，默认值为 `http://localhost:5173`（Vite 开发服务器）和 `http://localhost`（Nginx）。不在列表中的来源，预检请求拿不到 `Access-Control-Allow-Origin`，浏览器会拦截。
- 对允许的来源，SeaweedFS 返回 `Access-Control-Allow-Origin: *`，方法和请求头不做限制。真正的访问控制靠预签名 URL 的签名和有效期，CORS 只决定哪些网页能发起请求。

部署时把前端实际使用的域名加入这个变量，然后重建 `seaweedfs` 容器（`docker compose up -d seaweedfs`）。

## 6. 上传与下载流程

```text
1. AM  POST /api/v1/orders/{id}/attachments/presign   {file_name, file_size}
       后端校验：订单归属、订单状态、文件格式白名单、200MB 上限
       返回 {presigned_url, object_key, expires_in_minutes}
2. 浏览器 PUT presigned_url                            直传 SeaweedFS，不经过后端
3. AM  POST /api/v1/orders/{id}/attachments          {file_name, file_size, object_key}
       后端校验：object_key 属于该订单；对象确实存在；实际大小与声明一致
       写入 attachments 表
4. 下载 GET /api/v1/attachments/{id}/download
       后端校验订单可见性 → 302 跳转到 5 分钟有效的预签名 GET URL
```

对象键格式为 `attachments/order-{orderId}/{UUID}.{扩展名}`，不使用用户提供的原始文件名，以防路径穿越。

## 7. 配置清单（`infra/.env`）

| 变量 | 说明 |
|---|---|
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | 后端身份 |
| `S3_ADMIN_ACCESS_KEY` / `S3_ADMIN_SECRET_KEY` | 初始化身份，必须与后端身份不同 |
| `S3_BUCKET` | 默认 `ordermgmt` |
| `S3_PUBLIC_ENDPOINT` | 浏览器可访问的 S3 地址 |
| `S3_CORS_ALLOWED_ORIGINS` | 允许直传的前端来源，逗号分隔 |
| `S3_REGION` | 可选，默认 `us-east-1` |

`infra/.env` 已被 `.gitignore` 排除，不会提交。`infra/.env.example` 中是可直接使用的**公开演示值**（课程作业），复制即可启动；所有端口只绑定 `127.0.0.1`。

## 8. 与原 MinIO 方案的差异

| 方面 | MinIO（原方案） | SeaweedFS（现方案） |
|---|---|---|
| 镜像可用性 | 已无法匿名拉取 | 可匿名拉取，版本固定为 3.94 |
| 后端代码 | 使用 MinIO Java SDK | 不变，仍用同一 SDK 通过 S3 协议访问 |
| 权限模型 | IAM 策略 JSON + `mc admin` | `s3.json` 静态身份与动作列表 |
| 管理控制台 | 9001 端口 Web 控制台 | 无 S3 控制台；9333（master）与 8888（filer）提供状态页 |
| Bucket 对象版本控制 | `mc version enable` | **未启用，见第 9 节** |

## 9. 已知限制与待办

1. **对象版本控制。** NFR-F4 要求设计文件永久留存。当前通过两种方式满足：每次上传都生成新的 UUID 对象键，不会覆盖旧对象；后端身份不对外提供删除对象的接口。SeaweedFS 的 S3 Bucket 版本控制尚未验证，Phase 2 的设计版本服务上线前应专门测试。
2. **单机无冗余。** `weed server` 单进程模式下，数据只有一份。正式使用前需要定期备份 `ordermgmt-seaweed-data` 数据卷；数据量增长后可拆分为多个 volume 节点并设置副本数。
3. **端口暴露。** 9333 和 8888 是 SeaweedFS 的管理端口，只适合本机调试。在局域网部署时，应只开放 8333，或通过 Nginx 反向代理 S3 网关并启用 HTTPS。
4. **孤儿对象清理。** 浏览器上传成功但未确认的对象会留在存储中。P4-T11 的清理任务需要改用 S3 `ListObjects` 实现，不能再使用 `mc find`。
5. **任务文档同步。** `docs/tasks/TASKS_PHASE_1.md` 中 P1-T02 仍写的是 MinIO 和 `mc`。如果需要文档与实现一致，应补充一条变更说明。

## 10. 启动与检查

```powershell
Set-Location E:\GitCode\advertising\blue333\infra
docker compose up -d --build
docker compose ps --all
docker compose logs seaweed-s3-config seaweed-s3-init
```

正常情况下：

- `seaweed-s3-config` 和 `seaweed-s3-init` 的状态是 `Exited (0)`。
- `seaweed-s3-init` 的日志最后一行包含 `is private and accepts presigned uploads from`。
- `backend` 的状态是 `healthy`。

不带签名直接访问时应被拒绝：

```powershell
curl.exe -i http://localhost:8333/ordermgmt/
```

预期返回 `403 AccessDenied`。
