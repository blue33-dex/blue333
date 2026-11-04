# 单笔宣传物料订单交付管控系统（课程作业）

客户经理（AM）登记订单，业务负责人（BO）审阅派发，设计制作人员（DS）接收任务。需求与任务拆分见 `docs/01-requirements` 和 `docs/tasks`。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.4、Spring Security + JWT、Spring Data JPA、Flyway |
| 数据库 | MySQL 8.0 |
| 对象存储 | SeaweedFS S3 Gateway（说明见 `docs/OBJECT_STORAGE_SEAWEEDFS.md`） |
| 前端 | Vue 3、TypeScript、Vite、Pinia |
| 部署 | Docker Compose + Nginx |

## 运行环境

- Docker Desktop（引擎处于 Running 状态）
- 前端开发另需 Node.js 20 及以上版本

## 启动后端与基础设施

```powershell
Copy-Item infra\.env.example infra\.env
Set-Location infra
docker compose up -d --build
docker compose ps --all
```

首次构建需要下载镜像和 Maven 依赖，耗时几分钟。

启动成功的标志：

- `backend` 状态为 `healthy`。
- `seaweed-s3-config` 和 `seaweed-s3-init` 状态为 `Exited (0)`。

| 地址 | 用途 |
|---|---|
| http://localhost:8080/actuator/health | 后端健康检查 |
| http://localhost/api/v1/... | 经 Nginx 访问的后端 API |
| http://localhost:8333 | S3 网关，浏览器直传使用 |

## 启动前端

```powershell
Set-Location frontend
npm.cmd install
npm.cmd run dev
```

访问 http://localhost:5173 。

前端默认连接真实后端（`/api` 由 Vite 代理到 `http://localhost:8080`）。如果只想离线演示登录、建单、审阅等已有 Mock 流程，在 `frontend/.env.local` 中设置：

```
VITE_USE_MOCK=true
```

注意：设计版本上传、版本历史、内部评审（对比/放行/退回/终稿）没有 Mock 数据，需要启动完整后端栈。

## 演示账号

三个账号的密码都是 `demo123`。

| 用户名 | 角色 |
|---|---|
| `am_demo` | 客户经理 AM |
| `bo_demo` | 业务负责人 BO |
| `ds_demo` | 设计制作人员 DS |

## 关于配置中的密钥

`infra/.env.example` 里的数据库密码、JWT 密钥和 S3 密钥都是**公开的演示值**，方便评阅者直接运行。为了降低风险：

- Compose 中所有端口只绑定 `127.0.0.1`，同一网络的其他机器无法访问。
- 真实的 `infra/.env` 不提交到 Git。

这些值不能用于任何真实部署。真实部署时应替换全部密钥。

## 运行测试

```powershell
Set-Location backend
mvn test
```

容器启动后，可以跑一遍 Phase 1 端到端检查（登录、建单、直传附件、提交、派发、DS 查看与下载、权限反例）。每次运行会新建一张演示订单：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra\scripts\e2e-phase1.ps1
```

设计版本（上传 → 确认 → 提交内部评审、版本历史、下载鉴权、并发编号）的端到端检查：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra\scripts\e2e-design-versions.ps1
```

内部评审（版本对比、放行、退回、标记终稿、评审记录与审计）的端到端检查：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra\scripts\e2e-internal-review.ps1
```

任务重新派发与取消（A-07 交接留痕、A-23 取消任务、通知与审计）的端到端检查：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra\scripts\e2e-reassign.ps1
```

该脚本会额外播种一个设计师账号 `ds_demo2`（id 9），用于验证交接的新旧设计师归属。

## 停止与清理

```powershell
Set-Location infra
docker compose down        # 停止容器，保留数据
docker compose down -v     # 停止容器并删除数据库与文件数据
```
