package com.blue333.ordermgmt.domain.enums;

/**
 * Exception lifecycle (E-18). The automatic overdue scan creates {@code PENDING_CONFIRM} records;
 * only a BO closes them. Archiving requires that no record remains outside {@code CLOSED}.
 */
public enum ExceptionStatus {
  PENDING_CONFIRM,
  IN_PROGRESS,
  CLOSED
}
