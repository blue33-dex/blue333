# Phase 1 · 登记 → 审阅 → 派发 · 详细任务说明

> **Phase 范围**：基础设施 + 认证 + RBAC + Order/MaterialTask 基础状态机 + AM 订单登记 + BO 审阅退回派发 + DS 可见任务
> **核心链路**：AM 创建订单 → AM 提交 → BO 审阅 → BO 退回 或 BO 派发 → DS 看到任务
> **故事覆盖**：AM-01 / AM-02 / AM-03 / AM-04 / BO-01 / BO-02 / BO-03 / DS-01（部分）
> **前置条件**：TASK_BREAKDOWN.md 已完成，人工审核通过
> **排除项**：所有设计版本 / 内部评审 / 客户评审 / 看板 / 时间线 / 异常 相关内容

---

## 阶段目标

Phase 1 交付后，系统形成**可独立运行的最小可测闭环**：

1. AM 登录 → 创建标准化订单 + 上传参考素材 + 提交订单
2. BO 登录 → 看到待审阅队列 → 审阅详情 → 退回或派发
3. DS 登录 → 看到派发给自己的任务列表（只读，不能上传版本）
4. 所有操作写入审计日志（BR-14）
5. BO 派发后 DS 收到站内通知（BR-15）
6. 数据可见性隔离（BR-13：AM 仅自己订单 / BO 全部 / DS 仅自己任务）

---

## 阶段交付物总览

| 层 | 交付 | 数量 |
|---|---|---|
| INFRA | Docker Compose + MySQL + MinIO + Nginx + Spring Boot 一键启动 | 4 容器 |
| DB | 11 张表 DDL + Flyway 迁移 + 种子数据 | 11 表 |
| BE | 9 个枚举 + 11 个实体 + 状态机 + 审计 + 通知 + JWT + 预签名 URL | — |
| API | 11 个接口（3 auth + 6 GLOSSARY + 2 补充）| 11 个 |
| FE | Vue 3 + Vite + 8 个页面 + 附件直传组件 | 8 页面 |
| TEST | 单元 + 集成 + 端到端（覆盖 10 条验收标准）| 10 验收 |

---

## Task 详细说明

### P1-T01 · Docker Compose + 容器编排

| 属性 | 内容 |
|---|---|
| **类型** | INFRA |
| **关联故事** | 基础设施，不直接关联用户故事 |
| **前置依赖** | 无 |
| **目标** | 提供一键启动整套环境的编排文件，包含 MySQL 8.0 + MinIO 最新 + Nginx 1.24 + Spring Boot 后端 |
| **实施步骤** | 1. 创建 `infra/docker-compose.yml`，定义 4 个 service 2. MySQL：挂载命名卷 `ordermgmt-mysql-data`，端口 3306，环境变量 MYSQL_ROOT_PASSWORD 从 .env 读取 3. MinIO：挂载命名卷 `ordermgmt-minio-data`，端口 9000/9001，access key / secret key 从 .env 读取 4. Nginx：映射 80/443 端口，挂载 nginx.conf 5. Spring Boot：build context 为 backend/，端口 8080 |
| **涉及文件** | `infra/docker-compose.yml`、`infra/nginx/nginx.conf`、`infra/nginx/conf.d/api.conf`、`infra/.env.example` |
| **验收标准** | `docker compose up -d` 后 4 个容器均 healthy；MySQL 可登录；MinIO 控制台 http://localhost:9001 可访问；Nginx http://localhost/ 返回 502（后端未启动时的预期） |
| **测试方法** | 本地执行 docker compose up -d + docker ps + 访问各端点 |
| **交付物** | 完整可运行的 Docker Compose 编排 |
| **完成条件** | docker compose up 无报错 + 4 容器 healthy + .env.example 包含所有必要变量注释 |

---

### P1-T02 · MinIO Bucket 初始化 + 权限策略

| 属性 | 内容 |
|---|---|
| **类型** | INFRA |
| **关联故事** | AM-02 上传素材（AM 上传参考文件）|
| **前置依赖** | P1-T01 |
| **目标** | 创建 `ordermgmt` bucket，设置只允许预签名 URL 访问（禁止公开读写），开启版本控制以满足 NFR-F4 永久留存 |
| **实施步骤** | 1. 编写 init 脚本 `infra/scripts/minio-init.sh`：使用 mc client 创建 bucket `ordermgmt` + 开启版本控制 2. 定义只读策略：允许 bucket listing + GET/PUT object，但仅通过预签名 URL 3. 将脚本挂入 docker-compose 入口 point（或单独执行一次）|
| **涉及文件** | `infra/scripts/minio-init.sh`、MinIO bucket 策略 JSON |
| **验收标准** | MinIO 控制台可见 `ordermgmt` bucket + 版本控制开启 + bucket Policy 禁止匿名访问 |
| **测试方法** | curl 直接 PUT 到 bucket 应返回 AccessDenied；使用预签名 URL PUT 应成功 |
| **交付物** | 初始化脚本 + 策略文件 |
| **完成条件** | 执行 mc 脚本后 bucket 就绪 + 可通过预签名 URL 上传 + 不可公开访问 |

---

### P1-T03 · Phase 1 11 张表 DDL

| 属性 | 内容 |
|---|---|
| **类型** | DB |
| **关联故事** | P1 所有故事（所有业务数据持久化基础）|
| **前置依赖** | P1-T01 |
| **目标** | 创建 11 张 Phase 1 必需的数据库表，严格按 GLOSSARY 表名、列名、枚举存储约定 |
| **表清单** | users · roles · user_roles · orders · material_specs · requirement_baselines · attachments · material_tasks · assignments · audit_logs · notifications |
| **实施步骤** | 1. 确定 Flyway 迁移脚本目录结构 `backend/src/main/resources/db/migration/` 2. V1__phase1_schema.sql：11 张表完整 DDL 3. 每张表：GLOSSARY 定义的列 + 类型 + 约束 + 唯一索引 + 外键索引 4. 状态字段统一 `VARCHAR(20~30)`，不用 MySQL ENUM 5. 时间戳统一 `DATETIME(3)` |
| **关键 DDL 细节** | - orders.order_no VARCHAR(20) UK - orders.status VARCHAR(20) - orders.created_at DATETIME(3) NOT NULL - material_specs.material_type VARCHAR(20) - material_tasks.status VARCHAR(20) - material_tasks.task_deadline DATETIME - audit_logs 无 delete 相关约束设计（append-only）- notifications idx_notif_recipient_id_is_read (recipient_id, is_read) 覆盖索引 |
| **涉及文件** | `backend/src/main/resources/db/migration/V1__phase1_schema.sql` |
| **验收标准** | Flyway 执行成功；11 张表结构与 GLOSSARY 描述一致；order_no / status / created_at 等关键字段索引存在 |
| **测试方法** | 连接 MySQL → `SHOW TABLES` → `DESCRIBE orders` 检查列 → 执行 `CHECK INDEX` |
| **交付物** | V1 迁移脚本 |
| **完成条件** | Flyway migrate 无报错 + 11 表全部创建 + 外键关系正确 |

