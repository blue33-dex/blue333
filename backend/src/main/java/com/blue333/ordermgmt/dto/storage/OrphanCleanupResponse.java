package com.blue333.ordermgmt.dto.storage;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/** Result of one orphan-object sweep (P4-T11). */
public record OrphanCleanupResponse(
    @JsonProperty("cleaned_at") LocalDateTime cleanedAt,
    @JsonProperty("quarantined_count") int quarantinedCount,
    @JsonProperty("purged_count") int purgedCount,
    @JsonProperty("failed_count") int failedCount) {
}
