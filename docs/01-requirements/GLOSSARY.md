# 单笔宣传物料订单交付管控系统 · 领域术语词典（Glossary）

> **文档类型**：统一术语词典（Single Source of Truth for Naming）
> **关联文档**：[PRD.md](./PRD.md)、[用户旅程与原子故事分解(1).md](./用户旅程与原子故事分解(1).md)、[验收标准清单.md](./验收标准清单.md)
> **用途**：为 MySQL 物理建模、Java 后端实体开发、TypeScript 类型定义、Vue 3 前端组件开发提供唯一命名权威来源，杜绝术语混乱与 AI 命名幻觉。
> **版本**：MVP v0.1 · FROZEN

---

## 00 命名约定总则

| 层 | 语言/框架 | 命名规则 | 示例 |
|---|---|---|---|
| MySQL 表名 | — | snake_case · 复数 | `orders`、`material_tasks` |
| MySQL 列名 | — | snake_case · 单数 | `order_no`、`material_type` |
| MySQL 索引 | — | `idx_{表缩写}_{列}` 或 `uk_{表缩写}_{列}` | `idx_ord_created_at`、`uk_mts_version_no` |
| Java 实体类 | Spring Boot / JPA | PascalCase | `Order`、`MaterialTask` |
| Java 字段 | — | camelCase | `orderNo`、`materialType` |
| Java 枚举类 | — | PascalCase + UPPER_SNAKE 值 | `OrderStatus.PENDING_CONFIRM` |
| Java Service 方法 | — | camelCase · 动词前缀 | `submitOrder()`、`assignTask()` |
| TypeScript 类型/接口 | — | PascalCase | `Order`、`MaterialTask` |
| TypeScript 属性 | — | camelCase | `orderNo`、`materialType` |
| TypeScript 枚举 | — | PascalCase + UPPER_SNAKE 值 | `OrderStatus.PENDING_CONFIRM` |
| Vue 3 组件 | Vue 3 SFC | PascalCase · 多词 | `OrderListPage`、`VersionComparePanel` |
| Vue 3 Composable | — | `use` 前缀 + PascalCase | `useOrderList`、`useVersionUpload` |
| REST API 路径 | — | kebab-case · 复数资源 | `/api/v1/orders/{id}/submit` |
| 枚举值（全栈统一） | — | UPPER_SNAKE_CASE | `PENDING_CONFIRM`、`IN_PRODUCTION` |

---

## 01 核心领域实体（Domain Entities）

### 1.1 账户与权限实体

| # | 中文领域概念 | 统一英文标识符 | MySQL 表名 | Java 类名 | TypeScript 类型名 | 业务含义与边界定义 |
|---|---|---|---|---|---|---|
| E-01 | 用户账号 | `User` | `users` | `User` | `User` | 系统登录主体，含用户名、密码哈希、邮箱、手机号、状态。一个用户可被分配一个角色（AM / BO / DS）。非 PRD 显式实体但状态机流转与审计日志依赖此实体的身份信息。 |
| E-02 | 角色 | `Role` | `roles` | `Role` | `Role` | RBAC 角色定义，枚举值为 AM（客户经理）、BO（业务负责人）、DS（设计制作人员）。用户与角色为 N:1 关系。 |
| E-03 | 用户-角色关联 | `UserRole` | `user_roles` | `UserRole` | `UserRole` | 用户与角色的中间关联表，支撑 RBAC 权限矩阵（PRD §2.2）。一个用户至少归属一个角色。 |

### 1.2 订单与需求实体

| # | 中文领域概念 | 统一英文标识符 | MySQL 表名 | Java 类名 | TypeScript 类型名 | 业务含义与边界定义 |
|---|---|---|---|---|---|---|
| E-04 | 订单 | `Order` | `orders` | `Order` | `Order` | 系统核心实体。由客户经理（AM）通过标准化表单创建，承载一笔宣传物料订单的全生命周期。含订单编号（`order_no`，格式 `ORD-YYYYMMDD-NNN`）、客户名称（`customer_name`）、使用场景（`use_scene`）、交付截止日期（`delivery_deadline`）、备注（`remark`）、订单状态（`status`）。订单为 1:N 物料任务的父实体。边界：一个订单可含多种物料/多规格，但同一物料类型的不同规格须拆分为独立物料任务（EC-B 规则）。 |
| E-05 | 物料规格行 | `MaterialSpec` | `material_specs` | `MaterialSpec` | `MaterialSpec` | 订单下的物料规格行项。含物料类型（`material_type`）、规格尺寸（`spec_size`）。一个订单 1:N 物料规格行，每个物料规格行在 BO 派发时生成一个对应的物料任务（MaterialTask）。边界：同一物料类型的不同规格不可合并为同一物料任务（PRD EC-B / BR-03 联动规则）。 |
| E-06 | 需求基线 | `RequirementBaseline` | `requirement_baselines` | `RequirementBaseline` | `RequirementBaseline` | 订单提交时冻结的需求快照，用于设计人员准确理解需求。含物料类型、规格尺寸、使用场景、交付截止日期、客户名称、备注的快照副本及基线版本号（`baseline_version`）。边界：基线一旦生成不可修改（BR-02）；订单被退回后重新提交时生成新版本基线，旧基线保留留痕。 |
| E-07 | 附件 | `Attachment` | `attachments` | `Attachment` | `Attachment` | 客户经理上传的参考素材文件。含文件名（`file_name`）、文件大小（`file_size`）、存储路径（`storage_path`）、关联订单 ID。边界：允许格式为 jpg/png/pdf/ai/psd/zip 等（NFR-F2）；单文件 ≤ 200MB（NFR-F1）；采用预签名 URL 直传对象存储（NFR-F3）。 |

