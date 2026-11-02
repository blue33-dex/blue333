package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blue333.ordermgmt.domain.Attachment;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.repository.AttachmentRepository;
import com.blue333.ordermgmt.repository.MaterialTaskRepository;
import com.blue333.ordermgmt.repository.OrderRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class AccessControlServiceTest {
  private static final long ORDER_ID = 10L;
  private static final long TASK_ID = 20L;
  private static final long ATTACHMENT_ID = 30L;
  private static final long OWNER_AM_ID = 1L;
  private static final long OTHER_AM_ID = 2L;
  private static final long BO_ID = 3L;
  private static final long DS_ID = 4L;
  private static final long OTHER_DS_ID = 5L;

  private OrderRepository orderRepository;
  private MaterialTaskRepository materialTaskRepository;
  private AttachmentRepository attachmentRepository;
  private AccessControlService service;
  private Order order;

  @BeforeEach
  void setUp() {
    orderRepository = mock(OrderRepository.class);
    materialTaskRepository = mock(MaterialTaskRepository.class);
    attachmentRepository = mock(AttachmentRepository.class);
    service = new AccessControlService(orderRepository, materialTaskRepository, attachmentRepository);

    order = mock(Order.class);
    when(order.getId()).thenReturn(ORDER_ID);
    User owner = user(OWNER_AM_ID);
    when(order.getAm()).thenReturn(owner);
    when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
    when(orderRepository.existsTaskAssignedTo(ORDER_ID, DS_ID)).thenReturn(true);
    when(orderRepository.existsTaskAssignedTo(ORDER_ID, OTHER_DS_ID)).thenReturn(false);

    MaterialTask task = mock(MaterialTask.class);
    User assignee = user(DS_ID);
    when(task.getAssignee()).thenReturn(assignee);
    when(task.getOrder()).thenReturn(order);
    when(materialTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

    Attachment attachment = mock(Attachment.class);
    when(attachment.getOrder()).thenReturn(order);
    when(attachmentRepository.findById(ATTACHMENT_ID)).thenReturn(Optional.of(attachment));
  }

  @Test
  void boSeesEveryOrderTaskAndAttachment() {
    CurrentUser bo = current(BO_ID, RoleType.BO);

    assertThat(service.findVisibleOrder(ORDER_ID, bo)).isSameAs(order);
    assertThat(service.findVisibleTask(TASK_ID, bo)).isNotNull();
    assertThat(service.findVisibleAttachment(ATTACHMENT_ID, bo)).isNotNull();
  }

  @Test
  void amSeesOnlyOwnOrdersAndGetsNotFoundForOthers() {
    assertThat(service.findVisibleOrder(ORDER_ID, current(OWNER_AM_ID, RoleType.AM))).isSameAs(order);

    assertThatThrownBy(() -> service.findVisibleOrder(ORDER_ID, current(OTHER_AM_ID, RoleType.AM)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void invisibleOrderIsIndistinguishableFromMissingOrder() {
    when(orderRepository.findById(99L)).thenReturn(Optional.empty());
    CurrentUser otherAm = current(OTHER_AM_ID, RoleType.AM);

    Throwable invisible = catchThrowable(() -> service.findVisibleOrder(ORDER_ID, otherAm));
    Throwable missing = catchThrowable(() -> service.findVisibleOrder(99L, otherAm));

    assertThat(invisible).isInstanceOf(ResourceNotFoundException.class);
    assertThat(missing).isInstanceOf(ResourceNotFoundException.class);
    assertThat(invisible.getMessage().replace(String.valueOf(ORDER_ID), "?"))
        .isEqualTo(missing.getMessage().replace("99", "?"));
  }

  @Test
  void nonPositiveIdsAreNotFound() {
    CurrentUser bo = current(BO_ID, RoleType.BO);
    assertThatThrownBy(() -> service.findVisibleOrder(null, bo)).isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.findVisibleTask(0L, bo)).isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.findVisibleAttachment(-1L, bo))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void dsSeesOnlyOrdersContainingATaskAssignedToThem() {
    assertThat(service.findVisibleOrder(ORDER_ID, current(DS_ID, RoleType.DS))).isSameAs(order);

    assertThatThrownBy(() -> service.findVisibleOrder(ORDER_ID, current(OTHER_DS_ID, RoleType.DS)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void ownedOrderLookupRequiresAmOwnership() {
    assertThat(service.findOwnedOrderForAm(ORDER_ID, current(OWNER_AM_ID, RoleType.AM))).isSameAs(order);

    assertThatThrownBy(() -> service.findOwnedOrderForAm(ORDER_ID, current(OTHER_AM_ID, RoleType.AM)))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.findOwnedOrderForAm(ORDER_ID, current(BO_ID, RoleType.BO)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void dsSeesOnlyTasksAssignedToThem() {
    assertThat(service.findVisibleTask(TASK_ID, current(DS_ID, RoleType.DS))).isNotNull();

    assertThatThrownBy(() -> service.findVisibleTask(TASK_ID, current(OTHER_DS_ID, RoleType.DS)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void amIsForbiddenFromTaskDetailsWithoutRevealingExistence() {
    assertThatThrownBy(() -> service.findVisibleTask(TASK_ID, current(OWNER_AM_ID, RoleType.AM)))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.findVisibleTask(12345L, current(OWNER_AM_ID, RoleType.AM)))
        .isInstanceOf(AccessDeniedException.class);
    verify(materialTaskRepository, never()).findById(any());
  }

  @Test
  void attachmentVisibilityFollowsItsOrder() {
    assertThat(service.findVisibleAttachment(ATTACHMENT_ID, current(OWNER_AM_ID, RoleType.AM))).isNotNull();
    assertThat(service.findVisibleAttachment(ATTACHMENT_ID, current(DS_ID, RoleType.DS))).isNotNull();

    assertThatThrownBy(() -> service.findVisibleAttachment(ATTACHMENT_ID, current(OTHER_AM_ID, RoleType.AM)))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.findVisibleAttachment(ATTACHMENT_ID, current(OTHER_DS_ID, RoleType.DS)))
        .isInstanceOf(ResourceNotFoundException.class);
    when(attachmentRepository.findById(77L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.findVisibleAttachment(77L, current(BO_ID, RoleType.BO)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  private static Throwable catchThrowable(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
    return org.assertj.core.api.Assertions.catchThrowable(callable);
  }

  private static CurrentUser current(long id, RoleType... roles) {
    return new CurrentUser(user(id), Set.of(roles));
  }

  private static User user(long id) {
    User user = mock(User.class);
    when(user.getId()).thenReturn(id);
    return user;
  }
}
