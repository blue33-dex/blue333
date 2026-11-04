package com.blue333.ordermgmt.domain.enums;

/** Design-version lifecycle states defined by GLOSSARY EN-03. */
public enum VersionStatus {
  DRAFT,
  PENDING_INTERNAL_REVIEW,
  RELEASED,
  IN_CLIENT_REVIEW,
  CLIENT_CONFIRMED,
  FINAL
}