### 1.3 任务分派实体

| # | 中文领域概念 | 统一英文标识符 | MySQL 表名 | Java 类名 | TypeScript 类型名 | 业务含义与边界定义 |
|---|---|---|---|---|---|---|
| E-08 | 物料任务 | `MaterialTask` | `material_tasks` | `MaterialTask` | `MaterialTask` | 物料规格行派发后生成的设计任务。由业务负责人（BO）派发给设计制作人员（DS）。含被派发人 ID（`assignee_id`）、截止时间（`task_deadline`）、优先级（`priority`）、任务状态（`status`）、关联订单 ID、关联物料规格行 ID。边界：一个物料规格行 → 一个物料任务（1:1）；任务状态机独立运转（PENDING_DESIGN → PENDING_APPROVAL → PENDING_REVISE ↔ PENDING_APPROVAL → APPROVED）。 |
| E-09 | 派发记录 | `Assignment` | `assignments` | `Assignment` | `Assignment` | BO 派发/重新分派操作的留痕记录。含派发人 ID（`assigner_id`）、被派发人 ID（`assignee_id`）、截止时间（`deadline`）、优先级（`priority`）、派发备注（`remark`）、派发类型（`assignment_type`：初始派发 / 重新分派）、原被派发人 ID（`previous_assignee_id`，仅重新分派时）。边界：重新分派须记录原因（如原被派发人请假/离职/超负荷，EC-D）。 |

### 1.4 设计版本实体

| # | 中文领域概念 | 统一英文标识符 | MySQL 表名 | Java 类名 | TypeScript 类型名 | 业务含义与边界定义 |
|---|---|---|---|---|---|---|
| E-10 | 设计版本 | `DesignVersion` | `design_versions` | `DesignVersion` | `DesignVersion` | 设计制作人员上传的设计稿版本。含版本号（`version_no`，自动递增 V1/V2/V3…）、设计说明（`design_note`）、变更要点（`change_summary`）、上传人 ID（`uploader_id`）、关联物料任务 ID、版本状态（`status`）。边界：版本号系统自动生成不可手动修改（BR-05）；历史版本永久留存不可删除不可覆盖（BR-07）；每次上传须填写设计说明（BR-06）。 |
| E-11 | 版本文件 | `VersionFile` | `version_files` | `VersionFile` | `VersionFile` | 设计版本关联的实际文件产物。含文件名（`file_name`）、文件大小（`file_size`）、存储路径（`storage_path`）、关联版本 ID。一个设计版本 1:1 版本文件。边界：采用预签名 URL 直传对象存储（NFR-F3）；文件永久留存不随订单完成删除（NFR-F4）；下载需鉴权（NFR-F5）。 |

### 1.5 评审与放行实体

| # | 中文领域概念 | 统一英文标识符 | MySQL 表名 | Java 类名 | TypeScript 类型名 | 业务含义与边界定义 |
|---|---|---|---|---|---|---|
| E-12 | 评审记录 | `ReviewRecord` | `review_records` | `ReviewRecord` | `ReviewRecord` | 业务负责人对设计版本的内部评审记录。含评审人 ID（`reviewer_id`）、关联版本 ID、评审结果（`review_result`：通过/不通过）、评审意见（`review_comment`）、关联物料任务 ID。边界：评审不通过时须填写退回意见，触发物料任务状态 → PENDING_REVISE。 |
| E-13 | 放行记录 | `ReleaseRecord` | `release_records` | `ReleaseRecord` | `ReleaseRecord` | 业务负责人将版本放行至客户评审的决定记录。含放行人 ID（`releaser_id`）、关联版本 ID、放行备注（`release_remark`）、放行时间戳。边界：仅状态为 PENDING_APPROVAL 的版本可放行；未放行版本不可进入客户评审（BR-08）。 |

### 1.6 客户评审与反馈实体

| # | 中文领域概念 | 统一英文标识符 | MySQL 表名 | Java 类名 | TypeScript 类型名 | 业务含义与边界定义 |
|---|---|---|---|---|---|---|
| E-14 | 客户评审 | `ClientReview` | `client_reviews` | `ClientReview` | `ClientReview` | 客户经理（AM）针对已放行版本发起的客户评审会话。含关联版本 ID、关联订单 ID、评审状态（`status`：评审中/已完成）、发起人 ID（`initiator_id`）。边界：仅已放行（RELEASED）版本可发起客户评审（BR-08）；一个版本可对应一个客户评审。 |
| E-15 | 反馈条目 | `FeedbackItem` | `feedback_items` | `FeedbackItem` | `FeedbackItem` | 客户对某版本的修改意见条目。含反馈编号（`feedback_no`，格式 `FB-NNN`）、反馈内容（`content`）、反馈类型（`feedback_type`）、反馈来源（`source`：客户/业务负责人）、关联版本 ID、关联客户评审 ID、创建人 ID、反馈状态（`status`）。边界：反馈必须关联至具体版本编号，不可脱离版本独立记录（BR-09）；设计人员可按版本维度聚合查看。 |

