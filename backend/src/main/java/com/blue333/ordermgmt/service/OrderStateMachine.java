package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Defines the order lifecycle transitions owned by the core order service. */
@Component
public class OrderStateMachine {
  private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = transitions();

  public void transition(Order order, OrderStatus targetStatus) {
    OrderStatus currentStatus = order.getStatus();
    if (!TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(targetStatus)) {
      throw new InvalidStateTransitionException(
          "Order cannot transition from " + currentStatus + " to " + targetStatus);
    }
    order.changeStatus(targetStatus);
  }

  private static Map<OrderStatus, Set<OrderStatus>> transitions() {
    Map<OrderStatus, Set<OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);
    // Submission and rejection keep the order in PENDING_CONFIRM until task assignment is added.
    // A-18 delivers IN_PRODUCTION → COMPLETED; COMPLETED and CANCELLED are terminal (BR-12).
    transitions.put(OrderStatus.PENDING_CONFIRM,
        Set.of(OrderStatus.PENDING_CONFIRM, OrderStatus.IN_PRODUCTION, OrderStatus.CANCELLED));
    transitions.put(OrderStatus.IN_PRODUCTION,
        Set.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
    transitions.put(OrderStatus.COMPLETED, Set.of());
    transitions.put(OrderStatus.CANCELLED, Set.of());
    return Map.copyOf(transitions);
  }
}
