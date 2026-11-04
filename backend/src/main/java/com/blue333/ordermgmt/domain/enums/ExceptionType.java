package com.blue333.ordermgmt.domain.enums;

/** Exception type (E-18). */
public enum ExceptionType {
  /** A material task passed its deadline, or a human recorded a schedule risk. */
  DELAY,
  /** Any other manually recorded exception. */
  OTHER
}
