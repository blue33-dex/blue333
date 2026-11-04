package com.blue333.ordermgmt.event;

import java.util.List;

/** Published after a manual or automatic exception record is persisted so the BO queue is notified. */
public record ExceptionRecordedEvent(
    Long exceptionId,
    Long orderId,
    String orderNo,
    String exceptionType,
    String description,
    List<Long> recipientIds) {
}
