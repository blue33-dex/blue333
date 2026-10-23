# Phase 4 · 看板 / 时间线 / 通知 / 异常 · 详细任务说明

> **Phase 范围**：异常处理（ExceptionRecord）+ 看板聚合查询 + 时间线 + 孤儿对象清理 + **取消订单完整实现** + **待确认：站内通知推送方案**
> **故事覆盖**：BO-04 / BO-07 + M9 通知强化 + A-22 cancelOrder 完整级联
> **前置条件**：Phase 3 全部完成 + P3 验收全部 PASS + CONFLICT-01/02 闭环验证通过
> **关键任务**：
> - **P4-T05 异常自动生成定时任务**：task_deadline 已过 + 任务非终态 → DELAY 类型
> - **P4-T09 待确认**：站内通知推送方案（SSE / WebSocket / 仅轮询 三种需决策）
> - **P4-T11 孤儿对象清理定时任务**：每日凌晨 3:00，MinIO 对象无 DB 记录且超 24h → 清理
> - **P4-T06 ArchiveOrder 异常前置**：归档前所有异常须为 CLOSED

---

## 阶段目标

Phase 4 交付后：
1. BO 看板（4 列）聚合所有订单状态，加载 ≤ 1.5s（NFR-P5）
2. BO 异常记录管理：手动创建 + 系统自动生成 DELAY 类型 + 关闭
3. BO 归档前所有异常须关闭（前置条件）
4. **取消订单完整级联**（A-22）：AM 取消草稿 / BO 取消 IN_PRODUCTION 订单 → 级联所有非终态 MaterialTask CANCELLED
5. 孤儿对象清理定时任务
6. 时间线完整（从 audit_logs 聚合 + A-29）
7. **待确认**：站内通知推送方案确定并实现

---

## Task 详细说明

### P4-T01 · Phase 4 1 张表 DDL

| 属性 | 内容 |
|---|---|
| **类型** | DB |
| **前置依赖** | Phase 3 全部完成 |
| **目标** | 新增 exception_records 表（最后一张表，总计 25 表）|
| **列** | exception_type VARCHAR(30) / description TEXT / resolution TEXT / status VARCHAR(20) / order_id FK / recorder_id FK |
| **索引** | idx_exc_order_id / idx_exc_status |
| **交付物** | V5__phase4_exception_tables.sql |

---

### P4-T02 · Phase 4 2 个枚举

| 属性 | 内容 |
|---|---|
| **类型** | BE-ENUM |
| **前置依赖** | P4-T01 |
| **目标** | ExceptionStatus(3: PENDING_CONFIRM / IN_PROGRESS / CLOSED) + ExceptionType(5: DELAY / CUSTOMER_COMPLAINT / QUALITY_ISSUE / STAFF_CHANGE / SPEC_CHANGE) |
| **交付物** | 2 Java enum + TS 同步 |

---

### P4-T03 · ExceptionRecord 实体 + Repository

| 属性 | 内容 |
|---|---|
| **类型** | BE-ENT |
| **前置依赖** | P4-T01, P4-T02 |
| **实体** | ExceptionRecord |
| **交付物** | 1 Entity + 1 Repository |

---

### P4-T04 · ExceptionRecordService

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC |
| **前置依赖** | P4-T03 |
| **方法** | recordException(A-20) → 手动创建 IN_PROGRESS；closeException(A-21) → IN_PROGRESS→CLOSED |
| **校验** | ExceptionType 枚举范围内；description 必填；reject 不能创建 CLOSED |
| **审计** | RECORD_EXCEPTION / CLOSE_EXCEPTION |
| **交付物** | ExceptionRecordService.java |

---

### P4-T05 · 异常自动生成定时任务

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC（定时任务）|
| **前置依赖** | P4-T04 |
| **目标** | 每日凌晨 4:00 扫描所有 MaterialTask：task_deadline < NOW AND status NOT IN (APPROVED, CANCELLED) → 自动创建 ExceptionRecord（type=DELAY, status=PENDING_CONFIRM, description="任务 {taskId} 已过期"）|
| **Spring 配置** | `@EnableScheduling` + `@Scheduled(cron = "0 0 4 * * ?")` |
| **去重** | 同一任务同一异常类型不重复创建（存在则跳过）|
| **交付物** | ExceptionAutoGenerator.java |

