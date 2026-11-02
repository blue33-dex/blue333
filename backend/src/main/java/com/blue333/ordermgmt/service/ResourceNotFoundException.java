package com.blue333.ordermgmt.service;

/**
 * Raised when a resource does not exist or is not visible to the current user. Both cases map to
 * HTTP 404 so callers cannot probe for the existence of resources they may not see.
 */
public class ResourceNotFoundException extends RuntimeException {
  public ResourceNotFoundException(String message) {
    super(message);
  }
}
