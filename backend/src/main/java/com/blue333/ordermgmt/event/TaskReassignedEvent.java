package com.blue333.ordermgmt.event;

import java.time.LocalDateTime;
import java.util.List;

/** Published after a material task is handed over to another designer (A-07). */
public record TaskReassignedEvent(
    List<Long> recipientIds,
    Long previousAssigneeId,
    Long taskId,
    String orderNo,
    LocalDateTime deadline) {
  public TaskReassignedEvent {
    recipientIds = EventRecipients.normalize(recipientIds);
  }
}