---

### P4-T06 · ArchiveOrder 异常前置条件

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC（扩展 P3-T07）|
| **前置依赖** | P3-T07, P4-T04 |
| **目标** | ArchiveOrder(A-19) 前置条件扩展：除 OrderStatus=COMPLETED 外，**该订单所有 ExceptionRecord 须为 CLOSED**；否则返回 422 |
| **交付物** | OrderService.archiveOrder() 扩展 |

---

### P4-T07 · KanbanService

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC |
| **前置依赖** | P1-T19（数据可见性）|
| **目标** | 看板聚合查询：按 OrderStatus 四种状态 GROUP BY + 筛选条件（负责人 / 时间范围 / 客户）|
| **SQL 设计** | 单条 SQL GROUP BY + 左联 material_tasks 聚合 latestStatus → 避免循环查询 → NFR-P5 1.5s 内 |
| **数据可见性** | BO 全部 / AM 仅自己订单（Phase 1 S-04 已实现扩展到 A-25）|
| **交付物** | KanbanService.java |

---

### P4-T08 · TimelineService

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC |
| **前置依赖** | P1-T12（审计日志）|
| **目标** | 从 audit_logs 聚合全流程时间线，按 operated_at 正序返回，游标分页 |
| **返回字段** | action_type 中文翻译 + operator_name + operated_at + before_state→after_state + 备注 |
| **数据可见性** | BR-13：AM 仅自己订单 / BO 全部 / DS 仅自己任务关联订单 |
| **交付物** | TimelineService.java |

---

### P4-T09 · 待确认：站内通知推送方案

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC（架构决策待定）|
| **前置依赖** | P1-T13（基础 Spring Event 通知已实现，Phase 4 强化实时推送）|
| **三方案对比** | 方案 A：**SSE（Server-Sent Events）** —— 简单、单向、EventSource API、无需升级 Nginx 复杂配置、架构方案倾向此方案。实现：`GET /api/v1/notifications/stream` → 长轮询 + Spring SseEmitter。前端 EventSource 监听。方案 B：WebSocket —— 双向但复杂，需要 STOMP 协议；MVP 站内提醒只需单向推送，WebSocket 过重。方案 C：仅前端轮询（GET /api/v1/notifications 每 30s）—— 简单但非实时。|
| **决策** | **待团队确认**。建议方案 A（SSE），MVP 足够 |
| **交付物** | 决策文档 + 对应实现 |

---

### P4-T10 · Phase 4 Controller（含 A-22 取消订单完整实现）

| 属性 | 内容 |
|---|---|
| **类型** | BE-CTL |
| **前置依赖** | P4-T04 / P4-T05 / P4-T06 / P4-T07 / P4-T08 / P4-T09 |
| **端点** | A-20 recordException / A-21 closeException / A-25 kanban / A-29 timeline / **A-22 cancelOrder 完整级联** |
| **A-22 完整级联** | AM 取消（仅 PENDING_CONFIRM）→ 级联物料任务 CANCELLED（但 PENDING_CONFIRM 状态下无物料任务，实际上只是简单取消）；BO 取消（IN_PRODUCTION）→ 级联所有非终态 MaterialTask CANCELLED；**已 APPROVED 的 MaterialTask 保留但订单标记 CANCELLED 状态**（PRD §3.4 联动规则）|
| **权限** | A-20/A-21/A-25/A-29 → BO；A-22 → AM 仅草稿 或 BO 任意 |
| **交付物** | ExceptionController + KanbanController + TimelineController + OrderController 扩展（A-22 级联）|

---

