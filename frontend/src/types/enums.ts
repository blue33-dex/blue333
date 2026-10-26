// Phase 1 枚举 · 严格对齐 GLOSSARY §02 UPPER_SNAKE_CASE
// 全栈唯一权威来源：docs/01-requirements/GLOSSARY.md

/** 订单状态 · GLOSSARY EN-01 */
export enum OrderStatus {
  PENDING_CONFIRM = 'PENDING_CONFIRM',
  IN_PRODUCTION = 'IN_PRODUCTION',
  COMPLETED = 'COMPLETED',
  CANCELLED = 'CANCELLED',
}

/** 物料任务状态 · GLOSSARY EN-02 */
export enum MaterialTaskStatus {
  PENDING_DESIGN = 'PENDING_DESIGN',
  PENDING_APPROVAL = 'PENDING_APPROVAL',
  PENDING_REVISE = 'PENDING_REVISE',
  APPROVED = 'APPROVED',
  CANCELLED = 'CANCELLED',
}

/** 物料类型 · GLOSSARY EN-06 */
export enum MaterialType {
  POSTER = 'POSTER',
  FLYER = 'FLYER',
  BANNER = 'BANNER',
  BROCHURE = 'BROCHURE',
  ROLLUP = 'ROLLUP',
  OTHER = 'OTHER',
}

/** 优先级 · GLOSSARY EN-07 */
export enum Priority {
  HIGH = 'HIGH',
  MEDIUM = 'MEDIUM',
  LOW = 'LOW',
}

/** 退回原因类型 · GLOSSARY EN-13 */
export enum RejectReasonType {
  INFORMATION_INCOMPLETE = 'INFORMATION_INCOMPLETE',
  SPEC_UNCLEAR = 'SPEC_UNCLEAR',
  MATERIAL_MISSING = 'MATERIAL_MISSING',
  OTHER = 'OTHER',
}

/** 角色类型 · GLOSSARY EN-14 */
export enum RoleType {
  AM = 'AM',
  BO = 'BO',
  DS = 'DS',
}

/** 通知类型 · GLOSSARY EN-15 */
export enum NotificationType {
  TODO = 'TODO',
  STATUS_CHANGE = 'STATUS_CHANGE',
}

/** 审计操作类型（Phase 1 子集）· GLOSSARY EN-16 */
export enum AuditActionType {
  CREATE_ORDER = 'CREATE_ORDER',
  SUBMIT_ORDER = 'SUBMIT_ORDER',
  REJECT_ORDER = 'REJECT_ORDER',
  ASSIGN_TASK = 'ASSIGN_TASK',
  CANCEL_ORDER = 'CANCEL_ORDER',
  CANCEL_TASK = 'CANCEL_TASK',
}

/** 审计目标类型（Phase 1 子集）· GLOSSARY EN-17 */
export enum AuditTargetType {
  ORDER = 'ORDER',
  MATERIAL_TASK = 'MATERIAL_TASK',
}
