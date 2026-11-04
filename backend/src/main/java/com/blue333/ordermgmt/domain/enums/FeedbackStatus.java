package com.blue333.ordermgmt.domain.enums;

/**
 * Feedback handling state (GLOSSARY EN-04). EXPIRED is set by the system when a newer version of the
 * order becomes FINAL, and an EXPIRED item can never be marked RESOLVED again.
 */
public enum FeedbackStatus {
  UNRESOLVED,
  RESOLVED,
  EXPIRED
}
