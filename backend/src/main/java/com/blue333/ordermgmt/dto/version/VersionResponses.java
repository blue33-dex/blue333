package com.blue333.ordermgmt.dto.version;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/** API views of design versions. Storage paths are never exposed; downloads go through S-07. */
public final class VersionResponses {
  private VersionResponses() {
  }

  public record VersionFileResponse(
      @JsonProperty("file_name") String fileName,
      @JsonProperty("file_size") Long fileSize) {
  }

  public record VersionResponse(
      @JsonProperty("id") Long id,
      @JsonProperty("task_id") Long taskId,
      @JsonProperty("order_id") Long orderId,
      @JsonProperty("material_type") String materialType,
      @JsonProperty("spec_size") String specSize,
      @JsonProperty("version_no") String versionNo,
      @JsonProperty("status") String status,
      @JsonProperty("design_note") String designNote,
      @JsonProperty("change_summary") String changeSummary,
      @JsonProperty("uploader_id") Long uploaderId,
      @JsonProperty("uploader_name") String uploaderName,
      @JsonProperty("is_final") boolean isFinal,
      @JsonProperty("is_overdue") boolean isOverdue,
      @JsonProperty("file") VersionFileResponse file,
      @JsonProperty("submitted_at") LocalDateTime submittedAt,
      @JsonProperty("created_at") LocalDateTime createdAt) {
  }

  /** A-08 Step 1 result. {@code next_version_no} is a preview; the number is assigned at confirm time. */
  public record UploadStartResponse(
      @JsonProperty("upload_session_id") String uploadSessionId,
      @JsonProperty("presigned_url") String presignedUrl,
      @JsonProperty("object_key") String objectKey,
      @JsonProperty("expires_in_minutes") long expiresInMinutes,
      @JsonProperty("next_version_no") String nextVersionNo) {
  }

  /** A-10: two versions of one order side by side, plus whether each free-text field differs. */
  public record VersionComparisonResponse(
      @JsonProperty("order_id") Long orderId,
      @JsonProperty("left") VersionResponse left,
      @JsonProperty("right") VersionResponse right,
      @JsonProperty("design_note_changed") boolean designNoteChanged,
      @JsonProperty("change_summary_changed") boolean changeSummaryChanged) {
  }
}
