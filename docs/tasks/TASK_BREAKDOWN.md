# 单笔宣传物料订单交付管控系统 · 任务拆解主索引

> **文档类型**：任务拆解总览 / 跨阶段追溯矩阵
> **日期**：2026-10-23
> **依据**：PRD.md / USER_STORIES.md / GLOSSARY.md + 已确认的全栈架构方案（v0.1）
> **用途**：人工审核后的任务执行入口；所有 Phase 文档从本索引推导

---

## 0. 已确认的文档冲突清单（Task 执行前置注意）

在开始任何 Task 之前，必须明确以下冲突已记录。**Task 执行时以 PRD 为业务规则依据**，不得自行编造解决方案。

| 编号 | 冲突点 | GLOSSARY 写法 | PRD 写法 | Task 执行依据 |
|---|---|---|---|---|
| CONFLICT-01 | **releaseVersion 是否立即将 MaterialTask 置为 APPROVED** | A-11 写"放行后物料任务状态 → APPROVED" | §3.3.3 明确要求"APPROVED = BO 放行 + 客户确认两步；AM 客户确认后才触发 MaterialTask → APPROVED" | **以 PRD 为准**。BO releaseVersion 仅触发 VersionStatus → RELEASED，MaterialTask 保持 PENDING_APPROVAL；AM confirmVersion 才是 MaterialTask → APPROVED 触发点 |
| CONFLICT-02 | **canMarkCompleted 校验条件** | 未显式描述 | §3.2.3 要求"该订单下所有物料任务均为 APPROVED 状态"方可 COMPLETED | **严格 `count(status=APPROVED) == 总任务数`**，不能用"无活跃态"近似（若有 CANCELLED 任务，整单不可 COMPLETED）|
| CONFLICT-03 | **GLOSSARY 29 个业务 API vs 实际所需** | §03 列出 29 个业务操作动词 | — | 29 个为核心业务 API；另需 **8 个补充 API**（S-01~S-08，见 §3.2），GLOSSARY 未定义但为基础设施必要 |

---

## 1. 用户故事与 Phase 分配

| Phase | 核心链路 | 覆盖用户故事 | 新增实体（表） | 新增枚举 |
|---|---|---|---|---|
| **1** | 登记 → 审阅 → 派发 | AM-01 / AM-02 / AM-03 / BO-01 / BO-02 / BO-03 / DS-01 部分 | 11 张表 | 9 个 |
| **2** | 版本上传 → 内部评审 → 放行 | DS-01 完整 / DS-02 / DS-03 / DS-04 / BO-05 / BO-06 / DS-06 | 4 张表 | 3 个 |
| **3** | 客户评审 → 反馈 → 确认 → 交付 | AM-04 / AM-05 / AM-06 / AM-07 / AM-08 / BO-08 / DS-05 | 4 张表 | 3 个 |
| **4** | 看板 / 时间线 / 通知强化 / 异常 | BO-04 / BO-07 + M9 通知全量 | 1 张表 | 2 个 |

---

## 2. 任务编号规则与类型定义

### 2.1 任务编号

格式：`P{n}-T{nn}`，如 `P1-T01`、`P2-T15`。

### 2.2 任务类型

| 类型代码 | 含义 | 典型产出 |
|---|---|---|
| INFRA | 服务器 / 基础设施配置 | Docker Compose 文件、Nginx 配置、MinIO bucket 策略 |
| DB | 数据库建模与迁移 | DDL SQL、Flyway/Liquibase 迁移脚本 |
| BE-ENUM | 后端枚举定义 | Java enum 类、TypeScript enum |
| BE-ENT | 后端 JPA 实体 + Repository | Entity 类、Repository 接口 |
| BE-SM | 后端状态机实现 | 状态机类、流转校验、联动逻辑 |
| BE-SVC | 后端 Service 层 | 业务方法、审计 AOP、XSS 清洗、数据可见性 |
| BE-CTL | 后端 Controller / API | REST Controller、DTO、请求/响应处理 |
| BE-UTIL | 后端工具类 | IdGenerator、PrivacyMask、XSSCleaner |
| BE-SEC | 后端安全 | Spring Security 配置、JWT Filter、@PreAuthorize 注解 |
| FE-SC | 前端脚手架 | Vue 3 + Vite 项目、Pinia Store、Router |
| FE-API | 前端 API 封装 | Axios 实例、接口函数 |
| FE-COMP | 前端组件 | SFC 组件、Composables |
| FE-PAGE | 前端页面 | 完整页面路由 |
| API-INTEG | 前后端联调 | 接口契约对齐、Mock 替换、联调 Bug 修复 |
| TEST | 测试任务 | 单元测试、集成测试、验收测试、状态机测试 |
| CLEANUP | 跨阶段遗留清理 | API 补充、孤儿对象清理脚本、文档更新 |

---

## 3. API 清单

