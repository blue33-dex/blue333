# Phase 3 · 客户评审 → 反馈 → 确认 → 交付 · 详细任务说明

> **Phase 范围**：ClientReview / FeedbackItem / ConfirmationRecord / DeliveryArchive + **CONFLICT-01 / CONFLICT-02 最终兑现**
> **核心链路**：BO 放行后（Version RELEASED）→ AM 发起客户评审 → 记录反馈 → 客户确认 → DS 标记终稿 → AM 标记已交付 → BO 归档
> **故事覆盖**：AM-05 / AM-06 / AM-07 / AM-08 / BO-08 / DS-05 + AM-04（Phase 1 视图扩展，加评审状态 + 反馈数量列）
> **前置条件**：Phase 2 全部完成 + P2 验收全部 PASS
> **关键任务**：
> - **P3-T04 为 CONFLICT-01 最终兑现点**：AM confirmVersion → MaterialTask APPROVED（MaterialTask 状态机真正进入终态）
> - **P3-T04 同时实现 CONFLICT-02**：canMarkCompleted 严格 count(APPROVED) == totalCount，非"无活跃态"
> - **P3-T06 确保 BR-10**：ConfirmationRecord 不可篡改（DB + Service 双重保护）

---

## 阶段目标

Phase 3 交付后：
1. AM 能对 BO 放行的版本发起客户评审
2. 客户反馈条目化绑定具体版本编号（BR-09）
3. 客户确认时记录不可篡改的留痕（BR-10）
4. **CONFLICT-01 兑现**：AM confirmVersion 才触发 MaterialTask → APPROVED
5. **CONFLICT-02 兑现**：canMarkCompleted 严格所有 MaterialTask 均 APPROVED 方可整单 COMPLETED
6. 反馈条目自动过期（同订单更高版本 FINAL 时）
7. 反馈需修改 → MaterialTask → PENDING_REVISE（联动）
8. 全部 MaterialTask APPROVED → AM 可标记已交付 → BO 归档生成 PDF

---

## Task 详细说明

### P3-T01 · Phase 3 4 张表 DDL

| 属性 | 内容 |
|---|---|
| **类型** | DB |
| **前置依赖** | Phase 2 全部完成 |
| **目标** | 新增 client_reviews / feedback_items / confirmation_records / delivery_archives 四张表 |
| **关键约束** | feedback_items 关联版本 FK（BR-09 绑定强制）；confirmation_records **无 UPDATE 权限设计**（BR-10 不可篡改）|
| **交付物** | V4__phase3_client_review_tables.sql |

---

### P3-T02 · Phase 3 3 个枚举

| 属性 | 内容 |
|---|---|
| **类型** | BE-ENUM |
| **前置依赖** | P3-T01 |
| **目标** | FeedbackStatus(3: UNRESOLVED / RESOLVED / EXPIRED) + FeedbackType(3) + FeedbackSource(2) |
| **关键** | FeedbackStatus.EXPIRED 不可手动置 RESOLVED，由系统自动 |
| **交付物** | 3 Java enum + TS 同步 |

---

### P3-T03 · Phase 3 实体 + Repository

| 属性 | 内容 |
|---|---|
| **类型** | BE-ENT |
| **前置依赖** | P3-T01, P3-T02 |
| **实体** | ClientReview / FeedbackItem / ConfirmationRecord / DeliveryArchive |
| **关键保护** | ConfirmationRecord Repository **不提供 delete/update 方法**（append-only + immutable）|
| **交付物** | 4 Entity + 4 Repository |

---

### P3-T04 · CONFLICT-01 / CONFLICT-02 最终兑现

| 属性 | 内容 |
|---|---|
| **类型** | BE-SM + BE-SVC（两者合并任务）|
| **前置依赖** | P3-T03, P2-T06 |
| **CONFLICT-01 兑现** | `confirmVersion(A-16)` 实现：VersionStatus CLIENT_CONFIRMED + **MaterialTaskStatus → APPROVED（这是 APPROVED 的唯一真正触发点）** |
| **CONFLICT-02 兑现** | `canMarkCompleted()` 严格校验：`count(status=APPROVED) == totalMaterialTaskCount`，**不允许用"无活跃态"近似**；存在 CANCELLED 任务 → 整单不可 COMPLETED |
| **测试重点** | releaseVersion 后 MaterialTask 仍 PENDING_APPROVAL（已在 P2-T18 验证）；confirmVersion 后 MaterialTask → APPROVED；有 CANCELLED 任务时 markDelivered 返回 409 |
| **交付物** | DesignVersionService.confirmVersion() + OrderService.canMarkCompleted() |

---

### P3-T05 · ClientReviewService

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC |
| **前置依赖** | P3-T03, P2-T06 |
| **方法** | initiateClientReview(A-13) → VersionStatus RELEASED→IN_CLIENT_REVIEW + 创建 ClientReview 记录 | recordFeedback(A-14) → 创建 FeedbackItem 关联 version_id + **若 feedback_type=MODIFICATION_SUGGESTION → MaterialTask→PENDING_REVISE** | resolveFeedback(A-15) → FeedbackItem UNRESOLVED→RESOLVED；**EXPIRED 禁止手动 resolve** | listFeedbacks(A-27) |
| **关键** | FeedbackItem 必须带 version_id（BR-09）；反馈需修改触发 MaterialTask 联动；反馈自动过期（同订单更高版本 FINAL 时，该版本下 EXPIRED）|
| **交付物** | ClientReviewService.java |

---