---

### P1-T04 · 种子数据

| 属性 | 内容 |
|---|---|
| **类型** | DB |
| **关联故事** | P1 所有故事（登录认证前提）|
| **前置依赖** | P1-T03 |
| **目标** | 插入三角色定义 + 每角色 1 个测试账号，确保 Phase 1 所有端到端测试有可用的登录身份 |
| **实施步骤** | 1. roles 表：插入 `AM`、`BO`、`DS` 三行（role_type VARCHAR(5) 对应 RoleType 枚举）2. users 表：插入 3 个账号 `am_demo` / `bo_demo` / `ds_demo`，密码 BCrypt hash 后存储，邮箱/手机号 3. user_roles 表：3 条关联 4. 可选：插入 1 条测试订单用于回归 |
| **涉及文件** | `backend/src/main/resources/db/migration/V2__phase1_seed.sql` |
| **验收标准** | 3 个账号均可登录；角色关联正确；密码可验证 |
| **测试方法** | S-01 登录接口对 3 个账号分别调用 → 均返回 200 + JWT |
| **交付物** | V2 迁移脚本 |
| **完成条件** | 迁移成功 + 3 账号可登录 + 角色关联正确 |

---

### P1-T05 · Phase 1 9 个枚举类

| 属性 | 内容 |
|---|---|
| **类型** | BE-ENUM |
| **关联故事** | P1 所有故事（枚举是状态机、实体、API Response 的通用语言）|
| **前置依赖** | P1-T01（项目骨架已就绪）|
| **目标** | 创建 Java enum 类 + 同步创建 TypeScript enum，确保全栈一致 |
| **枚举清单** | OrderStatus(4) · MaterialTaskStatus(5) · MaterialType(6) · Priority(3) · RejectReasonType(4) · RoleType(3) · NotificationType(2) · AuditActionType（Phase 1 子集）· AuditTargetType（Phase 1 子集）|
| **实施步骤** | 1. 后端 `backend/src/main/java/.../enums/` 下每个枚举一个文件 2. 枚举值严格按 GLOSSARY §02 UPPER_SNAKE_CASE 3. 前端 `frontend/src/types/enums.ts` 同步定义 4. **Phase 1 AuditActionType 取子集**：SUBMIT_ORDER / REJECT_ORDER / ASSIGN_TASK / CANCEL_ORDER / CREATE_ORDER（5 个）；完整 17 个后续 Phase 逐步加入 |
| **涉及文件** | `backend/src/main/java/.../enums/*.java` × 9 + `frontend/src/types/enums.ts` |
| **验收标准** | Java enum 编译通过；TypeScript enum 无 TS 错误；全栈 enum 值完全一致 |
| **测试方法** | 后端单元测试验证枚举值；前端 TypeScript 编译 |
| **交付物** | 9 Java enum + 1 TS enum 文件 |
| **完成条件** | 两端枚举值 UPPER_SNAKE_CASE 完全对齐 + Java 编译 + TS 编译 |

---

### P1-T06 · IdGenerator

| 属性 | 内容 |
|---|---|
| **类型** | BE-UTIL |
| **关联故事** | AM-01（创建订单时生成 order_no）|
| **前置依赖** | P1-T05 |
| **目标** | 生成格式 `ORD-YYYYMMDD-NNN` 的唯一订单编号，并发安全 |
| **实施步骤** | 1. Spring Bean `IdGenerator` 类 2. 方法 `generateOrderNo()`：按当日日期查询 orders 表最大序号 + 1；并发下用 `SELECT ... FOR UPDATE` 或 Redis INCR 保证原子性 3. 序号 3 位，当日超 999 时格式扩展为 4 位 4. 返回完整字符串 |
| **涉及文件** | `backend/src/main/java/.../util/IdGenerator.java` |
| **验收标准** | 单线程连续生成 1000 个不重复；并发 10 线程各生成 100 个不重复 |
| **测试方法** | 单元测试：固定日期 mock → 验证 ORD-20261023-001 到 ORD-20261023-00N 顺序；并发测试 |
| **交付物** | IdGenerator.java |
| **完成条件** | 单线程 + 并发测试均通过 + 生成格式符合 GLOSSARY E-04 |

---

### P1-T07 · XSSCleaner

| 属性 | 内容 |
|---|---|
| **类型** | BE-UTIL |
| **关联故事** | NFR-S3 / NFR-S4（XSS 清洗是横切关注点，贯穿所有文本字段存储）|
| **前置依赖** | P1-T01 |
| **目标** | 在所有用户输入文本存储前进行 XSS 清洗，清洗 HTML 标签、脚本注入；仅保留纯文本或白名单标签 |
| **实施步骤** | 1. 引入 Hutool 依赖 2. Spring Bean `XSSCleaner`：封装 Hutool XSSFilter.filter 3. 提供静态方法 `clean(String input)` 返回清洗后字符串 4. 定义白名单：仅允许 `<br>`、`<strong>`、`<em>`（可选），默认纯文本 |
| **涉及文件** | `backend/src/main/java/.../util/XSSCleaner.java` |
| **验收标准** | `<script>alert(1)</script>` → 清洗为空或纯文本 `<p onclick="alert(1)">test</p>` → 清洗为空 `正常文本` → 不变 |
| **测试方法** | 单元测试覆盖 script / event handler / iframe / img onerror 等常见注入方式 |
| **交付物** | XSSCleaner.java |
| **完成条件** | 常见 XSS Payload 全部清洗 + 正常文本不受影响 |

---

### P1-T08 · PrivacyMask

| 属性 | 内容 |
|---|---|
| **类型** | BE-UTIL |
| **关联故事** | NFR-S1 / NFR-S2（隐私脱敏）|
| **前置依赖** | P1-T01 |
| **目标** | 在后端响应 DTO 层统一注入脱敏规则：手机号 `138****8888`、邮箱 `z***@example.com` |
| **实施步骤** | 1. Spring Bean `PrivacyMask` 类 2. `maskPhone(String phone)`：保留前 3 后 4，中间替换 `****` 3. `maskEmail(String email)`：保留首字符 + `***` + @domain，如 `z***@example.com` 4. 在 Response DTO 的 getter 或 Jackson Serializer 中注入 |
| **涉及文件** | `backend/src/main/java/.../util/PrivacyMask.java` + 可选 Jackson Serializer |
| **验收标准** | `13812348888` → `138****8888` `zhangming@example.com` → `z***@example.com` 非手机号/邮箱格式 → 不处理 |
| **测试方法** | 单元测试覆盖各种手机号/邮箱格式 + 非格式输入 |
| **交付物** | PrivacyMask.java |
| **完成条件** | 脱敏规则正确 + 非敏感字段不被误脱敏 |

---

### P1-T09 · Phase 1 实体 + Repository