### 3.1 GLOSSARY 定义的 29 个核心业务 API

| 编号 | 方法 | 路径 | 动词 | 对应 Phase |
|---|---|---|---|---|
| A-01 | POST | `/api/v1/orders` | createOrder | P1 |
| A-02 | POST | `/api/v1/orders/{orderId}/attachments` | uploadAttachment | P1 |
| A-03 | POST | `/api/v1/orders/{orderId}/submit` | submitOrder | P1 |
| A-04 | GET | `/api/v1/orders/{orderId}/review` | reviewOrder | P1 |
| A-05 | POST | `/api/v1/orders/{orderId}/reject` | rejectOrder | P1 |
| A-06 | POST | `/api/v1/orders/{orderId}/assign` | assignTask | P1 |
| A-07 | POST | `/api/v1/material-tasks/{taskId}/reassign` | reassignTask | P2 |
| A-08 | POST | `/api/v1/material-tasks/{taskId}/versions` | uploadVersion | P2 |
| A-09 | POST | `/api/v1/versions/{versionId}/submit-review` | submitForReview | P2 |
| A-10 | GET | `/api/v1/orders/{orderId}/versions/compare?v1={id}&v2={id}` | compareVersions | P2 |
| A-11 | POST | `/api/v1/versions/{versionId}/release` | releaseVersion | P2 |
| A-12 | POST | `/api/v1/versions/{versionId}/reject` | rejectVersion | P2 |
| A-13 | POST | `/api/v1/versions/{versionId}/client-review` | initiateClientReview | P3 |
| A-14 | POST | `/api/v1/versions/{versionId}/feedbacks` | recordFeedback | P3 |
| A-15 | PATCH | `/api/v1/feedbacks/{feedbackId}/resolve` | resolveFeedback | P3 |
| A-16 | POST | `/api/v1/versions/{versionId}/confirm` | confirmVersion | P3 |
| A-17 | POST | `/api/v1/versions/{versionId}/mark-final` | markFinal | P3 |
| A-18 | POST | `/api/v1/orders/{orderId}/deliver` | markDelivered | P3 |
| A-19 | POST | `/api/v1/orders/{orderId}/archive` | archiveOrder | P3 |
| A-20 | POST | `/api/v1/orders/{orderId}/exceptions` | recordException | P4 |
| A-21 | PATCH | `/api/v1/exceptions/{exceptionId}/close` | closeException | P4 |
| A-22 | POST | `/api/v1/orders/{orderId}/cancel` | cancelOrder | P2（基础）/ P4（完整）|
| A-23 | POST | `/api/v1/material-tasks/{taskId}/cancel` | cancelTask | P2 |
| A-24 | GET | `/api/v1/orders/mine` | listMyOrders | P1 |
| A-25 | GET | `/api/v1/orders/kanban` | viewOrderKanban | P4 |
| A-26 | GET | `/api/v1/orders/{orderId}/versions` | listVersions | P2 |
| A-27 | GET | `/api/v1/versions/{versionId}/feedbacks` | listFeedbacks | P3 |
| A-28 | GET | `/api/v1/orders/{orderId}/archive-report` | exportArchiveReport | P4 |
| A-29 | GET | `/api/v1/orders/{orderId}/timeline` | viewTimeline | P4 |

### 3.2 GLOSSARY 之外的必要补充 API

| 编号 | 方法 | 路径 | 补充理由 | 对应 Phase |
|---|---|---|---|---|
| S-01 | POST | `/api/v1/auth/login` | JWT 登录（所有业务接口的认证前提）| P1 |
| S-02 | POST | `/api/v1/auth/logout` | 前端清理 token + 后端可选黑名单 | P1 |
| S-03 | POST | `/api/v1/auth/refresh` | Refresh Token 轮换 Access Token | P1 |
| S-04 | GET | `/api/v1/orders/{orderId}` | 三角色通用订单详情读取（含数据可见性过滤）；A-04 为 BO 审阅专用只读，此接口需同时返回通用字段 | P1 |
| S-05 | GET | `/api/v1/material-tasks/mine` | DS 我的任务列表；GLOSSARY A-24 仅定义了 AM 的 listMyOrders | P1 |
| S-06 | GET | `/api/v1/material-tasks/{taskId}` | BO/DS 任务详情读取 | P1 |
| S-07 | GET | `/api/v1/versions/{versionId}/download` | 下载鉴权：后端校验角色+数据可见性 → 生成限时 5min 预签名 GET URL → HTTP 302 | P2 |
| S-08 | GET | `/api/v1/attachments/{attachmentId}/download` | 参考素材附件下载，同 S-07 鉴权逻辑 | P1 |

### 3.3 文件上传确认接口（补充）

| 编号 | 方法 | 路径 | 说明 | 对应 Phase |
|---|---|---|---|---|
| S-09 | POST | `/api/v1/versions/confirm-upload` | Step 3 元数据确认：Step 1 生成 uploadSessionId → Step 2 直传 MinIO → Step 3 调此接口写入 DB（设计版本 + 版本文件） | P2 |

