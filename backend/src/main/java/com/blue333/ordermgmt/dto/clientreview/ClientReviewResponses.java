package com.blue333.ordermgmt.dto.clientreview;

import com.blue333.ordermgmt.dto.version.VersionResponses.VersionResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/** API views for the Phase 3 client-review flow (A-13 ~ A-17, A-27). */
public final class ClientReviewResponses {
  private ClientReviewResponses() {
  }

  public record ClientReviewResponse(
      @JsonProperty("id") Long id,
      @JsonProperty("version_id") Long versionId,
      @JsonProperty("version_no") String versionNo,
      @JsonProperty("order_id") Long orderId,
      @JsonProperty("status") String status,
      @JsonProperty("initiator_id") Long initiatorId,
      @JsonProperty("initiator_name") String initiatorName,
      @JsonProperty("started_at") LocalDateTime startedAt,
      @JsonProperty("completed_at") LocalDateTime completedAt) {
  }

  public record FeedbackResponse(
      @JsonProperty("id") Long id,
      @JsonProperty("feedback_no") String feedbackNo,
      @JsonProperty("version_id") Long versionId,
      @JsonProperty("version_no") String versionNo,
      @JsonProperty("client_review_id") Long clientReviewId,
      @JsonProperty("content") String content,
      @JsonProperty("feedback_type") String feedbackType,
      @JsonProperty("source") String source,
      @JsonProperty("status") String status,
      @JsonProperty("created_by_id") Long createdById,
      @JsonProperty("created_by_name") String createdByName,
      @JsonProperty("created_at") LocalDateTime createdAt,
      @JsonProperty("updated_at") LocalDateTime updatedAt) {
  }

  /**
   * A-16 result. Unresolved feedback does not block confirmation (GLOSSARY); the caller receives
   * the count and a warning so the UI can surface it.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ConfirmVersionResponse(
      @JsonProperty("version") VersionResponse version,
      @JsonProperty("confirmation_record_id") Long confirmationRecordId,
      @JsonProperty("confirmer_name") String confirmerName,
      @JsonProperty("confirmed_at") LocalDateTime confirmedAt,
      @JsonProperty("unresolved_feedback_count") long unresolvedFeedbackCount,
      @JsonProperty("warning") String warning) {
  }
}
