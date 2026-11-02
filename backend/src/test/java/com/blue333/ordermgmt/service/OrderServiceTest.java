package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blue333.ordermgmt.aop.AuditedAction;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.domain.enums.RejectReasonType;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.dto.order.RejectOrderCommand;
import com.blue333.ordermgmt.event.OrderRejectedEvent;
import com.blue333.ordermgmt.event.OrderSubmittedEvent;
import com.blue333.ordermgmt.repository.AttachmentRepository;
import com.blue333.ordermgmt.repository.MaterialSpecRepository;
import com.blue333.ordermgmt.repository.OrderRepository;
import com.blue333.ordermgmt.repository.RequirementBaselineRepository;
import com.blue333.ordermgmt.repository.UserRepository;
import com.blue333.ordermgmt.util.IdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

class OrderServiceTest {
  private static final Long ORDER_ID = 1L;
  private static final Long AM_ID = 5L;
  private static final Long BO_ID = 7L;
  private static final Instant NOW = Instant.parse("2026-11-02T08:30:00Z");

  private OrderRepository orderRepository;
  private MaterialSpecRepository materialSpecRepository;
  private RequirementBaselineRepository requirementBaselineRepository;
  private AttachmentRepository attachmentRepository;
  private UserRepository userRepository;
  private ApplicationEventPublisher eventPublisher;
  private OrderService service;
  private Order order;
  private User bo;

  @BeforeEach
  void setUp() {
    orderRepository = mock(OrderRepository.class);
    materialSpecRepository = mock(MaterialSpecRepository.class);
    requirementBaselineRepository = mock(RequirementBaselineRepository.class);
    attachmentRepository = mock(AttachmentRepository.class);
    userRepository = mock(UserRepository.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    service = new OrderService(orderRepository, materialSpecRepository, requirementBaselineRepository,
        attachmentRepository, mock(IdGenerator.class), new OrderStateMachine(),
        mock(AttachmentFilePolicyValidator.class), new ObjectMapper(), eventPublisher, userRepository,
        Clock.fixed(NOW, ZoneOffset.UTC));

    User am = mock(User.class);
    when(am.getId()).thenReturn(AM_ID);
    bo = mock(User.class);
    when(bo.getId()).thenReturn(BO_ID);
    order = Order.create("ORD-20261102-001", "Customer", "Launch event", LocalDate.of(2026, 12, 15),
        null, am);
    when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));
  }

  @Test
  void rejectionPersistsReasonSanitizedRemarkRejectingUserAndTime() {
    Order rejected = service.rejectOrder(new RejectOrderCommand(ORDER_ID, RejectReasonType.SPEC_UNCLEAR,
        "  <script>x</script><b>Size</b> missing  "), bo);

    assertThat(rejected.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRM);
    assertThat(rejected.getLastRejectReasonType()).isEqualTo(RejectReasonType.SPEC_UNCLEAR);
    assertThat(rejected.getLastRejectRemark()).doesNotContain("<", ">", "script").contains("Size missing");
    assertThat(rejected.getLastRejectedBy()).isSameAs(bo);
    assertThat(rejected.getLastRejectedAt()).isEqualTo(LocalDateTime.of(2026, 11, 2, 8, 30));

    ArgumentCaptor<OrderRejectedEvent> event = ArgumentCaptor.forClass(OrderRejectedEvent.class);
    verify(eventPublisher).publishEvent(event.capture());
    assertThat(event.getValue().recipientIds()).containsExactly(AM_ID);
  }

  @Test
  void rejectionRequiresActorReasonAndNonBlankRemark() {
    assertThatIllegalArgumentException().isThrownBy(() -> service.rejectOrder(
        new RejectOrderCommand(ORDER_ID, RejectReasonType.OTHER, "reason"), null));
    assertThatIllegalArgumentException().isThrownBy(() -> service.rejectOrder(
        new RejectOrderCommand(ORDER_ID, null, "reason"), bo));
    assertThatIllegalArgumentException().isThrownBy(() -> service.rejectOrder(
        new RejectOrderCommand(ORDER_ID, RejectReasonType.OTHER, "<p> </p>"), bo));
    assertThat(order.getLastRejectReasonType()).isNull();
    verify(orderRepository, never()).save(any());
  }

  @Test
  void rejectingAMissingOrderIsNotFound() {
    when(orderRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.rejectOrder(
        new RejectOrderCommand(404L, RejectReasonType.OTHER, "reason"), bo))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void rejectionAuditRemarkIsTheSanitizedRemark() throws NoSuchMethodException {
    AuditedAction audited = OrderService.class
        .getMethod("rejectOrder", RejectOrderCommand.class, User.class)
        .getAnnotation(AuditedAction.class);
    StandardEvaluationContext context = new StandardEvaluationContext();
    context.setVariable("command", new RejectOrderCommand(ORDER_ID, RejectReasonType.OTHER, " <i>Bad</i> scan "));

    Object remark = new SpelExpressionParser().parseExpression(audited.remark()).getValue(context);

    assertThat(remark).isEqualTo("Bad scan");
  }

  @Test
  void submissionNotifiesEveryEnabledBo() {
    when(requirementBaselineRepository.existsByOrderId(any())).thenReturn(true);
    when(materialSpecRepository.existsByOrderId(any())).thenReturn(true);
    when(attachmentRepository.existsByOrderId(any())).thenReturn(true);
    when(userRepository.findEnabledIdsByRoleType(RoleType.BO)).thenReturn(List.of(BO_ID, 8L));

    service.submitOrder(ORDER_ID);

    ArgumentCaptor<OrderSubmittedEvent> event = ArgumentCaptor.forClass(OrderSubmittedEvent.class);
    verify(eventPublisher).publishEvent(event.capture());
    assertThat(event.getValue().recipientIds()).containsExactly(BO_ID, 8L);
    assertThat(event.getValue().orderNo()).isEqualTo("ORD-20261102-001");
  }

  @Test
  void submittingAMissingOrderIsNotFound() {
    when(orderRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submitOrder(404L)).isInstanceOf(ResourceNotFoundException.class);
  }
}