---

## 4. 任务总清单（按 Phase 分组）

### 4.1 Phase 1 · 登记 → 审阅 → 派发

| 编号 | 类型 | 名称 | 依赖 | 状态 |
|---|---|---|---|---|
| P1-T01 | INFRA | Docker Compose + 容器编排（MySQL + MinIO + Nginx + Spring Boot） | — | 待启动 |
| P1-T02 | INFRA | MinIO bucket 初始化 + 权限策略（ordermgmt bucket + 上传/下载角色）| P1-T01 | 待启动 |
| P1-T03 | DB | Phase 1 11 张表 DDL + Flyway 迁移脚本 | P1-T01 | 待启动 |
| P1-T04 | DB | 种子数据：三角色（AM/BO/DS）定义 + 各 1 测试账号 + 初始权限 | P1-T03 | 待启动 |
| P1-T05 | BE-ENUM | Phase 1 9 个枚举类（Java + TypeScript 同步）| P1-T01 | 待启动 |
| P1-T06 | BE-UTIL | IdGenerator（order_no 格式 ORD-YYYYMMDD-NNN + 唯一并发安全）| P1-T05 | 待启动 |
| P1-T07 | BE-UTIL | XSSCleaner（Hutool 集成 + 白名单标签策略）| P1-T01 | 待启动 |
| P1-T08 | BE-UTIL | PrivacyMask（手机号 138\*\*\*\*8888 + 邮箱 z\*\*\*@example.com）| P1-T01 | 待启动 |
| P1-T09 | BE-ENT | Phase 1 实体 + Repository（11 个 JPA Entity + 对应 Repository）| P1-T03, P1-T05 | 待启动 |
| P1-T10 | BE-SEC | Spring Security 配置 + JWT Filter + @PreAuthorize 注解体系 | P1-T09 | 待启动 |
| P1-T11 | BE-CTL | S-01/S-02/S-03 认证 Controller（login/logout/refresh）| P1-T10 | 待启动 |
| P1-T12 | BE-SVC | 审计日志 AOP 切面（17 个 AuditActionType 覆盖）| P1-T09 | 待启动 |
| P1-T13 | BE-SVC | 站内通知基础 Service（Spring Event + ApplicationListener + 异步线程池）| P1-T09 | 待启动 |
| P1-T14 | BE-SVC | 预签名 URL 生成器（MinIO SDK + PUT/GET URL + 过期控制）| P1-T02, P1-T09 | 待启动 |
| P1-T15 | BE-SM | Order 状态机（4 态：INIT→PENDING_CONFIRM→IN_PRODUCTION/CANCELLED + PENDING_CONFIRM↔PENDING_CONFIRM 退回重提）| P1-T05, P1-T09, P1-T12 | 待启动 |
| P1-T16 | BE-SM | MaterialTask 状态机（5 态基础流转：INIT→PENDING_DESIGN + 终态 CANCELLED）| P1-T05, P1-T09, P1-T12 | 待启动 |
| P1-T17 | BE-SVC | OrderService（createOrder + uploadAttachment + submitOrder + rejectOrder + assignTask + cancelOrder 基础 + listMyOrders）| P1-T15, P1-T16, P1-T14 | 待启动 |
| P1-T18 | BE-SVC | MaterialTaskService（assignTask 内部创建 PENDING_DESIGN 任务 + data visibility filter）| P1-T16, P1-T09 | 待启动 |
| P1-T19 | BE-SVC | OrderService · 数据可见性 Repository 注入（AM 仅自己 / BO 全部 / DS 仅自己任务）| P1-T09 | 待启动 |
| P1-T20 | BE-CTL | A-01/A-02/A-03/A-04/A-05/A-06/A-22/A-24 Controller | P1-T17 | 待启动 |
| P1-T21 | BE-CTL | S-04/S-05/S-06/S-08 通用只读 Controller | P1-T19 | 待启动 |
| P1-T22 | FE-SC | Vue 3 + Vite + TypeScript 脚手架 + Pinia + Vue Router + 三角色路由守卫 | — | 待启动 |
| P1-T23 | FE-SC | 全局 Axios 拦截器（JWT 自动附加 + 401 自动刷新 + 错误处理）| P1-T22 | 待启动 |
| P1-T24 | FE-API | Phase 1 所有 API 函数封装（认证 + 订单 CRUD + 通用只读）| P1-T23 | 待启动 |
| P1-T25 | FE-PAGE | LoginView（登录页 + JWT 存储 + 角色跳转）| P1-T24 | 待启动 |
| P1-T26 | FE-COMP | 预签名直传附件组件（Step 1 预签名 URL → Step 2 PUT MinIO → Step 3 确认 + 失败重试指数退避）| P1-T24 | 待启动 |
| P1-T27 | FE-PAGE | AM CreateOrder（表单 + material_specs 动态行 + 附件上传 + 提交）| P1-T26 | 待启动 |
| P1-T28 | FE-PAGE | AM MyOrderList（列表 + 状态标签 + 筛选 + 游标分页）| P1-T24 | 待启动 |
| P1-T29 | FE-PAGE | 通用 OrderDetail（需求基线 + 附件列表 + 物料任务列表 + 退回原因显示）| P1-T26 | 待启动 |
| P1-T30 | FE-PAGE | BO PendingReviewList（待审阅订单列表 + 按 submission 时间倒序）| P1-T24 | 待启动 |
| P1-T31 | FE-PAGE | BO OrderReview + RejectOrder + AssignTask 三个子页面 | P1-T29 | 待启动 |
| P1-T32 | FE-PAGE | DS MyTaskList + TaskDetail（只读需求基线 + 附件）| P1-T24 | 待启动 |
| P1-T33 | API-INTEG | Phase 1 前后端联调（认证 + 订单登记 + 审阅 + 退回 + 派发全链路 Mock 替换 → 真实 API）| P1-T20, P1-T21, P1-T25~P1-T32 | 待启动 |
| P1-T34 | TEST | 状态机单元测试（Order 4 态所有合法/非法流转 + MaterialTask 初始流转）| P1-T15, P1-T16 | 待启动 |
| P1-T35 | TEST | RBAC 三层权限测试（路由守卫 + @PreAuthorize + 数据可见性注入）| P1-T10, P1-T19 | 待启动 |
| P1-T36 | TEST | 文件上传集成测试（成功 + 失败重试 + 孤儿对象清理 + 下载鉴权 S-08）| P1-T14, P1-T26 | 待启动 |
| P1-T37 | TEST | 审计日志完整性测试（每步操作 audit_logs 表正确写入）| P1-T12 | 待启动 |
| P1-T38 | TEST | 站内通知触发测试（BO 派发 → DS 收到待办）| P1-T13 | 待启动 |
| P1-T39 | TEST | XSS 清洗 + 隐私脱敏测试（输入 script 标签 → DB 无 HTML；手机号/邮箱脱敏）| P1-T07, P1-T08 | 待启动 |
| P1-T40 | TEST | 端到端验收测试（覆盖 P1-01~P1-10 全部验收标准）| P1-T33, P1-T34~P1-T39 | 待启动 |

