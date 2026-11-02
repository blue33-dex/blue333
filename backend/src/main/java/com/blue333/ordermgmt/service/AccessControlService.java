package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.domain.Attachment;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.repository.AttachmentRepository;
import com.blue333.ordermgmt.repository.MaterialTaskRepository;
import com.blue333.ordermgmt.repository.OrderRepository;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central data-visibility rules (BR-13).
 *
 * <ul>
 *   <li>BO sees every order, task, and attachment.</li>
 *   <li>AM sees only orders whose am_id is the AM, and their attachments; AM never sees task details.</li>
 *   <li>DS sees tasks assigned to the DS, and orders (and their attachments) that contain such a task.</li>
 * </ul>
 *
 * <p>A resource that exists but is outside the caller's scope raises the same
 * {@link ResourceNotFoundException} as a missing one, so existence is never disclosed. A caller whose
 * roles can never access a resource type gets {@link AccessDeniedException} before any lookup, which
 * likewise reveals nothing about specific rows. Users holding several roles get the union of scopes.</p>
 */
@Service
public class AccessControlService {
  private final OrderRepository orderRepository;
  private final MaterialTaskRepository materialTaskRepository;
  private final AttachmentRepository attachmentRepository;

  public AccessControlService(
      OrderRepository orderRepository,
      MaterialTaskRepository materialTaskRepository,
      AttachmentRepository attachmentRepository) {
    this.orderRepository = orderRepository;
    this.materialTaskRepository = materialTaskRepository;
    this.attachmentRepository = attachmentRepository;
  }

  @Transactional(readOnly = true)
  public Order findVisibleOrder(Long orderId, CurrentUser currentUser) {
    requireCurrentUser(currentUser);
    Order order = loadOrder(orderId);
    if (!isOrderVisible(order, currentUser)) {
      throw orderNotFound(orderId);
    }
    return order;
  }

  /** For AM-only order operations: the caller must be an AM and the owner of the order. */
  @Transactional(readOnly = true)
  public Order findOwnedOrderForAm(Long orderId, CurrentUser currentUser) {
    requireCurrentUser(currentUser);
    if (!currentUser.hasRole(RoleType.AM)) {
      throw new AccessDeniedException("Only an account manager can perform this operation");
    }
    Order order = loadOrder(orderId);
    if (!isOwnedBy(order, currentUser)) {
      throw orderNotFound(orderId);
    }
    return order;
  }

  /** PRD 2.2.2: AM may not view task details, so a caller without BO or DS gets 403. */
  @Transactional(readOnly = true)
  public MaterialTask findVisibleTask(Long taskId, CurrentUser currentUser) {
    requireCurrentUser(currentUser);
    if (!currentUser.hasRole(RoleType.BO) && !currentUser.hasRole(RoleType.DS)) {
      throw new AccessDeniedException("Task details are not available for this role");
    }
    if (!isPositive(taskId)) {
      throw taskNotFound(taskId);
    }
    MaterialTask task = materialTaskRepository.findById(taskId).orElseThrow(() -> taskNotFound(taskId));
    boolean visible = currentUser.hasRole(RoleType.BO)
        || (currentUser.hasRole(RoleType.DS) && sameUser(task.getAssignee(), currentUser));
    if (!visible) {
      throw taskNotFound(taskId);
    }
    return task;
  }

  /** An attachment is visible exactly when its order is visible. */
  @Transactional(readOnly = true)
  public Attachment findVisibleAttachment(Long attachmentId, CurrentUser currentUser) {
    requireCurrentUser(currentUser);
    if (!isPositive(attachmentId)) {
      throw attachmentNotFound(attachmentId);
    }
    Attachment attachment = attachmentRepository.findById(attachmentId)
        .orElseThrow(() -> attachmentNotFound(attachmentId));
    if (!isOrderVisible(attachment.getOrder(), currentUser)) {
      throw attachmentNotFound(attachmentId);
    }
    return attachment;
  }

  private boolean isOrderVisible(Order order, CurrentUser currentUser) {
    if (currentUser.hasRole(RoleType.BO)) {
      return true;
    }
    if (currentUser.hasRole(RoleType.AM) && isOwnedBy(order, currentUser)) {
      return true;
    }
    return currentUser.hasRole(RoleType.DS)
        && orderRepository.existsTaskAssignedTo(order.getId(), currentUser.id());
  }

  private boolean isOwnedBy(Order order, CurrentUser currentUser) {
    return sameUser(order.getAm(), currentUser);
  }

  private boolean sameUser(User user, CurrentUser currentUser) {
    return user != null && user.getId() != null && Objects.equals(user.getId(), currentUser.id());
  }

  private Order loadOrder(Long orderId) {
    if (!isPositive(orderId)) {
      throw orderNotFound(orderId);
    }
    return orderRepository.findById(orderId).orElseThrow(() -> orderNotFound(orderId));
  }

  private void requireCurrentUser(CurrentUser currentUser) {
    if (currentUser == null || currentUser.id() == null) {
      throw new AccessDeniedException("An authenticated user is required");
    }
  }

  private boolean isPositive(Long id) {
    return id != null && id > 0;
  }

  private ResourceNotFoundException orderNotFound(Long orderId) {
    return new ResourceNotFoundException("Order does not exist: " + orderId);
  }

  private ResourceNotFoundException taskNotFound(Long taskId) {
    return new ResourceNotFoundException("Material task does not exist: " + taskId);
  }

  private ResourceNotFoundException attachmentNotFound(Long attachmentId) {
    return new ResourceNotFoundException("Attachment does not exist: " + attachmentId);
  }
}
