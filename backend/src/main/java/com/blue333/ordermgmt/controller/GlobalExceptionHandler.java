package com.blue333.ordermgmt.controller;

import com.blue333.ordermgmt.dto.ErrorResponse;
import com.blue333.ordermgmt.service.InvalidStateTransitionException;
import com.blue333.ordermgmt.service.ResourceNotFoundException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to {@link ErrorResponse}. Extends {@link ResponseEntityExceptionHandler} so that
 * standard Spring MVC errors (unknown route, unsupported method, missing parameter, ...) keep their
 * proper 4xx status instead of falling into the generic 500 handler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  static final String VALIDATION_ERROR = "VALIDATION_ERROR";
  static final String INTERNAL_ERROR_MESSAGE = "An unexpected error occurred";
  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
    return error(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, messageOr(exception, "Invalid request"), null);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleArgumentTypeMismatch(
      MethodArgumentTypeMismatchException exception) {
    return error(HttpStatus.BAD_REQUEST, VALIDATION_ERROR,
        "Invalid value for parameter '" + exception.getName() + "'", null);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", messageOr(exception, "Resource not found"), null);
  }

  @ExceptionHandler(InvalidStateTransitionException.class)
  public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidStateTransitionException exception) {
    return error(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION",
        messageOr(exception, "Invalid state transition"), null);
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException exception) {
    return error(HttpStatus.UNPROCESSABLE_ENTITY, "PRECONDITION_FAILED",
        messageOr(exception, "Precondition failed"), null);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
    return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", null);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException exception) {
    HttpStatusCode status = exception.getStatusCode();
    String message = exception.getReason() != null ? exception.getReason() : reasonPhrase(status);
    return ResponseEntity.status(status).headers(exception.getHeaders())
        .body(new ErrorResponse(statusName(status), message, null));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
    LOGGER.error("Unhandled exception", exception);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", INTERNAL_ERROR_MESSAGE, null);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status,
      WebRequest request) {
    String detail = exception.getBindingResult().getFieldErrors().stream()
        .map(this::describe)
        .collect(Collectors.joining("; "));
    ErrorResponse body = new ErrorResponse(VALIDATION_ERROR, "Request validation failed",
        detail.isBlank() ? null : detail);
    return handleExceptionInternal(exception, body, headers, HttpStatus.BAD_REQUEST, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status,
      WebRequest request) {
    // The parser message can echo internal type names, so it is not returned.
    ErrorResponse body = ErrorResponse.of(VALIDATION_ERROR, "Request body is missing or malformed");
    return handleExceptionInternal(exception, body, headers, HttpStatus.BAD_REQUEST, request);
  }

  /** Converts the framework's ProblemDetail bodies for other standard MVC exceptions. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception, Object body, HttpHeaders headers, HttpStatusCode statusCode,
      WebRequest request) {
    Object errorBody = body;
    if (!(body instanceof ErrorResponse)) {
      String code = statusCode.value() == HttpStatus.BAD_REQUEST.value()
          ? VALIDATION_ERROR : statusName(statusCode);
      String message = reasonPhrase(statusCode);
      if (statusCode.is4xxClientError()
          && exception instanceof org.springframework.web.ErrorResponse frameworkError
          && frameworkError.getBody().getDetail() != null) {
        message = frameworkError.getBody().getDetail();
      }
      if (statusCode.is5xxServerError()) {
        LOGGER.error("Request failed with status {}", statusCode.value(), exception);
        message = INTERNAL_ERROR_MESSAGE;
      }
      errorBody = ErrorResponse.of(code, message);
    }
    return super.handleExceptionInternal(exception, errorBody, headers, statusCode, request);
  }

  private String describe(FieldError fieldError) {
    String message = fieldError.getDefaultMessage() == null ? "is invalid" : fieldError.getDefaultMessage();
    return fieldError.getField() + ": " + message;
  }

  private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message, String detail) {
    return ResponseEntity.status(status).body(new ErrorResponse(code, message, detail));
  }

  private String messageOr(Exception exception, String fallback) {
    String message = exception.getMessage();
    return message == null || message.isBlank() ? fallback : message;
  }

  private static String statusName(HttpStatusCode status) {
    HttpStatus resolved = HttpStatus.resolve(status.value());
    return resolved != null ? resolved.name() : "HTTP_" + status.value();
  }

  private static String reasonPhrase(HttpStatusCode status) {
    HttpStatus resolved = HttpStatus.resolve(status.value());
    return resolved != null ? resolved.getReasonPhrase() : "HTTP " + status.value();
  }
}