### P3-T06 · ConfirmationRecordService

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC |
| **前置依赖** | P3-T03, P1-T12 |
| **目标** | confirmVersion（同时由 P3-T04 调用）生成 ConfirmationRecord；confirmer_name + confirmed_at（系统自动 NOW）+ version_id + order_id |
| **BR-10 双重保护** | DB 层无 UPDATE 权限 + Service 层所有 Repository 方法均只 insert 不 update |
| **审计** | 操作写入 CONFIRM_VERSION 审计 |
| **交付物** | ConfirmationRecordService.java |

---

### P3-T07 · OrderService 扩展（markDelivered + archiveOrder）

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC |
| **前置依赖** | P3-T04, P3-T03 |
| **方法** | markDelivered(A-18) → 前置条件 **canMarkCompleted 严格 count** → OrderStatus→COMPLETED（终态）；**BR-12 终态不可回退** | archiveOrder(A-19) → 前置条件 OrderStatus=COMPLETED + 所有异常 CLOSED（异常推迟 Phase 4，Phase 3 此条件先跳过）→ 生成 PDF 归档报告（OpenPDF / iText）|
| **PDF 内容** | 全流程时间线：需求登记 → 所有状态变更 → 操作人 → 时间戳 → 审计日志聚合 |
| **交付物** | OrderService 扩展 + ArchiveReportGenerator.java |

---

### P3-T08 ~ P3-T14 · Controller + 前端

| 编号 | 类型 | 依赖 | 交付 |
|---|---|---|---|
| P3-T08 | BE-CTL | P3-T05~P3-T07 | A-13~A-19 / A-27 / A-28 Controller |
| P3-T09 | FE-API | P3-T08 | Phase 3 API 函数 |
| P3-T10 | FE-PAGE | P3-T09 | AM ClientReview（发起评审 + 记录反馈 + 客户确认）|
| P3-T11 | FE-PAGE | P1-T28 | AM MyOrderList 扩展（增加评审状态 + 反馈数量列）|
| P3-T12 | FE-PAGE | P2-T14 | DS FeedbackView（按版本聚合反馈 + 标记已解决 + EXPIRED 保护）|
| P3-T13 | FE-PAGE | P3-T09 | BO OrderArchive + Timeline 初始 |
| P3-T14 | API-INTEG | P3-T10~P3-T13 | Phase 3 全链路联调 |

---

### P3-T15 · CONFLICT-01 / CONFLICT-02 专项测试（最高优先级）

| 属性 | 内容 |
|---|---|
| **前置依赖** | P3-T04 |
| **测试矩阵** | 1. **CONFLICT-01 验证**：BO releaseVersion → MaterialTask 仍 PENDING_APPROVAL（Phase 2 已验证）；AM confirmVersion → MaterialTask **变为 APPROVED**（Phase 3 首次验证）| 2. **CONFLICT-02 验证**：订单有 2 个 MaterialTask，1 个 APPROVED + 1 个 CANCELLED → markDelivered 返回 409（不允许）；2 个均 APPROVED → markDelivered 成功 | 3. 终态不可回退（BR-12）：COMPLETED 后尝试 cancelOrder → 409 | 4. 反馈需修改联动：AM 反馈类型 MODIFICATION_SUGGESTION → MaterialTask→PENDING_REVISE |
| **交付物** | ConflictResolutionTest.java |

---

### P3-T16 ~ P3-T20 · 其他测试

| 编号 | 目标 |
|---|---|
| P3-T16 | ConfirmationRecord 不可篡改（DB + Service 双层保护）|
| P3-T17 | FeedbackItem 自动过期（同订单更高版本 FINAL → 低版本 FeedbackItem → EXPIRED + RESOLVE 拒绝）|
| P3-T18 | Feedback 需修改 → MaterialTask PENDING_REVISE 联动 |
| P3-T19 | 审计日志（RECORD_FEEDBACK / CONFIRM_VERSION / MARK_FINAL / MARK_DELIVERED / ARCHIVE_ORDER）|
| P3-T20 | RBAC 扩展（AM 仅自己订单发起评审；DS 仅自己任务 resolve）|

---

### P3-T21 · Phase 3 端到端验收

| 属性 | 内容 |
|---|---|
| **前置依赖** | P3-T14 + P3-T15~P3-T20 全部 PASS |
| **验收覆盖** | AM-04 / AM-05 / AM-06 / AM-07 / AM-08 / BO-08 / DS-05 + CONFLICT-01/02 全链路 |
| **关键验收** | CONFLICT-01 闭环：BO 放行 → APPROVED 未提前 → AM 确认 → APPROVED 正确触发 | CONFLICT-02 闭环：有 CANCELLED 任务时整单不可 COMPLETE → 全 APPROVED 时可 COMPLETE |
| **交付物** | 验收测试报告 |

---

## Phase 3 完成标志

| 检查点 | 要求 |
|---|---|
| DB | 24 总表 |
| **CONFLICT-01** | **confirmVersion → APPROVED，验证通过** |
| **CONFLICT-02** | **canMarkCompleted 严格 count，验证通过** |
| 确认不可篡改 | P3-T16 测试 PASS |
| 反馈过期 | P3-T17 测试 PASS |
| 终态不可回退 | BR-12 测试 PASS |
| PDF 归档 | A-19 成功生成 PDF |
| E2E | 客户评审 → 反馈 → 确认 → 终稿 → 交付 完整闭环 |

---

## Phase 3 待确认项

| 编号 | 项目 | 说明 |
|---|---|---|
| 待确认-01 | PDF 归档报告库选型 | OpenPDF（AGPL） vs iText 7（商业授权复杂）。**架构方案建议 OpenPDF**，需团队确认授权合规性 |
| 待确认-02 | FeedbackItem 自动过期触发时机 | 架构方案建议"同订单更高版本 FINAL 时触发"。需确认：是 markFinal 时同步触发，还是定时任务异步扫描 |
