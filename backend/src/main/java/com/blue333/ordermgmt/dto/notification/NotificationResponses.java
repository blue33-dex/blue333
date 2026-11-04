package com.blue333.ordermgmt.dto.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public final class NotificationResponses {
  private NotificationResponses() {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NotificationResponse(
      @JsonProperty("id") Long id,
      @JsonProperty("notification_type") String notificationType,
      @JsonProperty("resource_type") String resourceType,
      @JsonProperty("resource_id") Long resourceId,
      @JsonProperty("title") String title,
      @JsonProperty("content") String content,
      @JsonProperty("read") boolean read,
      @JsonProperty("read_at") LocalDateTime readAt,
      @JsonProperty("created_at") LocalDateTime createdAt) {}

  public record UnreadCountResponse(@JsonProperty("unread_count") long unreadCount) {}

  public record MarkAllReadResponse(@JsonProperty("updated_count") int updatedCount) {}
}
