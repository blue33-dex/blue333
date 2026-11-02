package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blue333.ordermgmt.domain.Assignment;
import com.blue333.ordermgmt.domain.MaterialSpec;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.Role;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.UserRole;
import com.blue333.ordermgmt.domain.enums.MaterialTaskStatus;
import com.blue333.ordermgmt.domain.enums.MaterialType;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.domain.enums.Priority;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.event.TaskAssignedEvent;
import com.blue333.ordermgmt.repository.AssignmentRepository;
import com.blue333.ordermgmt.repository.MaterialSpecRepository;
import com.blue333.ordermgmt.repository.MaterialTaskRepository;
import com.blue333.ordermgmt.repository.OrderRepository;
import com.blue333.ordermgmt.repository.UserRepository;
import com.blue333.ordermgmt.repository.UserRoleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class MaterialTaskServiceTest {
  private static final Long ORDER_ID = 1L;
  private static final Long BO_ID = 2L;
  private static final Long DS_ID = 3L;
  private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 12, 1, 18, 0);

  private OrderRepository orderRepository;
  private MaterialSpecRepository materialSpecRepository;
  private MaterialTaskRepository materialTaskRepository;
  private AssignmentRepository assignmentRepository;
  private UserRepository userRepository;
  private UserRoleRepository userRoleRepository;
  private ApplicationEventPublisher eventPublisher;
  private MaterialTaskService service;

  private User bo;
  private User ds;
  private Order order;

  @BeforeEach
  void setUp() {
    orderRepository = mock(OrderRepository.class);
    materialSpecRepository = mock(MaterialSpecRepository.class);
    materialTaskRepository = mock(MaterialTaskRepository.class);
    assignmentRepository = mock(AssignmentRepository.class);
    userRepository = mock(UserRepository.class);
    userRoleRepository = mock(UserRoleRepository.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    service = new MaterialTaskService(orderRepository, materialSpecRepository, materialTaskRepository,
        assignmentRepository, userRepository, userRoleRepository, new OrderStateMachine(),
        new MaterialTaskStateMachine(), eventPublisher);

    bo = enabledUser(BO_ID, RoleType.BO);
    ds = enabledUser(DS_ID, RoleType.DS);
    order = Order.create("ORD-20261102-001", "Customer", "Launch event", LocalDate.of(2026, 12, 15),
        null, mock(User.class));
    when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
    when(materialTaskRepository.save(any(MaterialTask.class))).thenAnswer(call -> call.getArgument(0));
  }

  @Test
  void createsOneDesignTaskAndOneInitialAssignmentPerSpecificationAndStartsProduction() {
    MaterialSpec poster = MaterialSpec.create(order, MaterialType.POSTER, "600x900mm");
    MaterialSpec banner = MaterialSpec.create(order, MaterialType.BANNER, "800x2000mm");
    when(materialSpecRepository.findAllByOrderId(any())).thenReturn(List.of(poster, banner));

    List<MaterialTask> tasks = service.assignOrder(ORDER_ID, bo, ds, DEADLINE, Priority.HIGH, "<b>urgent</b>");

    assertThat(tasks).hasSize(2);
    assertThat(tasks).allSatisfy(task -> {
      assertThat(task.getStatus()).isEqualTo(MaterialTaskStatus.PENDING_DESIGN);
      assertThat(task.getAssignee()).isSameAs(ds);
      assertThat(task.getTaskDeadline()).isEqualTo(DEADLINE);
      assertThat(task.getPriority()).isEqualTo(Priority.HIGH);
    });
    assertThat(tasks).extracting(MaterialTask::getMaterialSpec).containsExactly(poster, banner);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PRODUCTION);

    ArgumentCaptor<Assignment> assignments = ArgumentCaptor.forClass(Assignment.class);
    verify(assignmentRepository, times(2)).save(assignments.capture());
    assertThat(assignments.getAllValues()).allSatisfy(assignment -> {
      assertThat(assignment.getAssignmentType()).isEqualTo(Assignment.INITIAL_ASSIGNMENT_TYPE);
      assertThat(assignment.getAssigner()).isSameAs(bo);
      assertThat(assignment.getAssignee()).isSameAs(ds);
      assertThat(assignment.getRemark()).doesNotContain("<", ">");
    });

    ArgumentCaptor<TaskAssignedEvent> events = ArgumentCaptor.forClass(TaskAssignedEvent.class);
    verify(eventPublisher, times(2)).publishEvent(events.capture());
    assertThat(events.getAllValues()).allSatisfy(event -> {
      assertThat(event.recipientIds()).containsExactly(DS_ID);
      assertThat(event.orderNo()).isEqualTo("ORD-20261102-001");
      assertThat(event.deadline()).isEqualTo(DEADLINE);
    });
  }

  @Test
  void rejectsAssigneeWhoIsNotADesigner() {
    User anotherBo = enabledUser(4L, RoleType.BO);
    when(materialSpecRepository.findAllByOrderId(any()))
        .thenReturn(List.of(MaterialSpec.create(order, MaterialType.POSTER, "A3")));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.assignOrder(ORDER_ID, bo, anotherBo, DEADLINE, Priority.LOW, null));
    verify(materialTaskRepository, never()).save(any());
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRM);
  }

  @Test
  void requiresDeadlineAndPriority() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.assignOrder(ORDER_ID, bo, ds, null, Priority.LOW, null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.assignOrder(ORDER_ID, bo, ds, DEADLINE, null, null));
    verify(materialTaskRepository, never()).save(any());
  }

  @Test
  void rejectsAssigningAnOrderThatIsAlreadyInProduction() {
    order.changeStatus(OrderStatus.IN_PRODUCTION);

    assertThatThrownBy(() -> service.assignOrder(ORDER_ID, bo, ds, DEADLINE, Priority.LOW, null))
        .isInstanceOf(InvalidStateTransitionException.class);
    verify(materialTaskRepository, never()).save(any());
  }

  @Test
  void rejectsOrderWithoutMaterialSpecifications() {
    when(materialSpecRepository.findAllByOrderId(any())).thenReturn(List.of());

    assertThatThrownBy(() -> service.assignOrder(ORDER_ID, bo, ds, DEADLINE, Priority.LOW, null))
        .isInstanceOf(IllegalStateException.class);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRM);
  }

  @Test
  void cancelsTaskOnlyWithAReasonAndRejectsTerminalTasks() {
    MaterialTask task = MaterialTask.create(order, MaterialSpec.create(order, MaterialType.POSTER, "A3"),
        ds, DEADLINE, Priority.MEDIUM);
    when(materialTaskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));

    assertThatIllegalArgumentException().isThrownBy(() -> service.cancelTask(10L, "   "));
    assertThat(task.getStatus()).isEqualTo(MaterialTaskStatus.PENDING_DESIGN);

    assertThat(service.cancelTask(10L, "Customer withdrew this item").getStatus())
        .isEqualTo(MaterialTaskStatus.CANCELLED);
    assertThatThrownBy(() -> service.cancelTask(10L, "Second cancellation"))
        .isInstanceOf(InvalidStateTransitionException.class);
  }

  private User enabledUser(Long id, RoleType roleType) {
    User user = mock(User.class);
    when(user.getId()).thenReturn(id);
    when(user.getEnabled()).thenReturn(true);
    when(userRepository.findById(id)).thenReturn(Optional.of(user));

    Role role = mock(Role.class);
    when(role.getRoleType()).thenReturn(roleType);
    UserRole userRole = mock(UserRole.class);
    when(userRole.getRole()).thenReturn(role);
    when(userRoleRepository.findByIdUserId(id)).thenReturn(List.of(userRole));
    return user;
  }
}