### 1.7 确认与交付实体

| # | 中文领域概念 | 统一英文标识符 | MySQL 表名 | Java 类名 | TypeScript 类型名 | 业务含义与边界定义 |
|---|---|---|---|---|---|---|
| E-16 | 确认记录 | `ConfirmationRecord` | `confirmation_records` | `ConfirmationRecord` | `ConfirmationRecord` | 客户确认最终版本的留痕记录。含确认人姓名（`confirmer_name`）、确认时间戳（`confirmed_at`，系统自动）、确认版本 ID、关联订单 ID。边界：确认记录一旦生成不可修改、不可删除（BR-10）；仅进入客户评审流程的版本可执行确认。 |
| E-17 | 交付归档 | `DeliveryArchive` | `delivery_archives` | `DeliveryArchive` | `DeliveryArchive` | 订单交付闭环后的归档记录。含归档操作人 ID（`archiver_id`）、归档时间戳（`archived_at`）、关联订单 ID、归档报告路径（`archive_report_path`）。边界：仅当订单下所有物料任务均为 APPROVED 状态时方可归档（BR-11）；归档后订单进入 COMPLETED 终态，不可回退（BR-12）；归档报告可导出为 PDF。 |

### 1.8 横向能力实体

| # | 中文领域概念 | 统一英文标识符 | MySQL 表名 | Java 类名 | TypeScript 类型名 | 业务含义与边界定义 |
|---|---|---|---|---|---|---|
| E-18 | 异常记录 | `ExceptionRecord` | `exception_records` | `ExceptionRecord` | `ExceptionRecord` | 业务负责人记录的订单异常及处理过程。含异常类型（`exception_type`）、异常描述（`description`）、处理记录（`resolution`）、异常状态（`status`：待确认/处理中/已关闭）、关联订单 ID、记录人 ID。边界：截止时间已过且未交付时系统自动生成异常提示（EC-C）；归档前所有异常须为"已关闭"状态。 |
| E-19 | 通知消息 | `Notification` | `notifications` | `Notification` | `Notification` | 站内提醒消息。含通知类型（`notification_type`：待办/状态变更）、目标用户 ID（`recipient_id`）、关联资源 ID（`resource_id`）、关联资源类型（`resource_type`：订单/任务/版本/反馈）、通知标题（`title`）、通知内容（`content`）、是否已读（`is_read`）。边界：仅站内推送，不触达外部渠道（微信/短信/邮件）（PRD §1.3.2）。 |
| E-20 | 操作审计日志 | `AuditLog` | `audit_logs` | `AuditLog` | `AuditLog` | 所有状态变更操作的不可篡改审计记录。含操作人 ID（`operator_id`）、操作类型（`action_type`）、目标对象类型（`target_type`）、目标对象 ID（`target_id`）、操作前状态（`before_state`）、操作后状态（`after_state`）、操作时间戳（`operated_at`）、操作结果（`result`：成功/失败）。边界：所有提交/退回/派发/放行/确认/取消操作均写入审计日志（BR-14）；日志记录不可修改、不可删除。 |

---

## 02 核心领域枚举（Domain Enums）

### 2.1 状态枚举

| # | 中文领域概念 | 统一枚举标识符 | 枚举值（全栈统一） | Java 枚举类 | TypeScript 枚举 | MySQL 存储约定 | 业务含义与边界定义 |
|---|---|---|---|---|---|---|---|
| EN-01 | 订单状态 | `OrderStatus` | `PENDING_CONFIRM` · `IN_PRODUCTION` · `COMPLETED` · `CANCELLED` | `OrderStatus` | `OrderStatus` | `VARCHAR(20)` | 整单状态机（PRD §3.2）。`PENDING_CONFIRM`：待确认，订单已提交等待 BO 审阅；`IN_PRODUCTION`：生产中，BO 已派发，设计/评审/迭代中；`COMPLETED`：已完成，客户确认 + AM 标记已交付，终态不可回退；`CANCELLED`：已取消，终态不可回退。 |
| EN-02 | 物料任务状态 | `MaterialTaskStatus` | `PENDING_DESIGN` · `PENDING_APPROVAL` · `PENDING_REVISE` · `APPROVED` · `CANCELLED` | `MaterialTaskStatus` | `MaterialTaskStatus` | `VARCHAR(20)` | 单物料任务状态机（PRD §3.3）。`PENDING_DESIGN`：待设计，任务已派发尚未上传版本；`PENDING_APPROVAL`：待内部评审，DS 已上传版本并提交评审；`PENDING_REVISE`：待修改，评审不通过或客户反馈需修改；`APPROVED`：已通过，BO 放行 + 客户确认，版本标记终稿；`CANCELLED`：已取消。PENDING_REVISE ↔ PENDING_APPROVAL 可循环。 |
| EN-03 | 版本状态 | `VersionStatus` | `DRAFT` · `PENDING_INTERNAL_REVIEW` · `RELEASED` · `IN_CLIENT_REVIEW` · `CLIENT_CONFIRMED` · `FINAL` | `VersionStatus` | `VersionStatus` | `VARCHAR(30)` | 设计版本生命周期状态。`DRAFT`：草稿，已上传未提交评审；`PENDING_INTERNAL_REVIEW`：待内部评审，已提交待 BO 审核；`RELEASED`：已放行，BO 放行可发起客户评审；`IN_CLIENT_REVIEW`：客户评审中，AM 已发起客户评审；`CLIENT_CONFIRMED`：客户已确认；`FINAL`：终稿，DS 标记为交付终稿版本。边界：仅 RELEASED 及以上状态可发起客户评审；仅 CLIENT_CONFIRMED 可标记为 FINAL。 |
| EN-04 | 反馈状态 | `FeedbackStatus` | `UNRESOLVED` · `RESOLVED` · `EXPIRED` | `FeedbackStatus` | `FeedbackStatus` | `VARCHAR(20)` | 反馈条目处理状态。`UNRESOLVED`：未解决，待设计人员处理；`RESOLVED`：已解决，设计人员已处理标记；`EXPIRED`：已过期，该版本已被后续版本替代，反馈不再需要处理。边界：EXPIRED 状态由系统自动设置（当同订单有更高版本被标记为终稿时）；不可将 EXPIRED 条目标记为 RESOLVED。 |
| EN-05 | 异常状态 | `ExceptionStatus` | `PENDING_CONFIRM` · `IN_PROGRESS` · `CLOSED` | `ExceptionStatus` | `ExceptionStatus` | `VARCHAR(20)` | 异常记录处理状态。`PENDING_CONFIRM`：待确认，系统自动生成的异常提示待 BO 确认；`IN_PROGRESS`：处理中，BO 已确认异常并记录处理过程；`CLOSED`：已关闭，异常已解决。边界：订单归档前所有异常须为 CLOSED 状态。 |

