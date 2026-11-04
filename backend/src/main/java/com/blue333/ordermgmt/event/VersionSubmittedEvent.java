package com.blue333.ordermgmt.event;

import java.util.List;

/** Published after a design version is submitted for internal review (DS-04, M9). */
public record VersionSubmittedEvent(
    List<Long> recipientIds, Long versionId, String orderNo, String versionNo, String materialLabel) {
  public VersionSubmittedEvent {
    recipientIds = EventRecipients.normalize(recipientIds);
  }
}