| 属性 | 内容 |
|---|---|
| **类型** | BE-ENT |
| **关联故事** | P1 所有故事（ORM 层基础）|
| **前置依赖** | P1-T03（DDL 已创建）、P1-T05（枚举已定义）|
| **目标** | 11 个 JPA Entity + 对应 Spring Data JPA Repository 接口，严格按 GLOSSARY 命名 |
| **实体清单** | User / Role / UserRole / Order / MaterialSpec / RequirementBaseline / Attachment / MaterialTask / Assignment / AuditLog / Notification |
| **实施步骤** | 1. 每个 Entity 一个文件，`@Entity + @Table(name=...)`，表名严格按 GLOSSARY 2. 枚举字段用 `@Enumerated(EnumType.STRING)` 存储 3. UserRole 中间表 `@EmbeddedId` 或双列 `@Id` 4. 每个 Repository 继承 `JpaRepository<T, Long>` 5. AuditLog Repository **不提供 delete 方法**（append-only 保护）|
| **涉及文件** | `backend/src/main/java/.../domain/*.java` × 11 + `backend/src/main/java/.../repository/*.java` × 11 |
| **验收标准** | Entity 编译通过；Repository 编译通过；启动 Hibernate ddl-auto=validate 无报错（表结构匹配）|
| **测试方法** | 启动 Spring Boot → Hibernate validate 通过；Repository 基础 CRUD 测试 |
| **交付物** | 11 Entity + 11 Repository |
| **完成条件** | Hibernate validate 通过 + 所有 Repository 方法可调用 |

---

### P1-T10 · Spring Security 配置

| 属性 | 内容 |
|---|---|
| **类型** | BE-SEC |
| **关联故事** | P1 所有故事（NFR-S5/S6 接口强鉴权）|
| **前置依赖** | P1-T09 |
| **目标** | JWT 无状态认证 + Spring Security 配置 + 自定义 JWT Filter + @PreAuthorize 注解体系入口 |
| **实施步骤** | 1. 引入 Spring Security + jjwt 依赖 2. `SecurityConfig` Bean：FilterChain 放行 `/api/v1/auth/**`，其他路径需认证 3. 自定义 `JwtAuthenticationFilter extends OncePerRequestFilter`：解析 Authorization header → 验证 JWT → 设置 SecurityContext 4. 自定义 `UserDetailsService`：从 users/user_roles 查询 5. PasswordEncoder BCrypt 6. 定义 JWT 工具类：生成 Access Token（30min）+ Refresh Token（7d）|
| **涉及文件** | `backend/src/main/java/.../config/SecurityConfig.java`、`backend/src/main/java/.../security/JwtAuthenticationFilter.java`、`backend/src/main/java/.../security/JwtUtils.java` |
| **验收标准** | 无 Token 访问受保护接口 → 401；过期 Token → 401；合法 Token → 200；未放行 auth 接口 S-01/S-02/S-03 可匿名调用 |
| **测试方法** | Postman/curl 模拟无 Token / 非法 Token / 合法 Token 三种场景 |
| **交付物** | SecurityConfig + JwtAuthenticationFilter + JwtUtils |
| **完成条件** | 三种 Token 场景返回正确状态码 + auth 白名单路径匿名可访问 |

---

### P1-T11 · 认证 Controller

| 属性 | 内容 |
|---|---|
| **类型** | BE-CTL |
| **关联故事** | P1 所有故事（登录是所有业务的前提）|
| **前置依赖** | P1-T10 |
| **目标** | S-01/S-02/S-03 三个认证接口实现 |
| **接口** | S-01 POST /api/v1/auth/login（返回 access_token + refresh_token + 当前用户信息含角色）S-02 POST /api/v1/auth/logout（前端清理；后端可选黑名单）S-03 POST /api/v1/auth/refresh（用 refresh_token 换 access_token）|
| **实施步骤** | 1. `AuthController` + `AuthService` 2. login：接收 username + password → BCrypt 验证 → 查询 UserRole → 生成双 Token 3. logout：返回 204（MVP 前端清理 Token 即可，后端暂不维护黑名单）4. refresh：验证 refresh_token → 生成新 access_token |
| **涉及文件** | `backend/src/main/java/.../controller/AuthController.java`、`backend/src/main/java/.../service/AuthService.java` |
| **验收标准** | P1-T04 种子数据 3 个账号均可登录；login 返回的 Token 能通过 JWT Filter 校验；refresh 后旧 access_token 立即失效 |
| **测试方法** | 3 账号分别 login → 用 Token 访问业务接口 → 过期后 refresh |
| **交付物** | AuthController + AuthService |
| **完成条件** | 3 账号登录成功 + Token 可用 + refresh 正常 |

---

### P1-T12 · 审计日志 AOP 切面

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC |
| **关联故事** | BR-14 操作审计（横切关注点，贯穿所有状态变更）|
| **前置依赖** | P1-T09 |
| **目标** | 所有状态变更操作（submitOrder / rejectOrder / assignTask / cancelOrder）自动写入 audit_logs 表，异步线程池写入，主流程零阻塞 |
| **实施步骤** | 1. 定义自定义注解 `@AuditLog(action = AuditActionType.SUBMIT_ORDER, targetType = AuditTargetType.ORDER)` 2. `AuditLogAspect` 切面：拦截注解方法 → 提取 operator_id（从 SecurityContext）+ before_state（方法执行前查询）+ after_state（方法执行后查询）3. 异步写入：`auditLogService.record()` 提交到 `@Async("auditLogExecutor")` 线程池 |
| **涉及文件** | `backend/src/main/java/.../aop/AuditLogAspect.java`、自定义注解 `@AuditLog` |
| **验收标准** | submitOrder 执行后 audit_logs 表新增 1 条，action_type=SUBMIT_ORDER；rejectOrder 同理；before_state 和 after_state 正确 |
| **测试方法** | Service 层单元测试：调用带 @AuditLog 注解方法 → 验证 audit_logs 插入正确 |
| **交付物** | AuditLogAspect + @AuditLog 注解 + AsyncConfig |
| **完成条件** | 所有带 @AuditLog 的方法均产生审计记录 + 异步不阻塞主流程 |

---

### P1-T13 · 站内通知基础 Service

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC |
| **关联故事** | BR-15 站内通知（横切关注点）|
| **前置依赖** | P1-T09 |
| **目标** | 状态变更时自动向相关角色推送站内通知，写入 notifications 表 |
| **实施步骤** | 1. 定义 Spring `ApplicationEvent`：`OrderSubmittedEvent / OrderRejectedEvent / TaskAssignedEvent` 2. `NotificationService`：接收 Event → 异步写入 notifications 表 3. 消息内容模板化：BO 派发 → "任务 {orderNo} 已派发给你，截止 {deadline}" 4. 收件人：从 Event 携带的 recipientId 列表 |
| **涉及文件** | `backend/src/main/java/.../event/*.java` × N + `backend/src/main/java/.../service/NotificationService.java` |
| **验收标准** | BO assignTask 执行后 notifications 表新增 1 条，recipient_id = 被派发 DS，is_read = 0 |
| **测试方法** | Service 层单元测试：publishEvent → 验证 notifications 插入 |
| **交付物** | Event 类 + NotificationService |
| **完成条件** | BO 派发 → DS 收到通知（DB 中可见）|