### 2.2 业务属性枚举

| # | 中文领域概念 | 统一枚举标识符 | 枚举值（全栈统一） | Java 枚举类 | TypeScript 枚举 | MySQL 存储约定 | 业务含义与边界定义 |
|---|---|---|---|---|---|---|---|
| EN-06 | 物料类型 | `MaterialType` | `POSTER` · `FLYER` · `BANNER` · `BROCHURE` · `ROLLUP` · `OTHER` | `MaterialType` | `MaterialType` | `VARCHAR(20)` | 宣传物料类型分类。`POSTER`：海报；`FLYER`：折页；`BANNER`：展架；`BROCHURE`：画册；`ROLLUP`：易拉宝；`OTHER`：其他。边界：同一物料类型的不同规格须拆分为独立物料任务（EC-B）。 |
| EN-07 | 优先级 | `Priority` | `HIGH` · `MEDIUM` · `LOW` | `Priority` | `Priority` | `VARCHAR(10)` | 任务派发时的优先级标记。`HIGH`：高优先；`MEDIUM`：中优先；`LOW`：低优先。由 BO 在派发时设定，用于看板排序与设计人员任务排序。 |
| EN-08 | 异常类型 | `ExceptionType` | `DELAY` · `CUSTOMER_COMPLAINT` · `QUALITY_ISSUE` · `STAFF_CHANGE` · `SPEC_CHANGE` | `ExceptionType` | `ExceptionType` | `VARCHAR(30)` | 异常记录分类。`DELAY`：延期；`CUSTOMER_COMPLAINT`：客户投诉；`QUALITY_ISSUE`：质量问题；`STAFF_CHANGE`：人员变更（请假/离职/超负荷）；`SPEC_CHANGE`：规格变更。边界：异常类型须在预定义枚举范围内，不可自定义。 |
| EN-09 | 反馈类型 | `FeedbackType` | `MODIFICATION_SUGGESTION` · `APPROVAL` · `REJECTION` | `FeedbackType` | `FeedbackType` | `VARCHAR(30)` | 客户反馈条目分类。`MODIFICATION_SUGGESTION`：修改建议，客户提出具体修改意见；`APPROVAL`：认可，客户认可该版本；`REJECTION`：驳回，客户明确不接受。 |
| EN-10 | 反馈来源 | `FeedbackSource` | `CLIENT` · `BUSINESS_OWNER` | `FeedbackSource` | `FeedbackSource` | `VARCHAR(20)` | 反馈条目来源角色。`CLIENT`：客户方反馈（由 AM 代为记录）；`BUSINESS_OWNER`：业务负责人反馈。用于设计人员辨识反馈来源。 |

### 2.3 评审与操作枚举

