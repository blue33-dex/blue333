package com.blue333.ordermgmt.event;

import java.time.LocalDateTime;
import java.util.List;

/** Published after a material task is assigned to one or more recipients. */
public record TaskAssignedEvent(
    List<Long> recipientIds, Long taskId, String orderNo, LocalDateTime deadline) {
  public TaskAssignedEvent {
    recipientIds = EventRecipients.normalize(recipientIds);
  }
}