**Phase 1 任务数量**：40

---

### 4.2 Phase 2 · 版本上传 → 内部评审 → 放行

| 编号 | 类型 | 名称 | 依赖 | 状态 |
|---|---|---|---|---|
| P2-T01 | DB | Phase 2 4 张表 DDL（design_versions / version_files / review_records / release_records） + Flyway 迁移 | P1 全部完成 | 待启动 |
| P2-T02 | BE-ENUM | Phase 2 3 个枚举（VersionStatus / ReviewResult / AssignmentType）+ VersionStatus 6 态流转枚举 | P2-T01 | 待启动 |
| P2-T03 | BE-ENT | Phase 2 实体 + Repository（DesignVersion / VersionFile / ReviewRecord / ReleaseRecord）| P2-T01, P2-T02 | 待启动 |
| P2-T04 | BE-UTIL | VersionNoGenerator（每个 MaterialTask 内 MAX + 1，并发安全）| P2-T03 | 待启动 |
| P2-T05 | BE-SM | Version 状态机（6 态：DRAFT / PENDING_INTERNAL_REVIEW / RELEASED / IN_CLIENT_REVIEW / CLIENT_CONFIRMED / FINAL + 完整流转规则）| P2-T02, P1-T12 审计 | 待启动 |
| P2-T06 | BE-SM | MaterialTask 状态机补全（PENDING_DESIGN ↔ PENDING_APPROVAL ↔ PENDING_REVISE 循环；**注意 CONFLICT-01：BO releaseVersion 不触发 APPROVED**）| P1-T16, CONFLICT-01 已确认 | 待启动 |
| P2-T07 | BE-SVC | DesignVersionService（uploadVersion + submitForReview + compareVersions + releaseVersion + rejectVersion 基础）| P2-T04, P2-T05, P2-T06 | 待启动 |
| P2-T08 | BE-SVC | MaterialTaskService 扩展（reassignTask + cancelTask + 版本上传后置校验 is_overdue）| P1-T18, P2-T06 | 待启动 |
| P2-T09 | BE-SVC | 重新分派 Assignment 记录写入（INITIAL → REASSIGN + previous_assignee_id + 重新分派原因必填）| P2-T08 | 待启动 |
| P2-T10 | BE-CTL | A-07/A-08/A-09/A-10/A-11/A-12/A-23/A-26 Controller + S-09 confirmVersionUpload | P2-T07, P2-T08 | 待启动 |
| P2-T11 | BE-CTL | S-07 版本下载鉴权 Controller（限时 5min 预签名 GET URL）| P1-T14, P1-T19 数据可见性 | 待启动 |
| P2-T12 | FE-API | Phase 2 API 函数封装 | P2-T10, P2-T11 | 待启动 |
| P2-T13 | FE-COMP | 版本上传组件（Step 1 → Step 2 PUT MinIO → Step 3 S-09 确认 + 失败重试 + 设计说明必填校验）| P1-T26 复用模板 | 待启动 |
| P2-T14 | FE-PAGE | DS TaskDetail 扩展（版本历史列表 + VersionStatus 标签 + 反馈占位 + 上传按钮 + 提交评审按钮）| P1-T32 | 待启动 |
| P2-T15 | FE-PAGE | BO InternalReview（版本并排对比面板 + 放行/退回操作 + 退回原因必填）| P2-T12 | 待启动 |
| P2-T16 | API-INTEG | Phase 2 前后端联调（版本上传 → 提交评审 → BO 评审 → 放行/退回全链路）| P2-T10, P2-T11, P2-T13~P2-T15 | 待启动 |
| P2-T17 | TEST | Version 状态机单元测试（6 态所有合法/非法流转）| P2-T05 | 待启动 |
| P2-T18 | TEST | MaterialTask 状态机循环流转测试（PENDING_REVISE ↔ PENDING_APPROVAL）+ **CONFLICT-01 确认：releaseVersion 后仍为 PENDING_APPROVAL** | P2-T06 | 待启动 |
| P2-T19 | TEST | 文件上传全链路测试（200MB 限制 NFR-F1 + 格式白名单 NFR-F2 + 版本自动编号 + 孤儿对象清理定时任务）| P2-T07, P1-T14 | 待启动 |
| P2-T20 | TEST | 审计日志完整性测试（UPLOAD_VERSION / SUBMIT_REVIEW / RELEASE_VERSION / REJECT_VERSION 各操作 audit_logs 正确写入）| P1-T12 | 待启动 |
| P2-T21 | TEST | RBAC 扩展测试（DS 仅上传自己的任务；BO 可放行所有版本；版本下载鉴权 S-07）| P1-T35 复用模板 | 待启动 |
| P2-T22 | TEST | 端到端验收测试（覆盖 DS-01~DS-04 / BO-05~BO-06 / DS-06 / A-07 / A-23 验收要点）| P2-T16, P2-T17~P2-T21 | 待启动 |

