package com.blue333.ordermgmt.controller;

import com.blue333.ordermgmt.domain.enums.ExceptionStatus;
import com.blue333.ordermgmt.domain.enums.ExceptionType;
import com.blue333.ordermgmt.dto.ApiResponse;
import com.blue333.ordermgmt.dto.exception.ExceptionResponses.ExceptionRecordResponse;
import com.blue333.ordermgmt.dto.exception.ExceptionResponses.ExceptionScanResponse;
import com.blue333.ordermgmt.service.CurrentUserService;
import com.blue333.ordermgmt.service.ExceptionAutoGenerator;
import com.blue333.ordermgmt.service.ExceptionRecordService;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A-20 record exception / A-21 close exception (BO only). */
@RestController
@RequestMapping("/api/v1")
public class ExceptionController {
  private final ExceptionRecordService exceptionRecordService;
  private final ExceptionAutoGenerator exceptionAutoGenerator;
  private final CurrentUserService currentUserService;

  public ExceptionController(ExceptionRecordService exceptionRecordService,
      ExceptionAutoGenerator exceptionAutoGenerator, CurrentUserService currentUserService) {
    this.exceptionRecordService = exceptionRecordService;
    this.exceptionAutoGenerator = exceptionAutoGenerator;
    this.currentUserService = currentUserService;
  }

  /** A-20: a BO records a manual exception against an order. */
  @PostMapping("/orders/{orderId:\\d+}/exceptions")
  @PreAuthorize("hasRole('BO')")
  public ResponseEntity<ApiResponse<ExceptionRecordResponse>> record(@PathVariable Long orderId,
      @Valid @RequestBody CreateExceptionRequest request) {
    ExceptionRecordResponse response = exceptionRecordService.record(orderId, request.exceptionType(),
        request.description(), currentUserService.requireCurrentUser());
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
  }

  @GetMapping("/orders/{orderId:\\d+}/exceptions")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<List<ExceptionRecordResponse>> listForOrder(@PathVariable Long orderId) {
    return ApiResponse.of(exceptionRecordService.listForOrder(orderId,
        currentUserService.requireCurrentUser()));
  }

  /** Exception queue used by the BO exception/timeline workspace. */
  @GetMapping("/exceptions")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<List<ExceptionRecordResponse>> list(
      @RequestParam(required = false) ExceptionStatus status,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return ApiResponse.page(exceptionRecordService.list(status, cursor, limit,
        currentUserService.requireCurrentUser()));
  }

  /** A-21: closing an exception is the precondition for archiving the order. */
  @PatchMapping("/exceptions/{exceptionId:\\d+}/close")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<ExceptionRecordResponse> close(@PathVariable Long exceptionId,
      @RequestBody(required = false) CloseExceptionRequest request) {
    String resolution = request == null ? null : request.resolution();
    return ApiResponse.of(exceptionRecordService.close(exceptionId, resolution,
        currentUserService.requireCurrentUser()));
  }

  /**
   * Operational trigger for the daily overdue scan (P4-T05). The same job runs on its cron; exposing
   * it to BO keeps the behaviour verifiable without waiting for 04:00.
   */
  @PostMapping("/exceptions/auto-scan")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<ExceptionScanResponse> autoScan() {
    currentUserService.requireCurrentUser();
    return ApiResponse.of(exceptionAutoGenerator.scanOverdue());
  }

  public record CreateExceptionRequest(
      @NotNull @JsonProperty("exception_type") ExceptionType exceptionType,
      @NotBlank @JsonProperty("description") String description) {}

  public record CloseExceptionRequest(@JsonProperty("resolution") String resolution) {}
}
