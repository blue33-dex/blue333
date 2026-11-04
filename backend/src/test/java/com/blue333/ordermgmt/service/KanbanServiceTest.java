package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.FeedbackStatus;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.dto.kanban.KanbanResponses.KanbanCardResponse;
import com.blue333.ordermgmt.dto.kanban.KanbanResponses.KanbanColumnResponse;
import com.blue333.ordermgmt.dto.kanban.KanbanResponses.KanbanResponse;
import com.blue333.ordermgmt.repository.FeedbackItemRepository;
import com.blue333.ordermgmt.repository.MaterialTaskRepository;
import com.blue333.ordermgmt.repository.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

/** P4-T07: A-25 kanban columns, totals, card counters and filters. */
class KanbanServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");

  private OrderRepository orderRepository;
  private MaterialTaskRepository materialTaskRepository;
  private FeedbackItemRepository feedbackItemRepository;
  private KanbanService service;

  @BeforeEach
  void setUp() {
    orderRepository = mock(OrderRepository.class);
    materialTaskRepository = mock(MaterialTaskRepository.class);
    feedbackItemRepository = mock(FeedbackItemRepository.class);
    service = new KanbanService(orderRepository, materialTaskRepository, feedbackItemRepository,
        Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Shanghai");
    when(materialTaskRepository.countGroupedByOrderIds(anyCollection())).thenReturn(List.of());
    when(feedbackItemRepository.countGroupedByOrderIds(anyCollection(), any())).thenReturn(List.of());
  }

  @Test
  void buildsFourColumnsWithTotalsAndLightCards() {
    Order pending = card(1L, OrderStatus.PENDING_CONFIRM);
    Order producing = card(2L, OrderStatus.IN_PRODUCTION);
    Order completed = card(3L, OrderStatus.COMPLETED);
    Order cancelled = card(4L, OrderStatus.CANCELLED);
    when(orderRepository.findForKanban(eq(OrderStatus.PENDING_CONFIRM), isNull(), isNull(), isNull(),
        isNull(), any())).thenReturn(List.of(pending));
    when(orderRepository.findForKanban(eq(OrderStatus.IN_PRODUCTION), isNull(), isNull(), isNull(),
        isNull(), any())).thenReturn(List.of(producing));
    when(orderRepository.findForKanban(eq(OrderStatus.COMPLETED), isNull(), isNull(), isNull(),
        isNull(), any())).thenReturn(List.of(completed));
    when(orderRepository.findForKanban(eq(OrderStatus.CANCELLED), isNull(), isNull(), isNull(),
        isNull(), any())).thenReturn(List.of(cancelled));
    when(orderRepository.countForKanban(isNull(), isNull(), isNull(), isNull())).thenReturn(List.of(
        new Object[] {OrderStatus.PENDING_CONFIRM, 5L},
        new Object[] {OrderStatus.IN_PRODUCTION, 12L},
        new Object[] {OrderStatus.COMPLETED, 40L},
        new Object[] {OrderStatus.CANCELLED, 3L}));
    when(materialTaskRepository.countGroupedByOrderIds(anyCollection()))
        .thenReturn(List.<Object[]>of(new Object[] {2L, 3L}));
    when(feedbackItemRepository.countGroupedByOrderIds(anyCollection(), eq(FeedbackStatus.UNRESOLVED)))
        .thenReturn(List.<Object[]>of(new Object[] {2L, 1L}));

    KanbanResponse response = service.kanban(null, null, null, null);

    assertThat(response.columns()).extracting(KanbanColumnResponse::status)
        .containsExactly("PENDING_CONFIRM", "IN_PRODUCTION", "COMPLETED", "CANCELLED");
    assertThat(response.columns()).extracting(KanbanColumnResponse::total)
        .containsExactly(5L, 12L, 40L, 3L);

    KanbanColumnResponse producingColumn = response.columns().get(1);
    assertThat(producingColumn.orders()).hasSize(1);
    KanbanCardResponse card = producingColumn.orders().get(0);
    assertThat(card.id()).isEqualTo(2L);
    assertThat(card.taskCount()).isEqualTo(3L);
    assertThat(card.openFeedbackCount()).isEqualTo(1L);
    assertThat(response.columns().get(0).orders().get(0).taskCount()).isZero();

    // One query per column, one totals aggregate, and two batched counter aggregates.
    verify(orderRepository).countForKanban(isNull(), isNull(), isNull(), isNull());
    verify(materialTaskRepository).countGroupedByOrderIds(anyCollection());
    verify(feedbackItemRepository).countGroupedByOrderIds(anyCollection(), eq(FeedbackStatus.UNRESOLVED));
  }

  @Test
  void passesSanitizedFiltersAndTheInclusiveDateWindow() {
    when(orderRepository.findForKanban(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
    when(orderRepository.countForKanban(any(), any(), any(), any())).thenReturn(List.of());
    LocalDate from = LocalDate.of(2026, 1, 1);
    LocalDate to = LocalDate.of(2026, 1, 31);

    service.kanban(7L, "  <i>Acme</i>  ", from, to);

    verify(orderRepository).findForKanban(eq(OrderStatus.PENDING_CONFIRM), eq(7L), eq("Acme"),
        eq(LocalDateTime.of(2026, 1, 1, 0, 0)), eq(LocalDateTime.of(2026, 2, 1, 0, 0)),
        eq(PageRequest.of(0, 50)));
    verify(orderRepository).countForKanban(eq(7L), eq("Acme"),
        eq(LocalDateTime.of(2026, 1, 1, 0, 0)), eq(LocalDateTime.of(2026, 2, 1, 0, 0)));
  }

  private Order card(Long id, OrderStatus status) {
    User am = mock(User.class);
    when(am.getName()).thenReturn("Account Manager");
    Order order = mock(Order.class);
    when(order.getId()).thenReturn(id);
    when(order.getOrderNo()).thenReturn("ORD-" + id);
    when(order.getCustomerName()).thenReturn("Customer " + id);
    when(order.getStatus()).thenReturn(status);
    when(order.getAm()).thenReturn(am);
    when(order.getDeliveryDeadline()).thenReturn(LocalDate.of(2026, 12, 15));
    when(order.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 1, 9, 0));
    return order;
  }
}