**Phase 2 任务数量**：22

---

### 4.3 Phase 3 · 客户评审 → 反馈 → 确认 → 交付

| 编号 | 类型 | 名称 | 依赖 | 状态 |
|---|---|---|---|---|
| P3-T01 | DB | Phase 3 4 张表 DDL（client_reviews / feedback_items / confirmation_records / delivery_archives） + Flyway | P2 全部完成 | 待启动 |
| P3-T02 | BE-ENUM | Phase 3 3 个枚举（FeedbackStatus / FeedbackType / FeedbackSource）| P3-T01 | 待启动 |
| P3-T03 | BE-ENT | Phase 3 实体 + Repository | P3-T01, P3-T02 | 待启动 |
| P3-T04 | BE-SM | **CONFLICT-01 兑现点**：confirmVersion → MaterialTask.APPROVED 联动逻辑实现 + Order 状态 canMarkCompleted 严格 count 校验 | P3-T03, P2-T06, CONFLICT-01/CONFLICT-02 已确认 | 待启动 |
| P3-T05 | BE-SVC | ClientReviewService（initiateClientReview + recordFeedback + resolveFeedback + 反馈自动过期 + 反馈需修改触发 MaterialTask → PENDING_REVISE）| P3-T03, P2-T06 | 待启动 |
| P3-T06 | BE-SVC | ConfirmationRecordService（confirmVersion：不可篡改 + confirmer_name + 系统时间戳）| P3-T03, P1-T12 | 待启动 |
| P3-T07 | BE-SVC | OrderService 扩展（markDelivered + archiveOrder + canMarkCompleted 严格校验 + PDF 归档报告生成）| P3-T03, P3-T04 | 待启动 |
| P3-T08 | BE-CTL | A-13/A-14/A-15/A-16/A-17/A-18/A-19/A-27/A-28 Controller | P3-T05~P3-T07 | 待启动 |
| P3-T09 | FE-API | Phase 3 API 函数封装 | P3-T08 | 待启动 |
| P3-T10 | FE-PAGE | AM ClientReview（发起评审 + 记录反馈条目 + 客户确认记录）| P3-T09 | 待启动 |
| P3-T11 | FE-PAGE | AM MyOrderList 扩展（我的订单 + 客户评审状态 + 反馈数量）| P1-T28 | 待启动 |
| P3-T12 | FE-PAGE | DS TaskDetail 扩展（FeedbackView：按版本聚合反馈条目 + 标记已解决 EXPIRED 保护）| P2-T14 | 待启动 |
| P3-T13 | FE-PAGE | BO OrderArchive + Timeline 初始实现 | P3-T09 | 待启动 |
| P3-T14 | API-INTEG | Phase 3 前后端联调（发起评审 → 反馈 → 确认 → 终稿 → 交付闭环）| P3-T08, P3-T09, P3-T10~P3-T13 | 待启动 |
| P3-T15 | TEST | **CONFLICT-01/CONFLICT-02 专项测试**（confirmVersion 触发 APPROVED；canMarkCompleted 严格 count；CANCELLED 任务存在时整单不可 COMPLETE；终态不可回退）| P3-T04 | 待启动 |
| P3-T16 | TEST | ConfirmationRecord 不可篡改测试（DB 层面 update 保护 + Service 层禁止修改）| P3-T06 | 待启动 |
| P3-T17 | TEST | FeedbackItem 反馈过期自动置 EXPIRED 测试（同订单更高版本 FINAL 时触发）| P3-T05 | 待启动 |
| P3-T18 | TEST | Feedback 需修改 → MaterialTask PENDING_REVISE 联动测试 | P3-T05, P2-T06 | 待启动 |
| P3-T19 | TEST | 审计日志完整性测试（RECORD_FEEDBACK / CONFIRM_VERSION / MARK_FINAL / MARK_DELIVERED / ARCHIVE_ORDER）| P1-T12 | 待启动 |
| P3-T20 | TEST | RBAC 扩展测试（AM 仅能发起自己订单的客户评审；DS 仅能标记自己任务的反馈已解决）| P1-T35 | 待启动 |
| P3-T21 | TEST | 端到端验收测试（覆盖 AM-04~AM-08 / BO-08 / DS-05 验收要点 + CONFLICT-01/CONFLICT-02 全链路验证）| P3-T14, P3-T15~P3-T20 | 待启动 |

