package com.blue333.ordermgmt.event;

import java.util.List;

/** Published after an order is rejected and its recipients should be notified. */
public record OrderRejectedEvent(
    List<Long> recipientIds, Long orderId, String orderNo, String rejectionReason) {
  public OrderRejectedEvent {
    recipientIds = EventRecipients.normalize(recipientIds);
  }
}