| # | 中文领域概念 | 统一枚举标识符 | 枚举值（全栈统一） | Java 枚举类 | TypeScript 枚举 | MySQL 存储约定 | 业务含义与边界定义 |
|---|---|---|---|---|---|---|---|
| EN-11 | 评审结果 | `ReviewResult` | `PASS` · `REJECT` | `ReviewResult` | `ReviewResult` | `VARCHAR(10)` | 内部评审结论。`PASS`：评审通过，可进入放行流程；`REJECT`：评审不通过，须填写退回意见，触发物料任务 → PENDING_REVISE。 |
| EN-12 | 派发类型 | `AssignmentType` | `INITIAL` · `REASSIGN` | `AssignmentType` | `AssignmentType` | `VARCHAR(10)` | 派发操作类型。`INITIAL`：初始派发，BO 首次将任务派发给 DS；`REASSIGN`：重新分派，因原被派发人请假/离职/超负荷（EC-D）等原因将任务转派给其他 DS。 |
| EN-13 | 退回原因类型 | `RejectReasonType` | `INFORMATION_INCOMPLETE` · `SPEC_UNCLEAR` · `MATERIAL_MISSING` · `OTHER` | `RejectReasonType` | `RejectReasonType` | `VARCHAR(30)` | 退回订单时的结构化原因。`INFORMATION_INCOMPLETE`：信息不完整；`SPEC_UNCLEAR`：规格不明确；`MATERIAL_MISSING`：素材缺失；`OTHER`：其他（须填写补充说明）。边界：退回时必须选择退回原因且填写补充说明（BR-03）。 |
| EN-14 | 角色类型 | `RoleType` | `AM` · `BO` · `DS` | `RoleType` | `RoleType` | `VARCHAR(5)` | 系统角色枚举。`AM`：客户经理，需求入口与客户交付出口；`BO`：业务负责人，流程中枢与质量关卡；`DS`：设计制作人员，版本生产与迭代执行。边界：角色权限严格按 PRD §2.2 RBAC 矩阵执行，不可越权。 |
| EN-15 | 通知类型 | `NotificationType` | `TODO` · `STATUS_CHANGE` | `NotificationType` | `NotificationType` | `VARCHAR(20)` | 站内通知分类。`TODO`：待办通知，有新任务进入用户待办列表；`STATUS_CHANGE`：状态变更通知，已有资源状态发生变化。 |
| EN-16 | 审计操作类型 | `AuditActionType` | `CREATE_ORDER` · `SUBMIT_ORDER` · `REJECT_ORDER` · `ASSIGN_TASK` · `REASSIGN_TASK` · `UPLOAD_VERSION` · `SUBMIT_REVIEW` · `RELEASE_VERSION` · `REJECT_VERSION` · `RECORD_FEEDBACK` · `CONFIRM_VERSION` · `MARK_FINAL` · `MARK_DELIVERED` · `ARCHIVE_ORDER` · `RECORD_EXCEPTION` · `CANCEL_ORDER` · `CANCEL_TASK` | `AuditActionType` | `AuditActionType` | `VARCHAR(30)` | 操作审计日志的操作类型枚举，覆盖全部状态变更操作。每个枚举值对应一个 PRD 中定义的业务操作动词，用于审计日志的 `action_type` 字段。 |
| EN-17 | 审计目标类型 | `AuditTargetType` | `ORDER` · `MATERIAL_TASK` · `DESIGN_VERSION` · `FEEDBACK_ITEM` · `EXCEPTION_RECORD` · `CONFIRMATION_RECORD` | `AuditTargetType` | `AuditTargetType` | `VARCHAR(30)` | 审计日志的目标对象类型分类，用于 `target_type` 字段，标识被操作的资源种类。 |

---

## 03 业务操作动词（Domain Actions）