**Phase 3 任务数量**：21

---

### 4.4 Phase 4 · 看板 / 时间线 / 通知 / 异常

| 编号 | 类型 | 名称 | 依赖 | 状态 |
|---|---|---|---|---|
| P4-T01 | DB | Phase 4 1 张表 DDL（exception_records）+ Flyway | P3 全部完成 | 待启动 |
| P4-T02 | BE-ENUM | Phase 4 2 个枚举（ExceptionStatus / ExceptionType）| P4-T01 | 待启动 |
| P4-T03 | BE-ENT | ExceptionRecord 实体 + Repository | P4-T01, P4-T02 | 待启动 |
| P4-T04 | BE-SVC | ExceptionRecordService（recordException + closeException + 异常自动生成：task_deadline 已过 + 任务非终态 → DELAY 类型 + PENDING_CONFIRM 状态）| P4-T03 | 待启动 |
| P4-T05 | BE-SVC | 异常自动生成定时任务（@Scheduled cron="0 0 4 * * ?" 每日凌晨 4 点扫描）| P4-T04 | 待启动 |
| P4-T06 | BE-SVC | ArchiveOrder 前置条件校验扩展（归档前所有异常须为 CLOSED 状态）| P4-T04, P3-T07 | 待启动 |
| P4-T07 | BE-SVC | KanbanService（看板聚合查询：按 4 种 OrderStatus GROUP BY + 筛选条件）| P1-T19 数据可见性 | 待启动 |
| P4-T08 | BE-SVC | TimelineService（从 audit_logs 聚合全流程时间线 + 游标分页）| P1-T12 审计 | 待启动 |
| P4-T09 | BE-SVC | **待确认**：站内通知 SSE 推送（Server-Sent Events / WebSocket / 仅轮询 三种方案需决策；架构方案倾向 SSE）| P1-T13 | 待决策 |
| P4-T10 | BE-CTL | A-20/A-21/A-25/A-29/A-22（取消订单完整实现） Controller | P4-T04~P4-T08 | 待启动 |
| P4-T11 | BE-UTIL | 孤儿对象清理定时任务（每日凌晨 3:00；MinIO 列出对象 → 对比 DB attachments + version_files → 清理 24h 无 DB 记录的对象）| P1-T14 | 待启动 |
| P4-T12 | FE-API | Phase 4 API 函数封装 | P4-T10 | 待启动 |
| P4-T13 | FE-PAGE | BO OrderKanban（4 列看板 + 负责人筛选 + 时间筛选 + NFR-P5 1.5s 加载）| P4-T12 | 待启动 |
| P4-T14 | FE-PAGE | BO Timeline（全流程时间线 + 操作人 + 状态变更 + 可导出）| P4-T12 | 待启动 |
| P4-T15 | FE-PAGE | BO ExceptionRecord 管理页（创建异常 + 记录处理 + 关闭）| P4-T12 | 待启动 |
| P4-T16 | FE-COMP | 站内通知铃铛（未读数量 + 点击展开 + 全部标记已读）| P4-T09 方案确定后 | 待启动 |
| P4-T17 | API-INTEG | Phase 4 前后端联调（看板 + 时间线 + 异常 + 通知 + 孤儿清理）| P4-T10~P4-T16 | 待启动 |
| P4-T18 | TEST | 看板性能测试（加载耗时 ≤ 1.5s，NFR-P5）| P4-T07, P4-T13 | 待启动 |
| P4-T19 | TEST | 异常自动生成测试（deadline 过期 → DELAY 类型异常自动创建）| P4-T04, P4-T05 | 待启动 |
| P4-T20 | TEST | ArchiveOrder 异常前置条件测试（未关闭异常 → 422）| P4-T06 | 待启动 |
| P4-T21 | TEST | 孤儿对象清理集成测试（Step 1 获取预签名 URL → Step 2 直传后不做 Step 3 → 24h 后定时任务清理）| P4-T11 | 待启动 |
| P4-T22 | TEST | 取消订单级联测试（IN_PRODUCTION → CANCELLED 级联所有非终态物料任务）| P1-T15, P1-T16 | 待启动 |
| P4-T23 | TEST | 审计日志完整性测试（RECORD_EXCEPTION / CLOSE_EXCEPTION / CANCEL_ORDER / CANCEL_TASK）| P1-T12 | 待启动 |
| P4-T24 | TEST | 端到端验收测试（覆盖 BO-04 / BO-07 + M9 通知全量 + 所有取消/异常场景）| P4-T17, P4-T18~P4-T23 | 待启动 |

