package com.blue333.ordermgmt.dto.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderResponses {
  private OrderResponses() {}

  public record MaterialSpecResponse(@JsonProperty("id") Long id, @JsonProperty("material_type") String materialType, @JsonProperty("spec_size") String specSize) {}
  public record AttachmentResponse(@JsonProperty("id") Long id, @JsonProperty("file_name") String fileName, @JsonProperty("file_size") Long fileSize, @JsonProperty("created_at") LocalDateTime createdAt) {}
  public record TaskResponse(@JsonProperty("id") Long id, @JsonProperty("material_type") String materialType, @JsonProperty("spec_size") String specSize, @JsonProperty("assignee_id") Long assigneeId, @JsonProperty("assignee_name") String assigneeName, @JsonProperty("task_deadline") LocalDateTime taskDeadline, @JsonProperty("priority") String priority, @JsonProperty("status") String status) {}
  public record BaselineResponse(@JsonProperty("baseline_version") Integer baselineVersion, @JsonProperty("customer_name") String customerName, @JsonProperty("use_scene") String useScene, @JsonProperty("delivery_deadline") LocalDate deliveryDeadline, @JsonProperty("remark") String remark, @JsonProperty("material_specs") List<MaterialSpecResponse> materialSpecs, @JsonProperty("created_at") LocalDateTime createdAt) {}
  public record RejectReasonResponse(@JsonProperty("reject_reason_type") String reasonType, @JsonProperty("remark") String remark, @JsonProperty("rejected_by") String rejectedBy, @JsonProperty("rejected_at") LocalDateTime rejectedAt) {}
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record OrderSummaryResponse(@JsonProperty("id") Long id, @JsonProperty("order_no") String orderNo, @JsonProperty("customer_name") String customerName, @JsonProperty("status") String status, @JsonProperty("am_name") String amName, @JsonProperty("delivery_deadline") LocalDate deliveryDeadline, @JsonProperty("created_at") LocalDateTime createdAt, @JsonProperty("attachment_count") long attachmentCount, @JsonProperty("task_count") long taskCount, @JsonProperty("reject_reason") RejectReasonResponse rejectReason) {}
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record OrderDetailResponse(@JsonProperty("id") Long id, @JsonProperty("order_no") String orderNo, @JsonProperty("customer_name") String customerName, @JsonProperty("use_scene") String useScene, @JsonProperty("delivery_deadline") LocalDate deliveryDeadline, @JsonProperty("remark") String remark, @JsonProperty("status") String status, @JsonProperty("am_id") Long amId, @JsonProperty("am_name") String amName, @JsonProperty("created_at") LocalDateTime createdAt, @JsonProperty("updated_at") LocalDateTime updatedAt, @JsonProperty("material_specs") List<MaterialSpecResponse> materialSpecs, @JsonProperty("attachments") List<AttachmentResponse> attachments, @JsonProperty("requirement_baseline") BaselineResponse requirementBaseline, @JsonProperty("tasks") List<TaskResponse> tasks, @JsonProperty("reject_reason") RejectReasonResponse rejectReason) {}
}
