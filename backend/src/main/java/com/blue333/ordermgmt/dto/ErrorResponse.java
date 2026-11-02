package com.blue333.ordermgmt.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Uniform API error body. {@code detail} is omitted when there is nothing safe to add. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, String detail) {
  public static ErrorResponse of(String code, String message) {
    return new ErrorResponse(code, message, null);
  }
}