| # | 中文操作动词 | 统一英文标识符 | Java Service 方法 | TypeScript API 函数 | REST API 路径 | 业务含义与边界定义 | 触发角色 |
|---|---|---|---|---|---|---|---|
| A-01 | 创建订单 | `createOrder` | `createOrder()` | `createOrder()` | `POST /api/v1/orders` | AM 通过标准化表单登记一笔宣传物料订单。必填字段：物料类型、规格尺寸、使用场景、交付截止日期、客户名称。创建后订单状态为 `PENDING_CONFIRM`（待确认），需求基线同步生成。 | AM |
| A-02 | 上传附件 | `uploadAttachment` | `uploadAttachment()` | `uploadAttachment()` | `POST /api/v1/orders/{orderId}/attachments` | AM 在订单提交前上传客户提供的参考文件与素材。文件直传对象存储（预签名 URL），元数据写入 `attachments` 表。允许格式与大小限制见 NFR-F1/F2。 | AM |
| A-03 | 提交订单 | `submitOrder` | `submitOrder()` | `submitOrder()` | `POST /api/v1/orders/{orderId}/submit` | AM 将已确认需求的订单提交给业务负责人审阅。前置条件：必填字段全部填写 + 至少关联 1 个附件。提交后订单状态保持 `PENDING_CONFIRM`，进入 BO 待办队列，需求基线冻结（BR-02）。 | AM |
| A-04 | 审阅订单 | `reviewOrder` | `reviewOrder()` | `reviewOrder()` | `GET /api/v1/orders/{orderId}/review` | BO 查看待派发订单的完整需求基线与附件，校验完整性后决定派发或退回。此为只读操作，不产生状态变更。 | BO |
| A-05 | 退回订单 | `rejectOrder` | `rejectOrder()` | `rejectOrder()` | `POST /api/v1/orders/{orderId}/reject` | BO 将需求不完整的订单退回 AM。必须填写退回原因（`reject_reason_type`）和补充说明（`remark`）（BR-03）。退回后订单回到 AM 待办，显示退回原因与补充说明。仅 `PENDING_CONFIRM` 状态可退回。 | BO |
| A-06 | 派发任务 | `assignTask` | `assignTask()` | `assignTask()` | `POST /api/v1/orders/{orderId}/assign` | BO 将订单派发给设计制作人员并设定截止时间与优先级。同一物料多规格须拆分为独立任务（EC-B）。派发后订单状态 → `IN_PRODUCTION`，物料任务状态 → `PENDING_DESIGN`。必须选择被派发人 + 设定截止时间（BR-04）。 | BO |
| A-07 | 重新分派 | `reassignTask` | `reassignTask()` | `reassignTask()` | `POST /api/v1/material-tasks/{taskId}/reassign` | BO 因原被派发人请假/离职/超负荷（EC-D）将任务转派给其他 DS。记录原被派发人、新被派发人、重新分派原因。原被派发人任务列表移除该任务，新被派发人任务列表新增。 | BO |
| A-08 | 上传版本 | `uploadVersion` | `uploadVersion()` | `uploadVersion()` | `POST /api/v1/material-tasks/{taskId}/versions` | DS 将设计稿上传至对应物料任务。系统自动生成递增版本号（V1/V2/V3…）（BR-05）。必须填写设计说明与变更要点（BR-06）。文件直传对象存储。仅被派发人可上传。 | DS |
| A-09 | 提交内部评审 | `submitForReview` | `submitForReview()` | `submitForReview()` | `POST /api/v1/versions/{versionId}/submit-review` | DS 将设计版本提交至内部评审。提交后版本状态 → `PENDING_INTERNAL_REVIEW`，物料任务状态 → `PENDING_APPROVAL`，进入 BO 待办。截止时间已过时标记"逾期提交"（EC-C）。 | DS |
| A-10 | 版本对比 | `compareVersions` | `compareVersions()` | `compareVersions()` | `GET /api/v1/orders/{orderId}/versions/compare?v1={id}&v2={id}` | BO 选择任意两个版本进行并排对比，展示设计说明与变更要点差异。仅至少 2 个版本时可用。此为只读操作。 | BO |
| A-11 | 放行版本 | `releaseVersion` | `releaseVersion()` | `releaseVersion()` | `POST /api/v1/versions/{versionId}/release` | BO 将内部评审通过的版本放行至客户评审。放行后版本状态 → `RELEASED`，物料任务状态 → `APPROVED`。未放行版本不可发起客户评审（BR-08）。仅 `PENDING_INTERNAL_REVIEW` 状态可放行。 | BO |
| A-12 | 退回修改 | `rejectVersion` | `rejectVersion()` | `rejectVersion()` | `POST /api/v1/versions/{versionId}/reject` | BO 评审不通过时退回版本要求 DS 迭代。须填写退回意见。退回后版本状态不变（历史留痕），物料任务状态 → `PENDING_REVISE`。仅 `PENDING_APPROVAL` 状态可退回。 | BO |
| A-13 | 发起客户评审 | `initiateClientReview` | `initiateClientReview()` | `initiateClientReview()` | `POST /api/v1/versions/{versionId}/client-review` | AM 将已放行版本分享给客户进行评审。仅 `RELEASED` 状态版本可发起。发起后版本状态 → `IN_CLIENT_REVIEW`，创建 `ClientReview` 记录绑定版本。 | AM |
| A-14 | 记录反馈 | `recordFeedback` | `recordFeedback()` | `recordFeedback()` | `POST /api/v1/versions/{versionId}/feedbacks` | AM 记录客户对某版本的修改意见。反馈内容必填，关联版本编号（BR-09）。创建 `FeedbackItem` 记录，设计人员可见。反馈状态初始为 `UNRESOLVED`。若需修改则触发物料任务 → `PENDING_REVISE`。 | AM |
| A-15 | 标记反馈已解决 | `resolveFeedback` | `resolveFeedback()` | `resolveFeedback()` | `PATCH /api/v1/feedbacks/{feedbackId}/resolve` | DS 将反馈条目状态从 `UNRESOLVED` 标记为 `RESOLVED`。不可将 `EXPIRED` 状态条目标记为已解决。 | DS |
| A-16 | 确认终稿 | `confirmVersion` | `confirmVersion()` | `confirmVersion()` | `POST /api/v1/versions/{versionId}/confirm` | AM 记录客户对最终版本的确认。记录确认人姓名、系统时间戳、版本号。确认记录不可篡改（BR-10）。版本状态 → `CLIENT_CONFIRMED`。仅进入客户评审流程的版本可确认。存在未解决反馈时系统告警但允许确认。 | AM |
| A-17 | 标记终稿 | `markFinal` | `markFinal()` | `markFinal()` | `POST /api/v1/versions/{versionId}/mark-final` | DS 在收到放行确认后将版本标记为终稿。仅 `CLIENT_CONFIRMED` 状态版本可标记。标记后版本状态 → `FINAL`，同订单其他版本不可再标记。离职人员交接后由接手人员执行（EC-D）。 | DS |
| A-18 | 标记已交付 | `markDelivered` | `markDelivered()` | `markDelivered()` | `POST /api/v1/orders/{orderId}/deliver` | AM 在交付完成后将订单标记为已交付。前置条件：客户已确认终稿（版本状态为 `CLIENT_CONFIRMED` 或 `FINAL`）。标记后订单状态 → `COMPLETED`（终态不可回退，BR-12）。 | AM |
| A-19 | 确认交付归档 | `archiveOrder` | `archiveOrder()` | `archiveOrder()` | `POST /api/v1/orders/{orderId}/archive` | BO 在客户确认后确认订单最终交付并闭环归档。前置条件：订单状态为 `COMPLETED` + 所有异常记录为 `CLOSED` 状态。归档后生成全流程时间线报告（PDF），可导出。 | BO |
| A-20 | 记录异常 | `recordException` | `recordException()` | `recordException()` | `POST /api/v1/orders/{orderId}/exceptions` | BO 在订单出现延期、客户投诉等异常时记录异常类型与处理过程。异常类型须在枚举范围内。异常状态初始为 `IN_PROGRESS`（手动记录）或 `PENDING_CONFIRM`（系统自动生成）。 | BO |
| A-21 | 关闭异常 | `closeException` | `closeException()` | `closeException()` | `PATCH /api/v1/exceptions/{exceptionId}/close` | BO 将异常记录状态从 `IN_PROGRESS` 变更为 `CLOSED`。订单归档前置条件：所有异常须为 `CLOSED` 状态。 | BO |
| A-22 | 取消订单 | `cancelOrder` | `cancelOrder()` | `cancelOrder()` | `POST /api/v1/orders/{orderId}/cancel` | 取消订单。AM 仅可取消 `PENDING_CONFIRM` 状态的草稿订单；BO 可取消 `IN_PRODUCTION` 状态的订单（须填写取消原因）。取消后订单状态 → `CANCELLED`（终态）。联动取消所有非终态物料任务。 | AM / BO |
| A-23 | 取消任务 | `cancelTask` | `cancelTask()` | `cancelTask()` | `POST /api/v1/material-tasks/{taskId}/cancel` | BO 取消单个物料任务。须填写取消原因。仅 `PENDING_DESIGN` / `PENDING_APPROVAL` / `PENDING_REVISE` 状态可取消。取消后物料任务状态 → `CANCELLED`。 | BO |
| A-24 | 查看我的订单 | `listMyOrders` | `listMyOrders()` | `listMyOrders()` | `GET /api/v1/orders/mine` | AM 查看自己创建的订单列表。显示每单当前状态与所处阶段。游标分页（NFR-P2）。数据可见性：仅自己创建的订单（BR-13）。 | AM |
| A-25 | 查看订单看板 | `viewOrderKanban` | `viewOrderKanban()` | `viewOrderKanban()` | `GET /api/v1/orders/kanban` | BO 以看板形式总览所有订单状态。按状态列展示，支持按负责人与时间筛选。加载耗时 ≤ 1.5s（NFR-P5）。数据可见性：全部订单（BR-13）。 | BO |
| A-26 | 查看版本历史 | `listVersions` | `listVersions()` | `listVersions()` | `GET /api/v1/orders/{orderId}/versions` | 查看某订单下所有设计版本的历史记录。按上传时间倒序。AM 仅自己订单，BO 全部，DS 仅自己任务。 | AM / BO / DS |
| A-27 | 查看反馈列表 | `listFeedbacks` | `listFeedbacks()` | `listFeedbacks()` | `GET /api/v1/versions/{versionId}/feedbacks` | 按版本维度查看反馈条目列表。DS 以版本聚合查看，可逐条标记已解决。AM 仅自己订单，BO 全部，DS 仅自己任务。 | AM / BO / DS |
| A-28 | 导出归档报告 | `exportArchiveReport` | `exportArchiveReport()` | `exportArchiveReport()` | `GET /api/v1/orders/{orderId}/archive-report` | BO 导出已归档订单的全流程时间线报告（PDF）。含需求登记 → 交付所有节点的时间戳、操作人、状态变更。导出失败时提供重试。 | BO |
| A-29 | 查看全流程时间线 | `viewTimeline` | `viewTimeline()` | `viewTimeline()` | `GET /api/v1/orders/{orderId}/timeline` | 查看订单全流程操作时间线。AM 仅自己订单，BO 全部，DS 仅自己任务。从审计日志聚合展示所有状态变更节点。 | AM / BO / DS |