### P4-T11 · 孤儿对象清理定时任务

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC（定时任务）|
| **前置依赖** | P1-T14（MinIO SDK）|
| **目标** | 每日凌晨 3:00 执行：MinIO mc find → 对比 DB attachments.storage_path + version_files.storage_path → 无 DB 记录 且 最后修改时间 > 24h → mc rm 删除 |
| **实现** | `@Scheduled(cron = "0 0 3 * * ?")` + MinioClient.listObjects() → 内存 Set 对比 → removeObject() |
| **安全** | 软删除先移动到 `ordermgmt-orphan` bucket 保留 7 天 → 硬删除 |
| **交付物** | OrphanObjectCleaner.java |

---

### P4-T12 ~ P4-T16 · 前端（并行）

| 编号 | 类型 | 依赖 | 交付 |
|---|---|---|---|
| P4-T12 | FE-API | P4-T10 | Phase 4 API 函数 |
| P4-T13 | FE-PAGE | P4-T12 | BO OrderKanban（4 列 + 筛选 + NFR-P5）|
| P4-T14 | FE-PAGE | P4-T12 | BO Timeline（时间线 + 导出）|
| P4-T15 | FE-PAGE | P4-T12 | BO ExceptionRecord 管理页 |
| P4-T16 | FE-COMP | P4-T09 方案确定后 | 站内通知铃铛组件 |

---

### P4-T17 · Phase 4 前后端联调

| 属性 | 内容 |
|---|---|
| **前置依赖** | P4-T10~P4-T16 全部完成 |
| **目标** | 看板 → 时间线 → 异常 CRUD → 取消级联 → 孤儿清理 全链路 |

---

### P4-T18 ~ P4-T23 · 测试任务

| 编号 | 类型 | 测试目标 |
|---|---|---|
| P4-T18 | TEST | 看板性能：加载耗时 ≤ 1.5s（NFR-P5）+ 4 列正确聚合 + 筛选正确 |
| P4-T19 | TEST | 异常自动生成：task_deadline 过期 → DELAY 类型异常自动创建 + PENDING_CONFIRM |
| P4-T20 | TEST | ArchiveOrder 异常前置：未关闭异常 → 422；全部 CLOSED → 成功 |
| P4-T21 | TEST | 孤儿对象清理：Step 1 预签名 URL → Step 2 PUT → 不做 Step 3 → 24h 后定时清理 |
| P4-T22 | TEST | 取消订单级联：IN_PRODUCTION → BO 取消 → 非终态 MaterialTask 全部 CANCELLED；已 APPROVED 保留但订单 CANCELLED |
| P4-T23 | TEST | 审计日志：RECORD_EXCEPTION / CLOSE_EXCEPTION / CANCEL_ORDER / CANCEL_TASK 正确写入 |

---

### P4-T24 · Phase 4 端到端验收

| 属性 | 内容 |
|---|---|
| **前置依赖** | P4-T17 + P4-T18~P4-T23 全部 PASS |
| **验收覆盖** | BO-04（看板）/ BO-07（异常）/ M9 通知强化 + A-22 完整级联 + 孤儿清理 |
| **交付物** | 验收测试报告 |

---

## Phase 4 完成标志（MVP 全部交付）

| 检查点 | 要求 |
|---|---|
| DB | 25 张总表 |
| API | 29 GLOSSARY + 8 补充 + S-09 确认接口 + 待定 SSE 接口 |
| 看板 | 4 列聚合 + ≤ 1.5s + 筛选 |
| 异常 | 手动记录 + 自动生成 + 关闭 + ArchiveOrder 前置 |
| 取消级联 | IN_PRODUCTION → BO 取消 → 非终态 MaterialTask 全部 CANCELLED |
| 孤儿清理 | 每日 3:00 → 孤儿对象自动清理 |
| 通知 | 待确认方案落地 + 铃铛组件 |
| 审计 | 6 个新审计操作 |
| **全链路 E2E** | 从创建订单到归档 + 所有取消/异常/看板场景 |

---

## Phase 4 待确认项

