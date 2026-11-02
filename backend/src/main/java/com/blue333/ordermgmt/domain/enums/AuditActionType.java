package com.blue333.ordermgmt.domain.enums;

/**
 * Phase 1 state-changing actions recorded in the audit log.
 * CANCEL_TASK is included because P1-T16 requires MaterialTask cancellation
 * to be audited under BR-14.
 */
public enum AuditActionType {
  CREATE_ORDER,
  SUBMIT_ORDER,
  REJECT_ORDER,
  ASSIGN_TASK,
  CANCEL_ORDER,
  CANCEL_TASK
}
