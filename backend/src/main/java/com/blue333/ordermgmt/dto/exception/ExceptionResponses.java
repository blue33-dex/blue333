package com.blue333.ordermgmt.dto.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public final class ExceptionResponses {
  private ExceptionResponses() {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ExceptionRecordResponse(
      @JsonProperty("id") Long id,
      @JsonProperty("order_id") Long orderId,
      @JsonProperty("order_no") String orderNo,
      @JsonProperty("task_id") Long taskId,
      @JsonProperty("exception_type") String exceptionType,
      @JsonProperty("status") String status,
      @JsonProperty("description") String description,
      @JsonProperty("resolution") String resolution,
      @JsonProperty("recorder_id") Long recorderId,
      @JsonProperty("recorder_name") String recorderName,
      @JsonProperty("created_at") LocalDateTime createdAt,
      @JsonProperty("updated_at") LocalDateTime updatedAt) {}

  /** Result of the automatic overdue scan, also returned by its manual BO trigger. */
  public record ExceptionScanResponse(
      @JsonProperty("scanned_at") LocalDateTime scannedAt,
      @JsonProperty("created_count") int createdCount,
      @JsonProperty("created_ids") java.util.List<Long> createdIds) {}
}