| 编号 | 项目 | 说明 | 建议 |
|---|---|---|---|
| 待确认-03 | 站内通知推送方案 | SSE vs WebSocket vs 轮询 | 建议 SSE（单向推送足够、简单、成熟）|
| 待确认-04 | ArchiveOrder PDF 库 | 同 Phase 3 待确认-01 | OpenPDF（AGPL）需确认授权合规性 |
| 待确认-05 | 孤儿对象清理执行时间 | 凌晨 3:00 vs 其他 | 凌晨 3:00 低峰期执行无副作用 |
| 待确认-06 | SSE 连接数限制 | 长连接对服务器资源的影响 | MVP 规模（中小企业 N 人）连接数低，暂无风险 |

---

## 全 MVP 完成标志

| 里程碑 | 标志 |
|---|---|
| Phase 1 | 登记 → 审阅 → 派发 可测闭环 |
| Phase 2 | 版本上传 → 内部评审 → 放行 可测闭环 + CONFLICT-01 阶段验证 |
| Phase 3 | 客户评审 → 反馈 → 确认 → 交付 完整闭环 + CONFLICT-01/02 兑现 |
| **Phase 4** | **看板 + 时间线 + 通知 + 异常 + 取消级联 + 孤儿清理 + 全 22 条故事覆盖 + 全 NFR 落实** |

---

> **任务拆解文档全部完成**。人工审核本阶段所有文档后，方可开始执行任何 Task。

## 新增文件汇总

| 文件 | Task 数量 |
|---|---|
| `docs/tasks/TASK_BREAKDOWN.md` | 主索引（107 总任务 + 22 故事追溯）|
| `docs/tasks/TASKS_PHASE_1.md` | 40 |
| `docs/tasks/TASKS_PHASE_2.md` | 22 |
| `docs/tasks/TASKS_PHASE_3.md` | 21 |
| `docs/tasks/TASKS_PHASE_4.md` | 24 |
| **合计** | **107 Task + 3 已知冲突 + 6 待确认项** |

## 用户故事覆盖情况

