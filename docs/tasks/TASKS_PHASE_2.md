# Phase 2 · 版本上传 → 内部评审 → 放行 · 详细任务说明

> **Phase 范围**：DesignVersion + VersionFile 全生命周期 + BO 内部评审放行 + DS 终稿标记
> **核心链路**：DS 上传版本 → DS 提交内部评审 → BO 评审 → BO 放行/退回 → DS 标记终稿
> **故事覆盖**：DS-01（完整）/ DS-02 / DS-03 / DS-04 / BO-05 / BO-06 / DS-06 / A-07 / A-23
> **前置条件**：Phase 1 全部完成 + P1-01 ~ P1-10 验收全部 PASS
> **关键注意**：**CONFLICT-01 必须在此 Phase 解决**——BO releaseVersion 仅触发 VersionStatus→RELEASED，不触发 MaterialTask→APPROVED；APPROVED 推迟到 Phase 3 confirmVersion

---

## 阶段目标

Phase 2 交付后：
1. DS 能上传设计稿 → 自动 V1/V2/V3 编号 + 设计说明必填 → 提交内部评审
2. BO 能看到版本历史 + 并排对比 → 放行或退回（退回须填意见）
3. BO 放行后版本状态 RELEASED，但 **MaterialTask 仍为 PENDING_APPROVAL**（CONFLICT-01 兑现点，APPROVED 推迟到 Phase 3）
4. DS 收到放行后标记终稿（VersionStatus → FINAL）
5. 退回修改时 MaterialTask → PENDING_REVISE → DS 可上传新版本重新提交

---

## Task 详细说明

### P2-T01 · Phase 2 4 张表 DDL

| 属性 | 内容 |
|---|---|
| **类型** | DB |
| **前置依赖** | Phase 1 全部完成 |
| **目标** | 新增 design_versions / version_files / review_records / release_records 四张表 |
| **约束** | design_versions.uk_dver_task_id_version_no (task_id, version_no) 唯一索引；status VARCHAR(30) 对应 VersionStatus；is_final TINYINT(1)；is_overdue TINYINT(1) |
| **交付物** | `V3__phase2_design_version_tables.sql` |
| **验收** | Flyway 执行成功；SHOW TABLES 确认 4 张表存在 |

---

### P2-T02 · Phase 2 3 个枚举

| 属性 | 内容 |
|---|---|
| **类型** | BE-ENUM |
| **前置依赖** | P2-T01 |
| **目标** | VersionStatus(6) + ReviewResult(2) + AssignmentType(2) |
| **关键** | VersionStatus 6 态完整：DRAFT / PENDING_INTERNAL_REVIEW / RELEASED / IN_CLIENT_REVIEW / CLIENT_CONFIRMED / FINAL |
| **交付物** | 3 Java enum + TS 同步 |

---

### P2-T03 · Phase 2 实体 + Repository

| 属性 | 内容 |
|---|---|
| **类型** | BE-ENT |
| **前置依赖** | P2-T01, P2-T02 |
| **目标** | DesignVersion / VersionFile / ReviewRecord / ReleaseRecord + 对应 Repository |
| **交付物** | 4 Entity + 4 Repository |

---

### P2-T04 · VersionNoGenerator

| 属性 | 内容 |
|---|---|
| **类型** | BE-UTIL |
| **前置依赖** | P2-T03 |
| **目标** | 每个 MaterialTask 内 MAX(version_no 数字部分) + 1，返回 V{n}；并发安全 |
| **验收** | 10 线程并发各生成 5 个版本 → 同一 task 内 50 个 version_no 无重复 |
| **交付物** | VersionNoGenerator.java |

---

### P2-T05 · Version 状态机（6 态完整）

| 属性 | 内容 |
|---|---|
| **类型** | BE-SM |
| **前置依赖** | P2-T02 |
| **目标** | 6 态完整流转：DRAFT → PENDING_INTERNAL_REVIEW → RELEASED → IN_CLIENT_REVIEW → CLIENT_CONFIRMED → FINAL |
| **关键流转** | uploadVersion → DRAFT | submitForReview → PENDING_INTERNAL_REVIEW | releaseVersion → RELEASED | **注意 CONFLICT-01：releaseVersion 不触发 MaterialTask → APPROVED，仅 VersionStatus → RELEASED** |
| **交付物** | VersionStateMachine.java |

