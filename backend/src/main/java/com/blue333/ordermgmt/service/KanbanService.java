package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.enums.FeedbackStatus;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.dto.kanban.KanbanResponses.KanbanCardResponse;
import com.blue333.ordermgmt.dto.kanban.KanbanResponses.KanbanColumnResponse;
import com.blue333.ordermgmt.dto.kanban.KanbanResponses.KanbanResponse;
import com.blue333.ordermgmt.repository.FeedbackItemRepository;
import com.blue333.ordermgmt.repository.MaterialTaskRepository;
import com.blue333.ordermgmt.repository.OrderRepository;
import com.blue333.ordermgmt.util.XSSCleaner;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A-25 kanban board (BO only, NFR-P5): fixed number of queries regardless of order count — one per
 * status column, one aggregate for the column totals, and two batched aggregates for the card
 * counters. Cards are capped so an old board cannot blow up the payload.
 */
@Service
public class KanbanService {
  public static final List<OrderStatus> COLUMNS = List.of(OrderStatus.PENDING_CONFIRM,
      OrderStatus.IN_PRODUCTION, OrderStatus.COMPLETED, OrderStatus.CANCELLED);
  private static final int MAX_CARDS_PER_COLUMN = 50;

  private final OrderRepository orderRepository;
  private final MaterialTaskRepository materialTaskRepository;
  private final FeedbackItemRepository feedbackItemRepository;
  private final Clock clock;
  private final ZoneId businessZone;

  public KanbanService(OrderRepository orderRepository, MaterialTaskRepository materialTaskRepository,
      FeedbackItemRepository feedbackItemRepository, Clock clock,
      @Value("${app.business-time-zone:Asia/Shanghai}") String businessTimeZone) {
    this.orderRepository = orderRepository;
    this.materialTaskRepository = materialTaskRepository;
    this.feedbackItemRepository = feedbackItemRepository;
    this.clock = clock;
    this.businessZone = ZoneId.of(businessTimeZone);
  }

  @Transactional(readOnly = true)
  public KanbanResponse kanban(Long amId, String customer, LocalDate from, LocalDate to) {
    String cleanCustomer = cleanCustomer(customer);
    LocalDateTime fromAt = from == null ? null : from.atStartOfDay();
    LocalDateTime toAt = to == null ? null : to.plusDays(1).atStartOfDay();
    Map<OrderStatus, Long> totals = loadTotals(amId, cleanCustomer, fromAt, toAt);

    List<List<Order>> rowsByColumn = new ArrayList<>();
    List<Long> visibleOrderIds = new ArrayList<>();
    for (OrderStatus status : COLUMNS) {
      List<Order> rows = orderRepository.findForKanban(status, amId, cleanCustomer, fromAt, toAt,
          PageRequest.of(0, MAX_CARDS_PER_COLUMN));
      rowsByColumn.add(rows);
      rows.forEach(order -> visibleOrderIds.add(order.getId()));
    }
    Map<Long, Long> taskCounts = taskCounts(visibleOrderIds);
    Map<Long, Long> feedbackCounts = openFeedbackCounts(visibleOrderIds);

    List<KanbanColumnResponse> columns = new ArrayList<>();
    for (int index = 0; index < COLUMNS.size(); index++) {
      OrderStatus status = COLUMNS.get(index);
      List<KanbanCardResponse> cards = rowsByColumn.get(index).stream()
          .map(order -> card(order, taskCounts, feedbackCounts)).toList();
      columns.add(new KanbanColumnResponse(status.name(), totals.getOrDefault(status, 0L), cards));
    }
    return new KanbanResponse(LocalDateTime.now(clock.withZone(businessZone)), columns);
  }

  private KanbanCardResponse card(Order order, Map<Long, Long> taskCounts,
      Map<Long, Long> feedbackCounts) {
    return new KanbanCardResponse(order.getId(), order.getOrderNo(), order.getCustomerName(),
        order.getStatus().name(), order.getAm().getName(), order.getDeliveryDeadline(),
        order.getCreatedAt(), taskCounts.getOrDefault(order.getId(), 0L),
        feedbackCounts.getOrDefault(order.getId(), 0L));
  }

  private Map<OrderStatus, Long> loadTotals(Long amId, String customer, LocalDateTime from,
      LocalDateTime to) {
    Map<OrderStatus, Long> totals = new HashMap<>();
    for (Object[] row : orderRepository.countForKanban(amId, customer, from, to)) {
      totals.put((OrderStatus) row[0], (Long) row[1]);
    }
    return totals;
  }

  private Map<Long, Long> taskCounts(Collection<Long> orderIds) {
    Map<Long, Long> counts = new HashMap<>();
    if (orderIds.isEmpty()) {
      return counts;
    }
    for (Object[] row : materialTaskRepository.countGroupedByOrderIds(orderIds)) {
      counts.put((Long) row[0], (Long) row[1]);
    }
    return counts;
  }

  private Map<Long, Long> openFeedbackCounts(Collection<Long> orderIds) {
    Map<Long, Long> counts = new HashMap<>();
    if (orderIds.isEmpty()) {
      return counts;
    }
    for (Object[] row : feedbackItemRepository.countGroupedByOrderIds(orderIds, FeedbackStatus.UNRESOLVED)) {
      counts.put((Long) row[0], (Long) row[1]);
    }
    return counts;
  }

  private String cleanCustomer(String customer) {
    if (customer == null || customer.isBlank()) {
      return null;
    }
    String clean = XSSCleaner.clean(customer).trim();
    return clean.length() > 100 ? clean.substring(0, 100) : clean;
  }
}