---

### P1-T14 · 预签名 URL 生成器

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC |
| **关联故事** | NFR-F3 文件直传对象存储（A-02 上传附件 + A-08 上传版本）|
| **前置依赖** | P1-T02（MinIO bucket 已就绪）、P1-T09 |
| **目标** | 后端生成带过期时间的预签名 PUT URL 和 GET URL，前端无密钥直传/直下载 |
| **实施步骤** | 1. 引入 MinIO SDK for Java 2. `FileStorageService` Spring Bean：注入 MinioClient 3. `generatePresignedPutUrl(String objectKey, long expiryMinutes)` → 返回预签名 PUT URL + 生成的 objectKey 4. `generatePresignedGetUrl(String objectKey, long expiryMinutes)` → 返回预签名 GET URL + HTTP 302 重定向 5. 预签名 URL 有效期：PUT 15min / GET 5min |
| **涉及文件** | `backend/src/main/java/.../service/FileStorageService.java` |
| **验收标准** | 用预签名 PUT URL 直接 curl PUT → MinIO bucket 出现对象；过期后 PUT 返回 AccessDenied |
| **测试方法** | 单元测试 mock MinioClient 生成 URL；集成测试真实直传 |
| **交付物** | FileStorageService |
| **完成条件** | PUT URL 直传成功 + GET URL 直下载成功 + 过期拒绝 |

---

### P1-T15 · Order 状态机

| 属性 | 内容 |
|---|---|
| **类型** | BE-SM |
| **关联故事** | AM-01（初始 PENDING_CONFIRM）、AM-03（submit）、BO-02（reject）、BO-03（assign）、AM-01/BO-22（cancel）|
| **前置依赖** | P1-T05（OrderStatus 枚举）、P1-T09（Order Entity）、P1-T12（审计）|
| **目标** | Phase 1 覆盖的 Order 4 态基础流转 |
| **Phase 1 覆盖的流转** | INIT → PENDING_CONFIRM（AM submitOrder）| PENDING_CONFIRM ↔ PENDING_CONFIRM（BO reject → AM 重提）| PENDING_CONFIRM → IN_PRODUCTION（BO assignTask）| PENDING_CONFIRM → CANCELLED（AM cancelOrder）| IN_PRODUCTION → CANCELLED（BO cancelOrder）|
| **实施步骤** | 1. `OrderStateMachine` 类 2. 每个流转方法：前置条件校验 + 状态变更 + 审计日志 + 联动通知 3. **Phase 1 不覆盖 IN_PRODUCTION → COMPLETED**（推迟 P3-T04）|
| **涉及文件** | `backend/src/main/java/.../statemachine/OrderStateMachine.java` |
| **验收标准** | 单元测试覆盖 Phase 1 所有合法流转；非法流转（IN_PRODUCTION → PENDING_CONFIRM）抛出 InvalidStateTransitionException |
| **测试方法** | 单元测试矩阵（6 条合法 + N 条非法）|
| **交付物** | OrderStateMachine.java |
| **完成条件** | Phase 1 所有合法流转通过 + 非法流转拒绝 + 审计日志正确 |

---

### P1-T16 · MaterialTask 状态机（基础）

| 属性 | 内容 |
|---|---|
| **类型** | BE-SM |
| **关联故事** | BO-03（派发 → PENDING_DESIGN）|
| **前置依赖** | P1-T05（MaterialTaskStatus 枚举）、P1-T09（MaterialTask Entity）|
| **目标** | Phase 1 覆盖的 MaterialTask 状态流转 |
| **Phase 1 覆盖的流转** | INIT → PENDING_DESIGN（BO assignTask 时自动创建）| 非终态 → CANCELLED（BO cancelTask）|
| **Phase 1 不覆盖** | PENDING_DESIGN ↔ PENDING_APPROVAL ↔ PENDING_REVISE 循环 + APPROVED 终态（全部推迟 P2）|
| **实施步骤** | 1. `MaterialTaskStateMachine` 类 2. Phase 1 只实现 INIT→PENDING_DESIGN + CANCELLED 3. **注意 CONFLICT-01**：APPROVED 触发点推迟到 P3-T04，此处不预埋错误逻辑 |
| **涉及文件** | `backend/src/main/java/.../statemachine/MaterialTaskStateMachine.java` |
| **验收标准** | assignTask 时创建 PENDING_DESIGN 任务；cancelTask 时改 CANCELLED |
| **测试方法** | 单元测试 |
| **交付物** | MaterialTaskStateMachine.java（Phase 1 最小实现）|
| **完成条件** | INIT→PENDING_DESIGN 正确创建 + CANCELLED 正确流转 |

---

### P1-T17 · OrderService

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC |
| **关联故事** | AM-01 / AM-02 / AM-03 / BO-02 / BO-03 / BO-22 / AM-04 |
| **前置依赖** | P1-T15（Order 状态机）、P1-T14（预签名 URL）、P1-T06（IdGenerator）|
| **目标** | Order 业务核心 Service |
| **方法清单（Phase 1）** | createOrder()（A-01）| uploadAttachment()（A-02，返回预签名 URL）| submitOrder()（A-03）| rejectOrder()（A-05，rejectReasonType 枚举必填）| listMyOrders()（A-24）| cancelOrder()（A-22 基础）|
| **关键业务逻辑** | - createOrder：order_no 生成 + 需求基线快照生成（RequirementBaseline）+ 状态 PENDING_CONFIRM - uploadAttachment：校验文件大小 ≤200MB + 格式白名单 → 返回预签名 PUT URL + uploadSessionId → 写入 Redis TTL 30min - submitOrder：必填字段校验 + 需求基线已生成 → 调用 OrderStateMachine.submit() - rejectOrder：校验 PENDING_CONFIRM + rejectReasonType 非空 + remark 非空 - cancelOrder：AM 仅可 PENDING_CONFIRM 草稿；BO 可 CANCELLED IN_PRODUCTION（Phase 1 先支持 AM 的草稿取消）|
| **涉及文件** | `backend/src/main/java/.../service/OrderService.java` |
| **验收标准** | 每个方法的业务规则均被 Service 层单元测试覆盖；状态机联动正确 |
| **测试方法** | Service 层单元测试（mock Repository + 真实状态机）|
| **交付物** | OrderService.java |
| **完成条件** | Phase 1 所有 Order 业务方法实现 + 业务规则校验通过 |

---

### P1-T18 · MaterialTaskService

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC |
| **关联故事** | BO-03（assignTask 时自动创建 MaterialTask PENDING_DESIGN）|
| **前置依赖** | P1-T16（MaterialTask 状态机）、P1-T09 |
| **目标** | BO 派发时同步创建 MaterialTask + Assignment 记录 |
| **实施步骤** | 1. `MaterialTaskService`：`createFromAssignment(Order order, MaterialSpec spec, User assignee, DateTime deadline, Priority priority)` → 调用 MaterialTaskStateMachine.init() → 写入 material_tasks 表（status=PENDING_DESIGN）2. 同时写入 assignments 表（assignment_type=INITIAL）|
| **涉及文件** | `backend/src/main/java/.../service/MaterialTaskService.java` |
| **验收标准** | BO assignTask 后 material_tasks 表新增记录 status=PENDING_DESIGN；assignments 表新增 INITIAL 记录 |
| **测试方法** | 单元测试 |
| **交付物** | MaterialTaskService.java（Phase 1 最小实现）|
| **完成条件** | BO 派发 → 自动创建任务 + 派发记录 |