---

## 04 术语一致性约束

### 4.1 禁止项

| 禁止行为 | 说明 |
|---|---|
| 禁止使用 `Task` 替代 `MaterialTask` | 全栈统一使用 `MaterialTask`，避免与通用"任务"概念混淆。Java 类名 `MaterialTask`，表名 `material_tasks`。 |
| 禁止使用 `Version` 替代 `DesignVersion` | 全栈统一使用 `DesignVersion`，区分设计版本与通用"版本"概念。Java 类名 `DesignVersion`，表名 `design_versions`。 |
| 禁止使用 `Order` 替代 `OrderStatus` 枚举值 | 枚举值必须使用 UPPER_SNAKE_CASE（如 `PENDING_CONFIRM`），禁止使用中文或小写。 |
| 禁止在代码中硬编码枚举值 | 枚举值须通过枚举类/类型引用，不可在业务代码中以字符串字面量出现。 |
| 禁止使用 `Client` 替代 `Customer` | 订单字段使用 `customer_name`（客户名称），不使用 `client_name`。但实体名统一为 `ClientReview`（客户评审）。 |
| 禁止使用 `Feedback` 替代 `FeedbackItem` | 全栈统一使用 `FeedbackItem`，表名 `feedback_items`。 |
| 禁止使用 `Release` 替代 `ReleaseRecord` | 全栈统一使用 `ReleaseRecord`，表名 `release_records`。 |

