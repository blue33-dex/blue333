package com.blue333.ordermgmt.service;

/** Raised when an order lifecycle action is not permitted from its current state. */
public class InvalidStateTransitionException extends IllegalStateException {
  public InvalidStateTransitionException(String message) {
    super(message);
  }
}