---

### P1-T19 · 数据可见性 Repository 注入

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC |
| **关联故事** | BR-13 数据可见性隔离 |
| **前置依赖** | P1-T09 |
| **目标** | Repository 查询层强制注入 WHERE 条件，AM 仅自己订单 / BO 全部 / DS 仅自己任务 |
| **实施步骤** | 1. 定义接口 `RoleScopedOrderRepository extends JpaRepository<Order, Long>` + 默认方法 `findVisibleOrders(Long currentUserId, RoleType role, Pageable pageable)` → switch(role) 调用对应查询 2. 各 Repository 实现带 `@Query` 的角色专属查询方法 3. Service 层强制调用带可见性过滤的方法，禁止直接 `findById` 绕过 |
| **涉及文件** | Repository 接口扩展 |
| **验收标准** | AM 查询 → WHERE am_id = currentUser；DS 查询 material_tasks → WHERE assignee_id = currentUser；BO → 无条件全量 |
| **测试方法** | Repository 层单元测试：模拟不同 RoleType → 验证 SQL 包含正确 WHERE |
| **交付物** | 扩展后的 Repository 接口 |
| **完成条件** | 三角色查询隔离正确 + BO 能看全部 + DS 无法看到他人任务 |

---

### P1-T20 · 订单模块 Controller

| 属性 | 内容 |
|---|---|
| **类型** | BE-CTL |
| **关联故事** | AM-01/02/03/04 + BO-01/02/03/22 |
| **前置依赖** | P1-T17, P1-T19 |
| **目标** | A-01 / A-02 / A-03 / A-04 / A-05 / A-06 / A-22 / A-24 共 8 个 Controller 端点 |
| **端点清单** | POST /api/v1/orders (A-01) POST /api/v1/orders/{orderId}/attachments (A-02) POST /api/v1/orders/{orderId}/submit (A-03) GET /api/v1/orders/{orderId}/review (A-04) POST /api/v1/orders/{orderId}/reject (A-05) POST /api/v1/orders/{orderId}/assign (A-06) POST /api/v1/orders/{orderId}/cancel (A-22) GET /api/v1/orders/mine (A-24) |
| **关键实现** | - 每个方法加 `@PreAuthorize`：A-01/A-02/A-03/A-24 → `hasRole('AM')`；A-04/A-05/A-06 → `hasRole('BO')`；A-22 → AM 仅 PENDING_CONFIRM 或 BO 任意 - 统一 Response 包装：成功 `{"data": ...}` + 业务 code - 游标分页实现（A-24 listMyOrders）|
| **涉及文件** | `backend/src/main/java/.../controller/OrderController.java` |
| **验收标准** | 每个端点通过 Postman 可调用；@PreAuthorize 注解正确拦截越权请求；A-24 分页正确返回 has_next + next_cursor |
| **测试方法** | Postman / curl 端到端调用 |
| **交付物** | OrderController.java + DTO 类 |
| **完成条件** | 8 端点全部返回正确 Response + 权限注解正确 |

---

### P1-T21 · 通用只读 Controller

| 属性 | 内容 |
|---|---|
| **类型** | BE-CTL |
| **关联故事** | DS-01（我的任务列表）+ BO/DS 任务详情读取 |
| **前置依赖** | P1-T19 |
| **目标** | S-04 / S-05 / S-06 / S-08 四个补充端点 |
| **端点清单** | GET /api/v1/orders/{orderId} (S-04) GET /api/v1/material-tasks/mine (S-05) GET /api/v1/material-tasks/{taskId} (S-06) GET /api/v1/attachments/{attachmentId}/download (S-08) |
| **关键实现** | - S-04/S-05/S-06 走 P1-T19 数据可见性过滤 - S-08 下载鉴权：后置校验角色 + 数据可见性 → 生成预签名 GET URL → HTTP 302 |
| **涉及文件** | 新增 `MaterialTaskController.java` + `AttachmentController.java` |
| **验收标准** | DS 调 S-05 只返回自己的任务；AM 调 S-04 只能看自己订单；BO 能看全部 |
| **测试方法** | 三角色分别调用 S-04/S-05/S-06 → 验证返回范围 |
| **交付物** | MaterialTaskController + AttachmentController |
| **完成条件** | 三角色只读隔离正确 + S-08 下载鉴权生效 |

---

### P1-T22 · Vue 3 脚手架

