package com.blue333.ordermgmt.dto.timeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/** A-29 order timeline: audit entries of the order and everything belonging to it. */
public final class TimelineResponses {
  private TimelineResponses() {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record TimelineEntryResponse(
      @JsonProperty("id") Long id,
      @JsonProperty("action_type") String actionType,
      @JsonProperty("action_label") String actionLabel,
      @JsonProperty("target_type") String targetType,
      @JsonProperty("target_id") Long targetId,
      @JsonProperty("operator_id") Long operatorId,
      @JsonProperty("operator_name") String operatorName,
      @JsonProperty("before_state") String beforeState,
      @JsonProperty("after_state") String afterState,
      @JsonProperty("result") String result,
      @JsonProperty("remark") String remark,
      @JsonProperty("operated_at") LocalDateTime operatedAt) {}
}