**Phase 4 任务数量**：24

---

## 5. 用户故事覆盖检查表（22 条全覆盖）

| 故事 | 验收要点 | 对应 Task | Phase |
|---|---|---|---|
| **AM-01** 标准化表单登记 | 必填字段校验（物料类型/规格/场景/截止/客户名称）| P1-T17, P1-T20, P1-T27 | P1 |
| **AM-02** 上传参考文件素材 | 多文件 + 关联订单 + 详情可见 | P1-T14, P1-T17, P1-T26, P1-T27, P1-T29 | P1 |
| **AM-03** 提交订单给 BO | 状态变 PENDING_CONFIRM + 出现在 BO 待办 | P1-T15, P1-T17, P1-T20, P1-T30 | P1 |
| **AM-04** 查看我负责的订单状态 | 我的订单列表 + 状态 + 阶段 | P1-T19, P1-T20(A-24), P1-T28 | P1 |
| **AM-05** 将版本分享客户评审 | 对指定版本发起 + 反馈绑定版本 | P3-T05, P3-T08(A-13), P3-T10 | P3 |
| **AM-06** 记录客户修改意见 | 条目化 + 关联版本 | P3-T05, P3-T08(A-14), P3-T10 | P3 |
| **AM-07** 客户确认留痕 | 确认人/时间/版本号 + 不可篡改 | P3-T06, P3-T08(A-16), P3-T15, P3-T16 | P3 |
| **AM-08** 标记已交付归档 | 订单 COMPLETED + 全流程时间线 | P3-T07, P3-T08(A-18), P3-T13, P4-T08 | P3/P4 |
| **BO-01** 审阅订单详情 | 待办列表 + 完整需求 + 附件 | P1-T20(A-04), P1-T29, P1-T30 | P1 |
| **BO-02** 退回并注明补充 | 退回原因必填 + 回到 AM 待办 | P1-T17, P1-T20(A-05), P1-T31 | P1 |
| **BO-03** 分派给具体 DS + 截止 | 派发记录 + 截止 + 优先级 + DS 收到任务 | P1-T16, P1-T17, P1-T18, P1-T20(A-06), P1-T31 | P1 |
| **BO-04** 看板总览所有订单 | 状态列 + 负责人筛选 + 时间筛选 | P4-T07, P4-T10(A-25), P4-T13 | P4 |
| **BO-05** 版本历史 + 版本对比 | 时间倒序 + 并排对比 + 差异 | P2-T07(compareVersions), P2-T10(A-10), P2-T15 | P2 |
| **BO-06** 放行至客户评审 | 放行记录 + 未放行不可进客户评审 | P2-T05, P2-T07, P2-T10(A-11), **CONFLICT-01 专项验证** | P2 |
| **BO-07** 记录异常与处理 | 类型 + 描述 + 处理 + 可追溯 | P4-T04, P4-T10(A-20/A-21), P4-T15 | P4 |
| **BO-08** 闭环归档 | COMPLETED + 时间线可导出 | P3-T07, P3-T08(A-19), P4-T08(A-29), P4-T14 | P3/P4 |
| **DS-01** 接收任务 + 查看需求基线 | 我的任务列表 + 截止时间 + 完整基线 | P1-T18, P1-T21(S-05/S-06), P1-T32 | P1 |
| **DS-02** 上传设计稿（自动版本号）| 自动递增 V1/V2 + 历史版本保留 | P2-T04, P2-T07(uploadVersion), P2-T10(A-08), P2-T13 | P2 |
| **DS-03** 填写设计说明变更要点 | 设计说明必填 + 变更要点 | P2-T13 前端校验 + P2-T07 后端校验 | P2 |
| **DS-04** 提交至内部评审 | 版本 → PENDING_INTERNAL_REVIEW + 进入 BO 待办 | P2-T05, P2-T06, P2-T07(submitForReview), P2-T10(A-09), P2-T14 | P2 |
| **DS-05** 查看反馈并迭代 | 按版本聚合 + 标记已解决 + 不可 EXPIRED 标记 | P3-T05, P3-T08(A-27), P3-T12, P3-T17 | P3 |
| **DS-06** 收到放行后标记终稿 | 放行后可标记 + FINAL 版本明确标识 | P2-T05, P2-T07, P2-T10(A-17), **注意：DS-06 的"收到放行"对应 CONFLICT-01 解决后（confirmVersion → APPROVED）才标记 FINAL** | P2 |