---

### P2-T06 · MaterialTask 状态机补全

| 属性 | 内容 |
|---|---|
| **类型** | BE-SM |
| **前置依赖** | P1-T16, **CONFLICT-01 已确认** |
| **目标** | Phase 1 未覆盖的循环流转：PENDING_DESIGN → PENDING_APPROVAL → PENDING_REVISE ↔ PENDING_APPROVAL |
| **CONFLICT-01 兑现** | releaseVersion 后 MaterialTask 仍保持 PENDING_APPROVAL，**绝不提前触发 APPROVED**；APPROVED 触发点推迟到 Phase 3 confirmVersion |
| **测试重点** | releaseVersion → 验证 MaterialTask.status 仍为 PENDING_APPROVAL（而非 APPROVED）|

---

### P2-T07 · DesignVersionService

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC |
| **前置依赖** | P2-T04, P2-T05, P2-T06 |
| **方法** | uploadVersion()（A-08）返回预签名 PUT URL + version_no + uploadSessionId；submitForReview()（A-09）→ Version DRAFT→PENDING_INTERNAL_REVIEW + MaterialTask PENDING_DESIGN→PENDING_APPROVAL；compareVersions()（A-10）并排返回两版本 design_note + change_summary 差异；releaseVersion()（A-11）VersionStatus→RELEASED **不触发 MaterialTask 变更**；rejectVersion()（A-12）Version 历史保留 + MaterialTask→PENDING_REVISE |
| **关键** | releaseVersion 不触发 APPROVED（CONFLICT-01）；rejectVersion 须填退回意见 |
| **交付物** | DesignVersionService.java |

---

### P2-T08 · MaterialTaskService 扩展

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC |
| **前置依赖** | P1-T18, P2-T06 |
| **新增方法** | reassignTask()（A-07）→ 原 DS 任务列表移除 + 新 DS 新增 + 写 Assignment(REASSIGN + previous_assignee_id)；cancelTask()（A-23）→ MaterialTask→CANCELLED + 非终态；uploadVersion 后置校验 is_overdue（task_deadline 已过 → 标 is_overdue=true）|
| **交付物** | MaterialTaskService 扩展 |

---

### P2-T09 · 重新分派 Assignment 记录

| 属性 | 内容 |
|---|---|
| **类型** | BE-SVC |
| **前置依赖** | P2-T08 |
| **目标** | REASSIGN 类型 Assignment + previous_assignee_id 记录 + 重新分派原因必填 |
| **交付物** | AssignmentService 新增方法 |

---

### P2-T10 · 版本模块 Controller

| 属性 | 内容 |
|---|---|
| **类型** | BE-CTL |
| **前置依赖** | P2-T07, P2-T08 |
| **端点** | A-07 reassignTask、A-08 uploadVersion、A-09 submitForReview、A-10 compareVersions、A-11 releaseVersion、A-12 rejectVersion、A-23 cancelTask、A-26 listVersions + **S-09 confirmVersionUpload**（Step 3 元数据确认写入 DB）|
| **权限注解** | A-07/A-10/A-11/A-12/A-23 → BO；A-08/A-09 → DS（仅自己任务）；A-26 → 三角色（数据可见性）|
| **交付物** | DesignVersionController.java + MaterialTaskController 扩展 |

---

### P2-T11 · 版本下载鉴权 Controller

| 属性 | 内容 |
|---|---|
| **类型** | BE-CTL |
| **前置依赖** | P1-T14, P1-T19 |
| **端点** | S-07 GET /api/v1/versions/{versionId}/download → 校验角色 + 数据可见性 → 生成限时 5min 预签名 GET URL → HTTP 302 |
| **规则** | 所有有数据可见性的角色（AM 仅自己订单 / BO 全部 / DS 仅自己上传）均可下载非 FINAL 版本；终稿下载限 AM（自己订单）+ BO |
| **交付物** | VersionController + 下载鉴权逻辑 |

