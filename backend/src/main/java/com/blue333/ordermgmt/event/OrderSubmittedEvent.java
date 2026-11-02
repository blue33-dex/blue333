package com.blue333.ordermgmt.event;

import java.util.List;

/** Published after an order is submitted and its recipients should be notified. */
public record OrderSubmittedEvent(List<Long> recipientIds, Long orderId, String orderNo) {
  public OrderSubmittedEvent {
    recipientIds = EventRecipients.normalize(recipientIds);
  }
}
