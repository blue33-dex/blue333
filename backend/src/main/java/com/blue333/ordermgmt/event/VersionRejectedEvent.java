package com.blue333.ordermgmt.event;

import java.util.List;

/** Published after BO rejects a version in internal review (A-12, M9). */
public record VersionRejectedEvent(
    List<Long> recipientIds, Long versionId, String orderNo, String versionNo, String reviewComment) {
  public VersionRejectedEvent {
    recipientIds = EventRecipients.normalize(recipientIds);
  }
}
