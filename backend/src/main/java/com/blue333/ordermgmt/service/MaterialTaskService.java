package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.aop.AuditedAction;
import com.blue333.ordermgmt.domain.Assignment;
import com.blue333.ordermgmt.domain.MaterialSpec;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.AuditActionType;
import com.blue333.ordermgmt.domain.enums.AuditTargetType;
import com.blue333.ordermgmt.domain.enums.MaterialTaskStatus;
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
import com.blue333.ordermgmt.util.XSSCleaner;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Transactional core for initial per-material task assignment and task cancellation. */
@Service
public class MaterialTaskService {
  private final OrderRepository orderRepository;
  private final MaterialSpecRepository materialSpecRepository;
  private final MaterialTaskRepository materialTaskRepository;
  private final AssignmentRepository assignmentRepository;
  private final UserRepository userRepository;
  private final UserRoleRepository userRoleRepository;
  private final OrderStateMachine orderStateMachine;
  private final MaterialTaskStateMachine taskStateMachine;
  private final ApplicationEventPublisher eventPublisher;

  public MaterialTaskService(
      OrderRepository orderRepository,
      MaterialSpecRepository materialSpecRepository,
      MaterialTaskRepository materialTaskRepository,
      AssignmentRepository assignmentRepository,
      UserRepository userRepository,
      UserRoleRepository userRoleRepository,
      OrderStateMachine orderStateMachine,
      MaterialTaskStateMachine taskStateMachine,
      ApplicationEventPublisher eventPublisher) {
    this.orderRepository = orderRepository;
    this.materialSpecRepository = materialSpecRepository;
    this.materialTaskRepository = materialTaskRepository;
    this.assignmentRepository = assignmentRepository;
    this.userRepository = userRepository;
    this.userRoleRepository = userRoleRepository;
    this.orderStateMachine = orderStateMachine;
    this.taskStateMachine = taskStateMachine;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Assigns every material specification on a pending-confirmation order to one assignee.
   * Assignment history is task-scoped, so this creates one task and one INITIAL history row per spec.
   */
  @Transactional
  @AuditedAction(action = AuditActionType.ASSIGN_TASK, targetType = AuditTargetType.ORDER,
      targetId = "#orderId", beforeState = "'PENDING_CONFIRM'", afterState = "'IN_PRODUCTION'")
  public List<MaterialTask> assignOrder(
      Long orderId,
      User assigner,
      User assignee,
      LocalDateTime taskDeadline,
      Priority priority,
      String remark) {
    Order order = findOrderForUpdate(orderId);
    if (order.getStatus() != OrderStatus.PENDING_CONFIRM) {
      throw new InvalidStateTransitionException("Only pending-confirmation orders can be assigned");
    }
    User persistedAssigner = requirePersistedEnabledUser(assigner, "assigner");
    User persistedAssignee = requirePersistedEnabledUser(assignee, "assignee");
    requireRole(persistedAssignee, RoleType.DS, "The assignee must be a designer (DS)");
    validateAssignment(taskDeadline, priority);

    List<MaterialSpec> materialSpecs = materialSpecRepository.findAllByOrderId(order.getId());
    if (materialSpecs.isEmpty()) {
      throw new IllegalStateException("At least one material specification is required before assignment");
    }
    for (MaterialSpec materialSpec : materialSpecs) {
      if (materialTaskRepository.existsByMaterialSpecId(materialSpec.getId())) {
        throw new IllegalStateException("Material task already exists for material specification: "
            + materialSpec.getId());
      }
    }

    String cleanedRemark = cleanOptional(remark);
    List<MaterialTask> tasks = new ArrayList<>();
    for (MaterialSpec materialSpec : materialSpecs) {
      MaterialTask savedTask = materialTaskRepository.save(MaterialTask.create(order, materialSpec,
          persistedAssignee, taskDeadline, priority));
      assignmentRepository.save(Assignment.initial(savedTask, persistedAssigner, persistedAssignee,
          taskDeadline, priority, cleanedRemark));
      tasks.add(savedTask);
      publishAfterCommit(new TaskAssignedEvent(List.of(persistedAssignee.getId()), savedTask.getId(),
          order.getOrderNo(), taskDeadline));
    }
    orderStateMachine.transition(order, OrderStatus.IN_PRODUCTION);
    orderRepository.save(order);
    return List.copyOf(tasks);
  }

  @Transactional
  @AuditedAction(action = AuditActionType.CANCEL_TASK, targetType = AuditTargetType.MATERIAL_TASK,
      targetId = "#taskId", afterState = "#result.status",
      remark = "T(com.blue333.ordermgmt.util.XSSCleaner).clean(#reason)")
  public MaterialTask cancelTask(Long taskId, String reason) {
    requireText(cleanOptional(reason), "A task cancellation reason is required");
    MaterialTask task = findTaskForUpdate(taskId);
    taskStateMachine.transition(task, MaterialTaskStatus.CANCELLED);
    return materialTaskRepository.save(task);
  }

  private Order findOrderForUpdate(Long orderId) {
    requirePositiveId(orderId, "orderId");
    return orderRepository.findByIdForUpdate(orderId)
        .orElseThrow(() -> new IllegalArgumentException("Order does not exist: " + orderId));
  }

  private MaterialTask findTaskForUpdate(Long taskId) {
    requirePositiveId(taskId, "taskId");
    return materialTaskRepository.findByIdForUpdate(taskId)
        .orElseThrow(() -> new IllegalArgumentException("Material task does not exist: " + taskId));
  }

  private User requirePersistedEnabledUser(User user, String label) {
    if (user == null || user.getId() == null || user.getId() <= 0) {
      throw new IllegalArgumentException("A persisted " + label + " is required");
    }
    User persistedUser = userRepository.findById(user.getId())
        .orElseThrow(() -> new IllegalArgumentException("User does not exist: " + user.getId()));
    if (!Boolean.TRUE.equals(persistedUser.getEnabled())) {
      throw new IllegalArgumentException("The " + label + " must be enabled");
    }
    return persistedUser;
  }

  private void requireRole(User user, RoleType roleType, String message) {
    boolean hasRole = userRoleRepository.findByIdUserId(user.getId()).stream()
        .anyMatch(userRole -> userRole.getRole().getRoleType() == roleType);
    if (!hasRole) {
      throw new IllegalArgumentException(message);
    }
  }

  private void validateAssignment(LocalDateTime taskDeadline, Priority priority) {
    if (taskDeadline == null) {
      throw new IllegalArgumentException("Task deadline is required");
    }
    if (priority == null) {
      throw new IllegalArgumentException("Task priority is required");
    }
  }

  private void requirePositiveId(Long id, String label) {
    if (id == null || id <= 0) {
      throw new IllegalArgumentException(label + " must be a positive value");
    }
  }

  private String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private String cleanOptional(String value) {
    return value == null ? null : XSSCleaner.clean(value).trim();
  }

  private void publishAfterCommit(Object event) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      eventPublisher.publishEvent(event);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        eventPublisher.publishEvent(event);
      }
    });
  }
}