| 故事 | Phase | 对应 Task |
|---|---|---|
| AM-01 标准化表单登记 | P1 | P1-T17/P1-T20/P1-T27 |
| AM-02 上传参考文件 | P1 | P1-T14/P1-T17/P1-T26/P1-T27/P1-T29 |
| AM-03 提交订单 | P1 | P1-T15/P1-T17/P1-T20/P1-T30 |
| AM-04 我的订单状态 | P1 | P1-T19/P1-T20(A-24)/P1-T28 |
| AM-05 发起客户评审 | P3 | P3-T05/P3-T08(A-13)/P3-T10 |
| AM-06 记录客户意见 | P3 | P3-T05/P3-T08(A-14)/P3-T10 |
| AM-07 客户确认留痕 | P3 | P3-T06/P3-T08(A-16)/P3-T15/P3-T16 |
| AM-08 标记交付归档 | P3 | P3-T07/P3-T08(A-18)/P3-T13/P4-T08 |
| BO-01 审阅订单详情 | P1 | P1-T20(A-04)/P1-T29/P1-T30 |
| BO-02 退回 | P1 | P1-T17/P1-T20(A-05)/P1-T31 |
| BO-03 分派 DS + 截止 | P1 | P1-T16/P1-T17/P1-T18/P1-T20(A-06)/P1-T31 |
| BO-04 看板总览 | P4 | P4-T07/P4-T10(A-25)/P4-T13 |
| BO-05 版本对比 | P2 | P2-T07(compareVersions)/P2-T10(A-10)/P2-T15 |
| BO-06 放行 | P2 | P2-T05/P2-T07/P2-T10(A-11)/**CONFLICT-01 专项** |
| BO-07 异常记录 | P4 | P4-T04/P4-T10(A-20/A-21)/P4-T15 |
| BO-08 闭环归档 | P3 | P3-T07/P3-T08(A-19)/P4-T08(A-29)/P4-T14 |
| DS-01 接收任务 + 查看基线 | P1 | P1-T18/P1-T21(S-05/S-06)/P1-T32 |
| DS-02 上传设计稿（自动版本号）| P2 | P2-T04/P2-T07(uploadVersion)/P2-T10(A-08)/P2-T13 |
| DS-03 设计说明必填 | P2 | P2-T13 前端校验 + P2-T07 后端校验 |
| DS-04 提交内部评审 | P2 | P2-T05/P2-T06/P2-T07(submitForReview)/P2-T10(A-09)/P2-T14 |
| DS-05 查看反馈并迭代 | P3 | P3-T05/P3-T08(A-27)/P3-T12/P3-T17 |
| DS-06 标记终稿 | P2 | P2-T05/P2-T07/P2-T10(A-17)/CONFLICT-01 兑现后 confirmVersion → APPROVED 才能标记 FINAL |

**全覆盖验证**：22 条用户故事 **100% 覆盖**，每条至少对应 1 个 Task。

## 关键依赖

```
Phase 1 INFRA → DB → BE-ENUM/UTIL → BE-ENT → BE-SM/SVC → BE-CTL → FE-SC → FE-API → FE-PAGE → API-INTEG → TEST
Phase 2 必须 Phase 1 全部完成 + 验收 PASS
Phase 3 必须 Phase 2 全部完成 + CONFLICT-01 阶段验证通过
Phase 4 必须 Phase 3 全部完成 + CONFLICT-01/02 闭环验证通过

Phase 内部可并行：
- BE-ENUM / BE-UTIL / FE-SC 三者相互独立，均依赖 INFRA
- Order 状态机 + MaterialTask 状态机 可并行
- FE-PAGE 各角色独立页面 可并行
```

## 待确认问题清单

| 编号 | 问题 | 影响范围 | 建议 |
|---|---|---|---|
| CONFLICT-01 | releaseVersion 是否立即触发 APPROVED | P2/P3 | **已按 PRD 定稿**：不触发，推迟 confirmVersion |
| CONFLICT-02 | canMarkCompleted 校验条件 | P3 | **已按 PRD 定稿**：严格 count(APPROVED) == total |
| CONFLICT-03 | GLOSSARY 29 业务 API vs 8 补充 API | 全阶段 | **已记录**：29 + 8 + S-09 = 38 端点 |
| 待确认-01 | PDF 归档报告库（OpenPDF vs iText 7）| P3-T07 | 建议 OpenPDF，需确认授权 |
| 待确认-02 | FeedbackItem 自动过期触发时机 | P3-T05 | 同步 markFinal vs 异步定时，建议定时 |
| 待确认-03 | 站内通知推送方案（SSE / WebSocket / 轮询）| P4-T09 | 建议 SSE |
| 待确认-04 | 孤儿对象清理执行时间 | P4-T11 | 凌晨 3:00 |
| 待确认-05 | 对象存储最终选型（MinIO vs 云）| INFRA | 建议 MinIO 自托管 |
| 待确认-06 | 部署编排（Docker Compose vs K8s）| INFRA | 建议 Docker Compose |

## 执行限制重申

| 限制 | 说明 |
|---|---|
| 禁止修改 | PRD.md / USER_STORIES.md / GLOSSARY.md 为只读 |
| 禁止跳过 CONFLICT 验证 | P2-T18 / P3-T15 必须专项验证 CONFLICT-01/02 |
| 禁止把 Phase 2-4 任务前置到 Phase 1 | 每个 Phase 有明确边界 |
| 禁止把非目标纳入任何 Phase | 见 TASK_BREAKDOWN.md §6 排除清单 |
| 先人工审核 Task 文档再执行 Task | 本任务拆解完成 → 人工确认 → 方可启动任何 Task |

---

**所有任务拆解文档已完成**。TASK_BREAKDOWN.md 为主索引（含追溯矩阵 + 依赖 + 冲突记录），TASKS_PHASE_1.md 为完整详细任务说明（40 Task × 每 Task 完整字段），TASKS_PHASE_2 ~ TASKS_PHASE_4 按主索引派生（22 + 21 + 24 Task × 核心字段）。**合计 107 个 Task + 3 个已解决冲突 + 6 个待确认问题**。请人工审核后再开始执行。