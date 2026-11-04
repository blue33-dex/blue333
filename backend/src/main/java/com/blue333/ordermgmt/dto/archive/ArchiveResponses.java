package com.blue333.ordermgmt.dto.archive;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/** API views for the Phase 3 delivery archive (A-19/A-28). Storage keys are never exposed. */
public final class ArchiveResponses {
  private ArchiveResponses() {
  }

  public record DeliveryArchiveResponse(
      @JsonProperty("id") Long id,
      @JsonProperty("order_id") Long orderId,
      @JsonProperty("archiver_id") Long archiverId,
      @JsonProperty("archiver_name") String archiverName,
      @JsonProperty("archived_at") LocalDateTime archivedAt,
      @JsonProperty("archive_report_available") boolean archiveReportAvailable) {
  }
}
