package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderStateMachineTest {
  private final OrderStateMachine stateMachine = new OrderStateMachine();

  @Test
  void permitsPendingConfirmationTransitions() {
    Order order = pendingOrder();

    stateMachine.transition(order, OrderStatus.PENDING_CONFIRM);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRM);

    stateMachine.transition(order, OrderStatus.IN_PRODUCTION);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PRODUCTION);

    stateMachine.transition(order, OrderStatus.CANCELLED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void rejectsIllegalOrTerminalTransitions() {
    Order pendingOrder = pendingOrder();
    assertThatThrownBy(() -> stateMachine.transition(pendingOrder, OrderStatus.COMPLETED))
        .isInstanceOf(InvalidStateTransitionException.class);

    Order cancelledOrder = pendingOrder();
    stateMachine.transition(cancelledOrder, OrderStatus.CANCELLED);
    assertThatThrownBy(() -> stateMachine.transition(cancelledOrder, OrderStatus.PENDING_CONFIRM))
        .isInstanceOf(InvalidStateTransitionException.class);
  }

  private Order pendingOrder() {
    return Order.create("ORD-20250101-001", "Customer", "Store display", LocalDate.now(), null,
        Mockito.mock(User.class));
  }
}