| 属性 | 内容 |
|---|---|
| **类型** | FE-SC |
| **关联故事** | 基础设施 |
| **前置依赖** | 无（可与后端并行）|
| **目标** | Vue 3 + Vite + TypeScript 项目脚手架 + Pinia Store + Vue Router + 三角色路由守卫 |
| **实施步骤** | 1. `npm create vite@latest frontend -- --template vue-ts` 2. 引入 Pinia + Vue Router 3. 定义路由：/login 公共；/am/* 仅 AM；/bo/* 仅 BO；/ds/* 仅 DS 4. beforeEach 守卫：从 Pinia store 读当前角色 → 角色不匹配跳 403 页面 |
| **涉及文件** | `frontend/src/router/index.ts`、`frontend/src/stores/auth.ts` |
| **验收标准** | npm run dev 启动；未登录跳 /login；AM 尝试访问 /bo/kanban → 403 页面 |
| **测试方法** | 浏览器手动测试 |
| **交付物** | 完整脚手架项目 |
| **完成条件** | npm run dev 无报错 + 路由守卫正确拦截 |

---

### P1-T23 · Axios 拦截器

| 属性 | 内容 |
|---|---|
| **类型** | FE-SC |
| **关联故事** | NFR-S5 Token 自动附加 + 401 自动刷新 |
| **前置依赖** | P1-T22 |
| **目标** | 全局 Axios 实例：请求拦截自动附加 JWT；响应拦截 401 自动调用 refresh；统一错误处理 |
| **实施步骤** | 1. `frontend/src/api/http.ts` 创建 Axios 实例 baseURL `/api/v1` 2. Request 拦截器：从 localStorage 读 access_token → 加 Authorization header 3. Response 拦截器：401 + code=TOKEN_EXPIRED → 自动调 S-03 refresh → 重放原请求 → 连续 2 次 401 跳 /login 4. 统一错误 toast 提示 |
| **涉及文件** | `frontend/src/api/http.ts` |
| **验收标准** | 业务调用自动带 JWT；Token 过期自动 refresh；连续过期跳 /login |
| **测试方法** | 浏览器 DevTools Network 面板 + 手动测试 Token 过期场景 |
| **交付物** | http.ts |
| **完成条件** | 拦截器全链路生效 + Token 自动刷新 |

---

### P1-T24 · Phase 1 API 函数封装

| 属性 | 内容 |
|---|---|
| **类型** | FE-API |
| **关联故事** | P1 所有 API 端点 |
| **前置依赖** | P1-T23 |
| **目标** | 11 个 Phase 1 API 的 TypeScript 函数封装，Request/Response 类型严格对应 GLOSSARY DTO |
| **实施步骤** | 1. `frontend/src/api/modules/auth.ts`：login / logout / refresh 2. `frontend/src/api/modules/order.ts`：create / uploadPresigned / submit / review / reject / assign / cancel / listMine 3. `frontend/src/api/modules/materialTask.ts`：listMine / getById 4. `frontend/src/api/modules/attachment.ts`：download 5. 所有 Request 类型在 `types/api.d.ts` 定义 |
| **涉及文件** | `frontend/src/api/modules/*.ts` × 4 + `frontend/src/types/api.d.ts` |
| **验收标准** | TypeScript 编译无错误；函数签名与后端 DTO 完全对齐 |
| **测试方法** | TS 编译 + 调用函数返回类型推断正确 |
| **交付物** | API 函数模块 |
| **完成条件** | 所有 API 函数封装完成 + TS 类型正确 |

---

### P1-T25 · LoginView

| 属性 | 内容 |
|---|---|
| **类型** | FE-PAGE |
| **关联故事** | 登录入口（基础设施）|
| **前置依赖** | P1-T24 |
| **目标** | 登录页：表单 + 调用 S-01 login + Token 存 Pinia + 按角色跳转首页 |
| **实施步骤** | 1. 用户名 + 密码输入 2. submit → 调 S-01 login 3. 成功：access_token + refresh_token 存 localStorage；当前用户信息 + 角色存 auth store 4. 跳转：AM → /am/orders/create；BO → /bo/review；DS → /ds/tasks |
| **涉及文件** | `frontend/src/views/LoginView.vue` |
| **验收标准** | P1-T04 三个种子账号均可登录；登录后角色跳转正确；密码错误显示提示 |
| **测试方法** | 浏览器手动测试 |
| **交付物** | LoginView.vue |
| **完成条件** | 3 账号登录成功 + 角色跳转正确 |

---

### P1-T26 · 预签名直传附件组件

| 属性 | 内容 |
|---|---|
| **类型** | FE-COMP |
| **关联故事** | AM-02（上传参考素材）|
| **前置依赖** | P1-T24 |
| **目标** | Step 1 获取预签名 URL → Step 2 PUT MinIO → Step 3 后端确认；失败指数退避重试 3 次；上传进度条 |
| **实施步骤** | 1. Composable `useDirectUpload`：封装三步流程 2. Step 1 POST 后端 → 拿到 presignedPutUrl + uploadSessionId 3. Step 2 `fetch(presignedPutUrl, {method:'PUT', body: file})` 直传；失败时 setTimeout 指数退避 1s→2s→4s 最多 3 次；3 次均失败提示用户"重新获取 URL" 4. Step 3 后端接口确认元数据写入 DB（**注意**：Phase 1 附件只有 Step 1 + Step 3 简化——Step 3 在 A-02 uploadAttachment 调用内完成，不需要独立 S-09）5. 显示上传进度（XMLHttpRequest.upload.onprogress）|
| **涉及文件** | `frontend/src/composables/useDirectUpload.ts` + UI 组件 `AttachmentUploader.vue` |
| **验收标准** | 上传成功后 MinIO bucket 有对象 + DB attachments 有记录；网络失败自动重试 3 次；进度条正常显示 |
| **测试方法** | 浏览器 DevTools 模拟网络抖动 |
| **交付物** | useDirectUpload + AttachmentUploader |
| **完成条件** | 三步流程完整 + 重试 + 进度 |

---

### P1-T27 · AM CreateOrder

| 属性 | 内容 |
|---|---|
| **类型** | FE-PAGE |
| **关联故事** | AM-01 + AM-02 |
| **前置依赖** | P1-T24 + P1-T26 |
| **目标** | 标准化表单 + material_specs 动态行 + 附件上传 + 提交 |
| **实施步骤** | 1. 必填字段：materialSpecs（至少 1 行，每行 materialType + specSize）+ useScene + deliveryDeadline + customerName 2. materialSpecs 动态增删行 3. 附件上传：复用 AttachmentUploader 组件 4. 提交：调 A-01 createOrder → 返回 orderId + orderNo → 自动调 A-03 submitOrder → 成功后跳 MyOrderList 5. 表单校验：前端必填 + 后端双重校验（422 反馈）|
| **涉及文件** | `frontend/src/views/am/CreateOrder.vue` |
| **验收标准** | 必填校验正确；提交后 DB orders 表新增 order_no=ORD-{today}-{seq} + material_specs 表关联新增 |
| **测试方法** | 浏览器手动 + 后端 DB 验证 |
| **交付物** | CreateOrder.vue |
| **完成条件** | 提交成功 + 数据完整写入 DB + 自动 submit → BO 待办可见 |

---

### P1-T28 · AM MyOrderList

| 属性 | 内容 |
|---|---|
| **类型** | FE-PAGE |
| **关联故事** | AM-04 |
| **前置依赖** | P1-T24 |
| **目标** | 我的订单列表 + OrderStatus 标签 + 游标分页 + 筛选 |
| **实施步骤** | 1. 调 A-24 listMyOrders 2. 列表列：order_no / material_type / customer_name / delivery_deadline / status / 创建时间 3. OrderStatus 标签组件 `OrderStatusTag`：不同状态不同颜色 4. 游标分页：上一页/下一页 + has_next 判断 5. 点击行 → 跳转 OrderDetail |
| **涉及文件** | `frontend/src/views/am/MyOrderList.vue` + `frontend/src/components/common/OrderStatusTag.vue` |
| **验收标准** | 列表只显示当前 AM 创建的订单（BR-13 数据可见性前端验证）；分页游标正确 |
| **测试方法** | 不同 AM 账号登录 → 验证只能看到自己的订单 |
| **交付物** | MyOrderList + OrderStatusTag |
| **完成条件** | 数据可见性正确 + 分页正确 + 状态标签正确 |

---

### P1-T29 · 通用 OrderDetail

| 属性 | 内容 |
|---|---|
| **类型** | FE-PAGE |
| **关联故事** | BO-01 + AM-01 详情查看 + DS-01 需求查看 |
| **前置依赖** | P1-T24 |
| **目标** | 订单详情页：需求基线快照 + 物料规格行 + 附件列表 + 物料任务列表 + 退回原因显示 |
| **实施步骤** | 1. 调 S-04 通用详情 + 调 A-04 BO 审阅详情（BO 角色额外信息）2. 区块：需求基线（不可编辑快照）+ 物料规格 + 附件（调 S-08 下载）+ 物料任务列表（只读，Phase 2 扩展）+ 退回原因（若有）|
| **涉及文件** | `frontend/src/views/OrderDetail.vue` |
| **验收标准** | AM / BO / DS 三角色均可打开 + 各自看到的数据范围正确 |
| **测试方法** | 三角色分别登录访问 S-04 → 验证返回内容 |
| **交付物** | OrderDetail.vue |
| **完成条件** | 三角色可见 + 需求基线 + 附件下载 + 退回原因 |

---

### P1-T30 · BO PendingReviewList

| 属性 | 内容 |
|---|---|
| **类型** | FE-PAGE |
| **关联故事** | BO-01 |
| **前置依赖** | P1-T24 |
| **目标** | 待审阅订单列表，按 submission 时间倒序 |
| **实施步骤** | 1. 列表筛选：order_no / customer_name / 时间范围 2. 行：order_no / material_type / AM 姓名 / submission 时间 / 附件数量 3. 点击 → BO OrderReview 页 |
| **涉及文件** | `frontend/src/views/bo/PendingReviewList.vue` |
| **验收标准** | BO 登录看到所有 AM 提交的待审阅订单；列表按 submission 时间倒序 |
| **测试方法** | BO 登录 → 与 MyOrderList 对比数据 |
| **交付物** | PendingReviewList.vue |
| **完成条件** | 列表正确 + 筛选可用 |

---

### P1-T31 · BO OrderReview + Reject + Assign

| 属性 | 内容 |
|---|---|
| **类型** | FE-PAGE |
| **关联故事** | BO-01（审阅详情）+ BO-02（退回）+ BO-03（派发）|
| **前置依赖** | P1-T29（OrderDetail 扩展）|
| **目标** | 同一页面三个操作区域：审阅详情（复用 OrderDetail）+ 退回操作区 + 派发操作区 |
| **实施步骤** | 1. 审阅详情区：复用 OrderDetail 布局 + BO 专属审阅信息 2. 退回区：RejectReasonType 下拉（INFORMATION_INCOMPLETE / SPEC_UNCLEAR / MATERIAL_MISSING / OTHER）+ remark 输入（必填）+ 二次确认弹窗（NFR-U3）+ 调 A-05 rejectOrder 3. 派发区：DS 列表（拉取 DS 角色用户）+ deadline 日期选择 + Priority 下拉 + 可选 remark + 调 A-06 assignTask + 物料规格行自动生成对应 MaterialTask |
| **涉及文件** | `frontend/src/views/bo/OrderReview.vue` |
| **验收标准** | BO 退回 → 订单回到 AM 待办 + 显示退回原因；BO 派发 → DS 登录看到新任务 + 状态 IN_PRODUCTION |
| **测试方法** | BO 退回 + 派发 → AM/DS 登录验证 |
| **交付物** | OrderReview.vue（含三个操作区）|
| **完成条件** | 退回 + 派发完整链路 + 数据联动正确 |

---

### P1-T32 · DS MyTaskList + TaskDetail（只读）

| 属性 | 内容 |
|---|---|
| **类型** | FE-PAGE |
| **关联故事** | DS-01 |
| **前置依赖** | P1-T24 |
| **目标** | DS 我的任务列表 + 任务详情只读（Phase 2 扩展上传）|
| **实施步骤** | 1. MyTaskList：调 S-05 → 只返回当前 DS 的任务 + status 标签 + deadline 倒计时 2. TaskDetail：调 S-06 → 显示关联订单的需求基线 + 附件（下载）+ deadline + priority 3. 只读：Phase 1 DS 不可操作，不可上传版本（Phase 2 扩展）|
| **涉及文件** | `frontend/src/views/ds/MyTaskList.vue`、`frontend/src/views/ds/TaskDetail.vue` |
| **验收标准** | DS 登录只能看到派发给自己的任务（BR-13）；任务详情只读；deadline 倒计时正确 |
| **测试方法** | DS 登录 + BO 派发新任务后刷新 → 新任务可见 |
| **交付物** | MyTaskList + TaskDetail（Phase 1 只读）|
| **完成条件** | DS 可见自己任务 + 详情只读 |

---

### P1-T33 · Phase 1 前后端联调

| 属性 | 内容 |
|---|---|
| **类型** | API-INTEG |
| **关联故事** | P1 所有故事的联调验证 |
| **前置依赖** | P1-T20 / P1-T21 / P1-T25 ~ P1-T32 全部完成 |
| **目标** | 用真实后端 API 替换前端 Mock，全链路跑通"登录 → 创建订单 → 提交 → BO 审阅 → BO 退回/派发 → DS 看到任务" |
| **实施步骤** | 1. 后端启动（Flyway 自动迁移 + 种子数据）2. 前端 Vite proxy 指向后端 3. AM / BO / DS 三角色分别走一遍完整链路 4. 修复联调 Bug |
| **涉及文件** | 前后端 Bug 修复提交 |
| **验收标准** | 三角色端到端操作无报错；DB 中数据完整；MinIO 中附件对象存在 |
| **测试方法** | 人工端到端操作 |
| **交付物** | 联调通过的前后端代码 + Bug 修复记录 |
| **完成条件** | 端到端完整链路无报错 + DB/MinIO 数据一致 |

---

### P1-T34 · Order / MaterialTask 状态机单元测试

| 属性 | 内容 |
|---|---|
| **类型** | TEST |
| **关联故事** | AM-03 / BO-02 / BO-03 状态流转 |
| **前置依赖** | P1-T15 / P1-T16 |
| **目标** | 覆盖 Phase 1 所有合法/非法状态流转路径 |
| **测试矩阵** | Order 合法 5 条：INIT→PENDING_CONFIRM / PENDING_CONFIRM↔PENDING_CONFIRM / PENDING_CONFIRM→IN_PRODUCTION / PENDING_CONFIRM→CANCELLED / IN_PRODUCTION→CANCELLED | Order 非法 N 条：IN_PRODUCTION→PENDING_CONFIRM / COMPLETED→任意 / CANCELLED→任意 | MaterialTask 合法 2 条：INIT→PENDING_DESIGN / 任意非终态→CANCELLED | 非法 N 条：CANCELLED→任意 |
| **涉及文件** | `backend/src/test/.../statemachine/OrderStateMachineTest.java` + `MaterialTaskStateMachineTest.java` |
| **验收标准** | 所有合法流转 PASS + 所有非法流转抛出 InvalidStateTransitionException |
| **测试方法** | JUnit + Mockito |
| **交付物** | 状态机测试类 |
| **完成条件** | 单元测试全部通过 |

---

### P1-T35 · RBAC 三层权限测试

| 属性 | 内容 |
|---|---|
| **类型** | TEST |
| **关联故事** | NFR-S5/S6 + BR-13 |
| **前置依赖** | P1-T10 / P1-T19 |
| **目标** | 覆盖三层权限：前端路由守卫 + Spring Security @PreAuthorize + 数据可见性 Repository 注入 |
| **测试矩阵** | 第 1 层：AM 浏览器尝试访问 /bo/kanban → Vue Router 守卫拦截 | 第 2 层：DS 带 JWT 调用 A-06 assignTask → Spring Security 返回 403 | 第 3 层：DS 调 S-04 查看 AM 的订单 → Repository 查询返回空 | 数据可见性：三个 AM 各有订单 → 互相看不到对方订单；BO 全部可见 |
| **涉及文件** | 后端 MockMvc 集成测试 + 前端手动测试 |
| **验收标准** | 三层权限均生效 + 无越权漏洞 |
| **测试方法** | MockMvc 单元测试（后端）+ 浏览器手动（前端）|
| **交付物** | 后端 RBAC 集成测试类 |
| **完成条件** | 三层权限矩阵全部 PASS |

---

### P1-T36 · 文件上传集成测试

| 属性 | 内容 |
|---|---|
| **类型** | TEST |
| **关联故事** | AM-02 + NFR-F1/F2/F3 |
| **前置依赖** | P1-T14 / P1-T26 |
| **目标** | 覆盖成功上传 + 200MB 限制 + 格式白名单 + 重试 + MinIO 验证 |
| **测试矩阵** | 成功：上传 10MB zip → MinIO 有对象 + DB attachments 有记录 | 超限：上传 201MB 文件 → 预签名 URL 生成时返回 422 | 格式：上传 .exe → 422 格式不允许 | 重试：Step 2 模拟网络失败（mimic MinIO 拒绝）→ 自动重试 3 次 | 孤儿对象：Step 1 后前端崩溃 → 定时任务后孤儿对象被清理 | 下载鉴权：DS 用 S-08 下载 AM 的附件 → 403 |
| **涉及文件** | 后端 Service 层测试 |
| **验收标准** | 所有矩阵点 PASS |
| **测试方法** | JUnit + Mock MinioClient |
| **交付物** | FileStorageServiceTest + AttachmentUploader 手动测试报告 |
| **完成条件** | 6 个测试点全部 PASS |

---

### P1-T37 · 审计日志完整性测试

| 属性 | 内容 |
|---|---|
| **类型** | TEST |
| **关联故事** | BR-14 |
| **前置依赖** | P1-T12 |
| **目标** | 每个状态变更操作均产生正确审计记录 |
| **测试矩阵** | AM submitOrder → audit_logs 表新增 SUBMIT_ORDER 记录，before_state=null after_state=PENDING_CONFIRM operator_id=AM BO rejectOrder → REJECT_ORDER BO assignTask → ASSIGN_TASK + 联动物料任务的 ASSIGN_TASK AM cancelOrder → CANCEL_ORDER |
| **涉及文件** | 后端 AOP 切面测试 |
| **验收标准** | 每步操作 audit_logs 均有对应记录 + 字段完整 |
| **测试方法** | 集成测试：调用 Service 方法后查询 audit_logs |
| **交付物** | AuditLogAspectTest |
| **完成条件** | 4 个操作均产生正确审计记录 |

---

### P1-T38 · 站内通知触发测试

| 属性 | 内容 |
|---|---|
| **类型** | TEST |
| **关联故事** | BR-15 |
| **前置依赖** | P1-T13 |
| **目标** | BO 派发 → DS 收到待办通知 |
| **测试矩阵** | BO assignTask 后 → notifications 表新增 1 条 recipient_id=被派发 DS title="任务 ORD-xxx 已派发给你" is_read=0 |
| **涉及文件** | NotificationServiceTest |
| **验收标准** | BO 派发 → DB notifications 表有对应记录 |
| **测试方法** | 集成测试 |
| **交付物** | NotificationServiceTest |
| **完成条件** | BO 派发 → DS 有通知记录 |

---

### P1-T39 · XSS + 隐私脱敏测试

| 属性 | 内容 |
|---|---|
| **类型** | TEST |
| **关联故事** | NFR-S1/S2/S3/S4 |
| **前置依赖** | P1-T07 / P1-T08 |
| **目标** | XSS 存储前清洗 + 敏感字段脱敏 |
| **测试矩阵** | XSS：`<script>alert(1)</script>` 存入 DB → 取出为纯文本 `<img onerror=alert(1)>` → 清洗为空 | 脱敏：手机号 13812348888 → 138****8888（BO/DS 视图）；AM 看到完整 | 脱敏：邮箱 zhangming@example.com → z***@example.com |
| **涉及文件** | XSSCleanerTest + PrivacyMaskTest |
| **验收标准** | 常见 XSS Payload 全部清洗 + 脱敏格式正确 |
| **测试方法** | 单元测试 |
| **交付物** | 两个工具类测试 |
| **完成条件** | XSS + 脱敏测试全部 PASS |

---

### P1-T40 · Phase 1 端到端验收测试

| 属性 | 内容 |
|---|---|
| **类型** | TEST |
| **关联故事** | P1 全部 10 条验收标准 |
| **前置依赖** | P1-T33 + P1-T34 ~ P1-T39 全部通过 |
| **目标** | 完整覆盖架构方案中确认的 Phase 1 10 条验收标准（P1-01 ~ P1-10）|
| **验收标准清单** | P1-01 AM 能创建含物料类型+规格的订单 → order_no=ORD-{today}-{seq} + PENDING_CONFIRM | P1-02 AM 能上传参考素材 → MinIO + DB | P1-03 AM 能提交订单到 BO 待办 | P1-04 BO 能审阅订单详情（含需求基线快照）| P1-05 BO 能退回（必填原因）| P1-06 BO 能派发（必选 DS + deadline）→ DS 看到新任务 | P1-07 审计日志完整写入（SUBMIT_ORDER / REJECT_ORDER / ASSIGN_TASK）| P1-08 XSS 清洗生效 | P1-09 数据可见性隔离（DS 无法看到他人订单）| P1-10 站内通知推送（BO 派发 → DS 收到通知）|
| **测试方法** | 人工端到端操作 + 自动化测试脚本（可选 Playwright/Cypress）|
| **验收条件** | 10 条验收标准全部 PASS |
| **交付物** | 验收测试报告 |
| **完成条件** | 10 条全部 PASS + 报告提交 |

---

## Phase 1 完成标志

| 检查点 | 要求 |
|---|---|
| 容器 | docker compose up → 4 容器 healthy |
| DB | 11 表结构正确 + 种子数据就绪 |
| API | 11 个接口全部可调用 + Swagger UI 可访问 |
| FE | npm run build 无错误 + 8 页面可访问 |
| 状态机 | 单元测试全部 PASS |
| RBAC | 三层权限测试全部 PASS |
| 文件上传 | 成功 + 失败 + 重试 测试 PASS |
| 审计日志 | 4 操作均有记录 |
| 通知 | BO 派发 → DS 有通知 |
| XSS + 脱敏 | 清洗 + 脱敏测试 PASS |
| E2E | P1-01 ~ P1-10 全部 PASS |

**Phase 1 交付后进入人工审核 → 确认无误后启动 Phase 2**。

---

> **Phase 1 文档结束**。Phase 2~4 文档按本模式从 TASK_BREAKDOWN.md 派生，粒度与 Phase 1 保持一致。
