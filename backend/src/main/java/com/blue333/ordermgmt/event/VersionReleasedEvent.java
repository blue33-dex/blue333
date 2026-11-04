package com.blue333.ordermgmt.event;

import java.util.List;

/** Published after BO releases a version to client review (A-11, M9). */
public record VersionReleasedEvent(
    List<Long> recipientIds, Long versionId, String orderNo, String versionNo) {
  public VersionReleasedEvent {
    recipientIds = EventRecipients.normalize(recipientIds);
  }
}
