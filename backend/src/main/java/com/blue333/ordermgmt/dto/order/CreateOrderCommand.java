package com.blue333.ordermgmt.dto.order;

import java.time.LocalDate;
import java.util.List;

/** Core command only. Authentication and HTTP binding are intentionally deferred. */
public record CreateOrderCommand(
    String customerName,
    String useScene,
    LocalDate deliveryDeadline,
    String remark,
    List<MaterialSpecCommand> materialSpecs) {
}
