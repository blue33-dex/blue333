package com.blue333.ordermgmt.domain.enums;

/**
 * State-changing actions recorded in the audit log (GLOSSARY EN-16), added phase by phase.
 * CANCEL_TASK is included because P1-T16 requires MaterialTask cancellation
 * to be audited under BR-14.
 */
public enum AuditActionType {
  CREATE_ORDER,
  SUBMIT_ORDER,
  REJECT_ORDER,
  ASSIGN_TASK,
  REASSIGN_TASK,
  CANCEL_ORDER,
  CANCEL_TASK,
  UPLOAD_VERSION,
  SUBMIT_REVIEW,
  RELEASE_VERSION,
  REJECT_VERSION,
  RECORD_FEEDBACK,
  CONFIRM_VERSION,
  MARK_FINAL,
  MARK_DELIVERED,
  ARCHIVE_ORDER,
  RECORD_EXCEPTION,
  CLOSE_EXCEPTION
}