**全覆盖验证**：22 条故事 100% 覆盖，每条故事至少有 1 个对应 Task。

---

## 6. 非目标事项 · 明确排除清单

以下事项**不列入**任何 Phase，不创建 Task，不做接口预留。

| 编号 | 非目标 | 排除理由 |
|---|---|---|
| EXCL-01 | 合同与财务（报价/合同/开票/收款）| PRD §6.1 显式排除 |
| EXCL-02 | 在线设计（画布/协作）| PRD §6.2 显式排除 |
| EXCL-03 | 印刷生产（对接印刷厂/排程/工单）| PRD §6.3 显式排除 |
| EXCL-04 | 物流配送（发货/物流/签收）| PRD §6.4 显式排除 |
| EXCL-05 | 外部消息通知（微信/短信/邮件）| PRD §6.5 显式排除（M9 站内提醒保留，外部渠道不接）|
| EXCL-06 | 客户自助门户 | PRD §6.6 显式排除 |
| EXCL-07 | 多租户 / SaaS 化 | PRD §6.7 显式排除 |
| EXCL-08 | 数据分析与报表 | PRD §6.8 显式排除 |
| EXCL-09 | 移动端 App | PRD §6.9 显式排除 |
| EXCL-10 | 对象存储之外的其他存储后端选型 | Phase 1 确定 MinIO 自托管，不再评估 S3/Oss/COS |
| EXCL-11 | Kubernetes 部署 | MVP 阶段用 Docker Compose，K8s 不列入任何 Phase |

---

## 7. 跨 Phase 前置依赖总览

```
P1 INFRA ──→ P1 DB ──→ P1 BE ──→ P1 FE ──→ P1 API-INTEG ──→ P1 TEST ──┐
                                                                          │
                                                                          ▼
Phase 2 全部（46 张表 + 2 BE + 2 FE + 11 测试） ← P1 全部完成           │
                                                                          │
                                                                          ▼
Phase 3 全部                                                             │
                                                                          │
                                                                          ▼
Phase 4 全部                                                             │
                                                                          │
                                                                          ▼
                                                              最终 E2E 验收
```

**Phase 内部串行依赖**：INFRA → DB → BE-ENUM/UTIL → BE-ENT → BE-SM/SVC → BE-CTL → FE-SC → FE-API → FE-PAGE → API-INTEG → TEST

**Phase 内部可并行**：
- BE-ENUM、BE-UTIL、FE-SC 三者可并行（均依赖 INFRA 但互不依赖）
- BE-SM 内部 Order 和 MaterialTask 可并行（P1-T15 与 P1-T16）
- FE-PAGE 内 AM CreateOrder、BO PendingReviewList、DS MyTaskList 三者可并行

---

## 8. 执行限制重申

| 限制项 | 说明 |
|---|---|
| 不得自行编造 CONFLICT 解决方案 | CONFLICT-01/02/03 由 **任务执行时** 按 PRD 为准实现，Task 文档已标注依赖 |
| 不得修改 PRD / USER_STORIES.md / GLOSSARY.md | 三份需求文档为只读 |
| 不得把 Phase 2-4 任务前置到 Phase 1 | 每个 Phase 有明确边界 |
| 不得把非目标事项纳入任何 Phase | 见 §6 排除清单 |
| 先人工审核 Task 文档，再执行 Task | 本文档完成后等待人工确认 |

---

> **本索引文档为 Task 拆解唯一权威来源**。各 Phase 详细文档（TASKS_PHASE_1.md ~ TASKS_PHASE_4.md）均从本索引派生，不新增 Task 也不删减 Task，仅为每个 Task 补充详细执行说明。
