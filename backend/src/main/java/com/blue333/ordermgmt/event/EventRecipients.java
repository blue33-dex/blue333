package com.blue333.ordermgmt.event;

import java.util.List;

final class EventRecipients {
  private EventRecipients() {
  }

  static List<Long> normalize(List<Long> recipientIds) {
    if (recipientIds == null) {
      return List.of();
    }
    return recipientIds.stream().filter(id -> id != null).distinct().toList();
  }
}
