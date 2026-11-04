package com.blue333.ordermgmt.dto.kanban;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** A-25 kanban board: one column per order status with a light card payload (single-query read model). */
public final class KanbanResponses {
  private KanbanResponses() {}

  public record KanbanCardResponse(
      @JsonProperty("id") Long id,
      @JsonProperty("order_no") String orderNo,
      @JsonProperty("customer_name") String customerName,
      @JsonProperty("status") String status,
      @JsonProperty("am_name") String amName,
      @JsonProperty("delivery_deadline") LocalDate deliveryDeadline,
      @JsonProperty("created_at") LocalDateTime createdAt,
      @JsonProperty("task_count") long taskCount,
      @JsonProperty("open_feedback_count") long openFeedbackCount) {}

  public record KanbanColumnResponse(
      @JsonProperty("status") String status,
      @JsonProperty("total") long total,
      @JsonProperty("orders") List<KanbanCardResponse> orders) {}

  public record KanbanResponse(
      @JsonProperty("generated_at") LocalDateTime generatedAt,
      @JsonProperty("columns") List<KanbanColumnResponse> columns) {}
}