### 4.2 易混淆术语对照

| 易混淆中文 | 正确标识符 | 错误标识符 | 说明 |
|---|---|---|---|
| 交付截止日期 | `delivery_deadline` | `deadline` / `due_date` / `end_date` | 订单级字段，客户要求的最终交付日期。 |
| 任务截止时间 | `task_deadline` | `deadline` / `due_date` | 物料任务级字段，BO 派发时设定的设计完成截止时间。可与 `delivery_deadline` 不同。 |
| 设计说明 | `design_note` | `description` / `comment` | 版本上传时填写的设计说明字段。 |
| 变更要点 | `change_summary` | `changelog` / `diff` / `change_note` | 版本上传时填写的本版本与上版本的差异要点。 |
| 退回原因 | `reject_reason_type` | `reject_reason` / `return_reason` | 退回订单时的结构化原因枚举字段。 |
| 补充说明 | `remark` | `note` / `comment` / `description` | 退回/派发/异常等操作时填写的自由文本说明字段。统一使用 `remark`。 |
| 确认人 | `confirmer_name` | `confirmer` / `confirmed_by` / `approver` | 客户确认记录中记录的确认人姓名字段。 |
| 被派发人 | `assignee_id` | `assigned_to` / `designer_id` | 物料任务/派发记录中被分派设计人员的用户 ID 字段。 |
| 订单编号 | `order_no` | `order_id` / `order_number` / `order_code` | 订单业务编号（`ORD-YYYYMMDD-NNN` 格式），与数据库主键 `id` 区分。 |
| 版本号 | `version_no` | `version` / `version_number` / `version_code` | 设计版本业务编号（V1/V2/V3…），与数据库主键 `id` 区分。 |
| 反馈编号 | `feedback_no` | `feedback_id` / `feedback_number` | 反馈条目业务编号（`FB-NNN` 格式），与数据库主键 `id` 区分。 |
| 终稿 | `is_final` | `is_terminal` / `is_completed` / `is_delivered` | 版本是否为终稿的布尔标识字段。 |
| 逾期提交 | `is_overdue` | `is_late` / `is_delayed` | 版本提交时是否超过任务截止时间的布尔标识字段。 |

---

## 05 跨文档术语追溯矩阵

| 术语 | PRD 章节 | 用户故事编号 | 验收标准编号 |
|---|---|---|---|
| `Order` | §3.2、§4.2-M1 | AM-01、AM-03、AM-04、AM-08 | AC-01.1、AC-03.1、AC-04.1、AC-08.1 |
| `MaterialTask` | §3.3、§4.2-M2 | BO-03、DS-01、DS-02 | AC-03.1(BO)、AC-01.1(DS)、AC-02.1(DS) |
| `DesignVersion` | §4.2-M3 | DS-02、DS-03、DS-04、DS-06 | AC-02.1(DS)、AC-03.1(DS)、AC-04.1(DS)、AC-06.1(DS) |
| `RequirementBaseline` | §4.2-M1、BR-02 | AM-01、BO-01、DS-01 | AC-01.1、AC-01.1(BO)、AC-01.1(DS) |
| `Attachment` | §4.2-M1 | AM-02 | AC-02.1 |
| `Assignment` | §4.2-M2 | BO-03 | AC-03.1(BO)、AC-03.4(BO) |
| `ReviewRecord` | §4.2-M4 | BO-05 | AC-05.1(BO) |
| `ReleaseRecord` | §4.2-M4、BR-08 | BO-06、DS-06 | AC-06.1(BO)、AC-06.1(DS) |
| `ClientReview` | §4.2-M5 | AM-05 | AC-05.1 |
| `FeedbackItem` | §4.2-M5、BR-09 | AM-06、DS-05 | AC-06.1、AC-05.1(DS) |
| `ConfirmationRecord` | §4.2-M6、BR-10 | AM-07 | AC-07.1 |
| `DeliveryArchive` | §4.2-M6、BR-11 | AM-08、BO-08 | AC-08.1、AC-08.1(BO) |
| `ExceptionRecord` | §4.2-M8 | BO-07 | AC-07.1(BO)、AC-07.2(BO) |
| `Notification` | §4.2-M9、BR-15 | 横向能力 | — |
| `AuditLog` | §2.3、BR-14 | 横向能力 | — |
| `OrderStatus` | §3.2.1 | 全流程 | 全部 Happy Path |
| `MaterialTaskStatus` | §3.3.1 | 全流程 | 全部 Happy Path |
| `VersionStatus` | §4.2-M3、§4.2-M4 | DS-02、DS-04、BO-06、DS-06 | AC-02.1(DS)、AC-04.1(DS)、AC-06.1(BO)、AC-06.1(DS) |
| `MaterialType` | §4.2-M1 | AM-01 | AC-01.1 |
| `Priority` | §4.2-M2 | BO-03 | AC-03.1(BO) |
| `ExceptionType` | §4.2-M8 | BO-07 | AC-07.1(BO) |

---

> 本 GLOSSARY 为全栈命名唯一权威来源。MySQL 建表、Java 实体类、TypeScript 类型定义、Vue 3 组件命名必须与本文档保持一致。任何新增术语须经评审后追加至本文档，禁止在代码中引入未收录于本文档的领域概念。
