package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blue333.ordermgmt.domain.ExceptionRecord;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.ExceptionStatus;
import com.blue333.ordermgmt.domain.enums.ExceptionType;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.dto.exception.ExceptionResponses.ExceptionRecordResponse;
import com.blue333.ordermgmt.event.ExceptionRecordedEvent;
import com.blue333.ordermgmt.repository.ExceptionRecordRepository;
import com.blue333.ordermgmt.repository.OrderRepository;
import com.blue333.ordermgmt.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;

/** P4-T04: A-20 record exception, A-21 close exception, and the P4-T06 archive precondition. */
class ExceptionRecordServiceTest {
  private static final Long ORDER_ID = 1L;
  private static final Long BO_ID = 7L;
  private static final Long EXCEPTION_ID = 90L;
  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");

  private ExceptionRecordRepository exceptionRecordRepository;
  private OrderRepository orderRepository;
  private UserRepository userRepository;
  private ApplicationEventPublisher eventPublisher;
  private ExceptionRecordService service;

  private Order order;
  private User bo;

  @BeforeEach
  void setUp() {
    exceptionRecordRepository = mock(ExceptionRecordRepository.class);
    orderRepository = mock(OrderRepository.class);
    userRepository = mock(UserRepository.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    service = new ExceptionRecordService(exceptionRecordRepository, orderRepository, userRepository,
        eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

    bo = mock(User.class);
    when(bo.getId()).thenReturn(BO_ID);
    when(bo.getName()).thenReturn("Business Owner");
    when(userRepository.findEnabledIdsByRoleType(RoleType.BO)).thenReturn(List.of(BO_ID, 8L));
    when(exceptionRecordRepository.save(any(ExceptionRecord.class)))
        .thenAnswer(call -> call.getArgument(0));

    order = mock(Order.class);
    when(order.getId()).thenReturn(ORDER_ID);
    when(order.getOrderNo()).thenReturn("ORD-20261104-001");
    when(order.getStatus()).thenReturn(OrderStatus.IN_PRODUCTION);
    when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
  }

  @Test
  void recordingSanitizesTheDescriptionAndNotifiesEveryBo() {
    ExceptionRecordResponse response = service.record(ORDER_ID, ExceptionType.OTHER,
        "  <script>alert(1)</script><b>Print colour</b> mismatch  ", businessOwner());

    assertThat(response.exceptionType()).isEqualTo("OTHER");
    assertThat(response.status()).isEqualTo("PENDING_CONFIRM");
    assertThat(response.description()).doesNotContain("<", ">").contains("Print colour mismatch");
    assertThat(response.recorderId()).isEqualTo(BO_ID);
    assertThat(response.recorderName()).isEqualTo("Business Owner");
    assertThat(response.taskId()).isNull();

    ArgumentCaptor<ExceptionRecordedEvent> event = ArgumentCaptor.forClass(ExceptionRecordedEvent.class);
    verify(eventPublisher).publishEvent(event.capture());
    assertThat(event.getValue().recipientIds()).containsExactly(BO_ID, 8L);
    assertThat(event.getValue().orderNo()).isEqualTo("ORD-20261104-001");
    assertThat(event.getValue().description()).contains("Print colour").doesNotContain("<");
  }

  @Test
  void recordingRequiresBusinessOwnerTypeAndDescription() {
    assertThatIllegalArgumentException().isThrownBy(() -> service.record(ORDER_ID,
        ExceptionType.DELAY, "late", designer()));
    assertThatIllegalArgumentException().isThrownBy(() -> service.record(ORDER_ID, null,
        "late", businessOwner()));
    assertThatIllegalArgumentException().isThrownBy(() -> service.record(ORDER_ID,
        ExceptionType.OTHER, "<p> </p>", businessOwner()));
    verify(exceptionRecordRepository, never()).save(any());
  }

  @Test
  void recordingRejectsMissingAndCancelledOrders() {
    when(orderRepository.findById(404L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.record(404L, ExceptionType.OTHER, "issue", businessOwner()))
        .isInstanceOf(ResourceNotFoundException.class);

    when(order.getStatus()).thenReturn(OrderStatus.CANCELLED);
    assertThatThrownBy(() -> service.record(ORDER_ID, ExceptionType.OTHER, "issue", businessOwner()))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("Cancelled");
    verify(exceptionRecordRepository, never()).save(any());
  }

  @Test
  void closingKeepsTheSanitizedResolutionAndMovesToClosed() {
    ExceptionRecord open = ExceptionRecord.create(order, null, ExceptionType.DELAY,
        ExceptionStatus.PENDING_CONFIRM, "late", bo);
    when(exceptionRecordRepository.findByIdForUpdate(EXCEPTION_ID)).thenReturn(Optional.of(open));

    ExceptionRecordResponse response = service.close(EXCEPTION_ID,
        "  <i>Re-scheduled</i> with supplier  ", businessOwner());

    assertThat(response.status()).isEqualTo("CLOSED");
    assertThat(response.resolution()).contains("Re-scheduled").contains("with supplier")
        .doesNotContain("<");
    verify(exceptionRecordRepository).save(open);
  }

  @Test
  void closingRequiresAnOpenRecordAndBusinessOwner() {
    ExceptionRecord closed = ExceptionRecord.create(order, null, ExceptionType.DELAY,
        ExceptionStatus.CLOSED, "late", bo);
    when(exceptionRecordRepository.findByIdForUpdate(EXCEPTION_ID)).thenReturn(Optional.of(closed));

    assertThatThrownBy(() -> service.close(EXCEPTION_ID, "done", businessOwner()))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("already closed");
    assertThatIllegalArgumentException().isThrownBy(() -> service.close(EXCEPTION_ID, "done", designer()));

    when(exceptionRecordRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.close(999L, "done", businessOwner()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void listingForOrderUsesTheSystemLabelWhenThereIsNoRecorder() {
    ExceptionRecord automatic = ExceptionRecord.create(order, null, ExceptionType.DELAY,
        ExceptionStatus.PENDING_CONFIRM, "late", null);
    when(orderRepository.existsById(ORDER_ID)).thenReturn(true);
    when(exceptionRecordRepository.findAllByOrderId(ORDER_ID)).thenReturn(List.of(automatic));

    List<ExceptionRecordResponse> rows = service.listForOrder(ORDER_ID, businessOwner());

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).recorderName()).isEqualTo("系统");
    assertThat(rows.get(0).recorderId()).isNull();
  }

  @Test
  void listingWithoutOrderIsNotFound() {
    when(orderRepository.existsById(ORDER_ID)).thenReturn(false);

    assertThatThrownBy(() -> service.listForOrder(ORDER_ID, businessOwner()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void listingTheQueueAppliesStatusAndCursorFilters() {
    ExceptionRecord row = ExceptionRecord.create(order, null, ExceptionType.OTHER,
        ExceptionStatus.PENDING_CONFIRM, "issue", bo);
    when(exceptionRecordRepository.findPage(any(), any(), any())).thenReturn(List.of(row));

    CursorPage<ExceptionRecordResponse> page = service.list(ExceptionStatus.PENDING_CONFIRM, null, 2,
        businessOwner());

    assertThat(page.items()).hasSize(1);
    assertThat(page.hasNext()).isFalse();
    verify(exceptionRecordRepository).findPage(
        org.mockito.ArgumentMatchers.eq(ExceptionStatus.PENDING_CONFIRM), isNull(),
        org.mockito.ArgumentMatchers.eq(PageRequest.of(0, 3)));
  }

  @Test
  void archivePreconditionFailsWhileAnyExceptionIsOpen() {
    when(exceptionRecordRepository.countByOrderIdAndStatusNot(ORDER_ID, ExceptionStatus.CLOSED))
        .thenReturn(0L);
    service.requireAllClosed(ORDER_ID);

    when(exceptionRecordRepository.countByOrderIdAndStatusNot(ORDER_ID, ExceptionStatus.CLOSED))
        .thenReturn(2L);
    assertThatThrownBy(() -> service.requireAllClosed(ORDER_ID))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("2 still open");
  }

  @Test
  void responsesCarryUtcTimestampsFromTheRecord() {
    ExceptionRecord record = mock(ExceptionRecord.class);
    when(record.getId()).thenReturn(EXCEPTION_ID);
    when(record.getOrder()).thenReturn(order);
    when(record.getExceptionType()).thenReturn(ExceptionType.DELAY);
    when(record.getStatus()).thenReturn(ExceptionStatus.PENDING_CONFIRM);
    when(record.getDescription()).thenReturn("late");
    when(record.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 4, 8, 0));
    when(record.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 11, 4, 8, 5));

    ExceptionRecordResponse response = service.toResponse(record);

    assertThat(response.id()).isEqualTo(EXCEPTION_ID);
    assertThat(response.orderId()).isEqualTo(ORDER_ID);
    assertThat(response.orderNo()).isEqualTo("ORD-20261104-001");
    assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 11, 4, 8, 0));
    assertThat(response.updatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 4, 8, 5));
  }

  private CurrentUser businessOwner() {
    return new CurrentUser(bo, Set.of(RoleType.BO));
  }

  private CurrentUser designer() {
    User designer = mock(User.class);
    when(designer.getId()).thenReturn(3L);
    return new CurrentUser(designer, Set.of(RoleType.DS));
  }
}