---

### P2-T12 ~ P2-T15 · 前端（并行）

| 编号 | 名称 | 依赖 | 交付 |
|---|---|---|---|
| P2-T12 | Phase 2 API 函数封装 | P2-T10, P2-T11 | designVersion.ts / version.ts / feedback.ts 模块 |
| P2-T13 | 版本上传组件（复用 P1-T26 模板，Step 3 调 S-09）| P2-T12 | VersionUploader.vue |
| P2-T14 | DS TaskDetail 扩展 | P2-T13 | 版本历史列表 + 提交评审按钮 + 终稿标记按钮 |
| P2-T15 | BO InternalReview | P2-T12 | 并排对比面板 + 放行/退回操作 |

---

### P2-T16 · Phase 2 前后端联调

| 属性 | 内容 |
|---|---|
| **前置依赖** | P2-T10 ~ P2-T15 全部完成 |
| **目标** | 全链路：DS 上传 V1 → 提交评审 → BO 对比 → 放行（验证 CONFLICT-01 兑现：MaterialTask 仍为 PENDING_APPROVAL）/ 退回 → DS 上传 V2 重新提交 |

---

### P2-T17 ~ P2-T21 · 测试任务

| 编号 | 类型 | 测试目标 |
|---|---|---|
| P2-T17 | TEST | Version 状态机 6 态所有合法/非法流转 |
| P2-T18 | TEST | **CONFLICT-01 专项**：releaseVersion → 验证 MaterialTask.status 仍为 PENDING_APPROVAL；rejectVersion → PENDING_REVISE；APPROVED 不应在 Phase 2 出现 |
| P2-T19 | TEST | 文件上传全链路（200MB 限制 NFR-F1 + 格式白名单 NFR-F2 + 版本自动编号 + 孤儿对象清理定时任务 + S-07 下载鉴权）|
| P2-T20 | TEST | 审计日志（UPLOAD_VERSION / SUBMIT_REVIEW / RELEASE_VERSION / REJECT_VERSION 正确写入）|
| P2-T21 | TEST | RBAC 扩展（DS 仅上传自己任务；BO 可放行所有；下载鉴权正确）|

---

### P2-T22 · Phase 2 端到端验收

| 属性 | 内容 |
|---|---|
| **前置依赖** | P2-T16 + P2-T17 ~ P2-T21 全部 PASS |
| **验收覆盖** | DS-01 完整 / DS-02（V1/V2 自动编号）/ DS-03（设计说明必填）/ DS-04（提交 BO 待办）/ BO-05（并排对比）/ BO-06（放行留痕）/ DS-06（放行后可见终稿按钮）/ A-07 reassignTask / A-23 cancelTask |
| **关键验证** | CONFLICT-01：BO 放行后 MaterialTask 仍为 PENDING_APPROVAL，不能提前 APPROVED |

---

## Phase 2 完成标志

| 检查点 | 要求 |
|---|---|
| DB | 4 新表 + 20 总表 |
| API | 新增 A-07~A-12 / A-23 / A-26 + S-07 / S-09 |
| 状态机 | Version 6 态全部流转 PASS |
| **CONFLICT-01** | **releaseVersion 后 MaterialTask 仍为 PENDING_APPROVAL，验证通过** |
| E2E | DS 上传 → BO 评审 → 放行/退回 → DS 迭代 完整链路 |
| 审计 | 5 个审计操作均有记录 |
| 文件上传 | 成功 + 失败 + 重试 + 200MB + 下载鉴权 |

---

> Phase 3 聚焦 **客户评审 → 反馈 → 确认 → 交付闭环**，核心是 CONFLICT-01/CONFLICT-02 的最终兑现（confirmVersion 触发 APPROVED + canMarkCompleted 严格 count 校验）；Phase 4 聚焦 **看板 / 时间线 / 通知强化 / 异常** + **取消订单完整实现** + **孤儿对象清理定时任务**。
