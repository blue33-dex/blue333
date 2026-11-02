package com.blue333.ordermgmt.service;

import cn.hutool.http.HtmlUtil;
import com.blue333.ordermgmt.aop.AuditedAction;
import com.blue333.ordermgmt.domain.Attachment;
import com.blue333.ordermgmt.domain.MaterialSpec;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.RequirementBaseline;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.AuditActionType;
import com.blue333.ordermgmt.domain.enums.AuditTargetType;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.dto.order.CreateOrderCommand;
import com.blue333.ordermgmt.dto.order.MaterialSpecCommand;
import com.blue333.ordermgmt.dto.order.RegisterAttachmentCommand;
import com.blue333.ordermgmt.dto.order.RejectOrderCommand;
import com.blue333.ordermgmt.event.OrderRejectedEvent;
import com.blue333.ordermgmt.event.OrderSubmittedEvent;
import com.blue333.ordermgmt.repository.AttachmentRepository;
import com.blue333.ordermgmt.repository.MaterialSpecRepository;
import com.blue333.ordermgmt.repository.OrderRepository;
import com.blue333.ordermgmt.repository.RequirementBaselineRepository;
import com.blue333.ordermgmt.util.IdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transactional order lifecycle core. Access control, controllers, upload endpoints, and task
 * assignment deliberately remain outside this service's scope.
 */
@Service
public class OrderService {
  private final OrderRepository orderRepository;
  private final MaterialSpecRepository materialSpecRepository;
  private final RequirementBaselineRepository requirementBaselineRepository;
  private final AttachmentRepository attachmentRepository;
  private final IdGenerator idGenerator;
  private final OrderStateMachine stateMachine;
  private final AttachmentFilePolicyValidator attachmentFilePolicyValidator;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;

  public OrderService(
      OrderRepository orderRepository,
      MaterialSpecRepository materialSpecRepository,
      RequirementBaselineRepository requirementBaselineRepository,
      AttachmentRepository attachmentRepository,
      IdGenerator idGenerator,
      OrderStateMachine stateMachine,
      AttachmentFilePolicyValidator attachmentFilePolicyValidator,
      ObjectMapper objectMapper,
      ApplicationEventPublisher eventPublisher) {
    this.orderRepository = orderRepository;
    this.materialSpecRepository = materialSpecRepository;
    this.requirementBaselineRepository = requirementBaselineRepository;
    this.attachmentRepository = attachmentRepository;
    this.idGenerator = idGenerator;
    this.stateMachine = stateMachine;
    this.attachmentFilePolicyValidator = attachmentFilePolicyValidator;
    this.objectMapper = objectMapper;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  @AuditedAction(action = AuditActionType.CREATE_ORDER, targetType = AuditTargetType.ORDER,
      targetId = "#result.id", afterState = "#result.status")
  public Order createOrder(CreateOrderCommand command, User am) {
    Objects.requireNonNull(command, "command must not be null");
    requireActor(am);
    validateCreateCommand(command);

    Order order = Order.create(idGenerator.generateOrderNo(), command.customerName().trim(),
        command.useScene().trim(), command.deliveryDeadline(), cleanOptional(command.remark()), am);
    Order savedOrder = orderRepository.save(order);
    List<MaterialSpec> materialSpecs = command.materialSpecs().stream()
        .map(spec -> MaterialSpec.create(savedOrder, spec.materialType(), spec.specSize().trim()))
        .toList();
    materialSpecRepository.saveAll(materialSpecs);
    requirementBaselineRepository.save(RequirementBaseline.initialSnapshot(
        savedOrder, am, serialize(materialSpecs)));
    return savedOrder;
  }

  /** Registers only metadata for an already-stored object. It never accepts or uploads file bytes. */
  @Transactional
  public Attachment registerStoredAttachment(Long orderId, RegisterAttachmentCommand command, User uploadedBy) {
    Objects.requireNonNull(command, "command must not be null");
    requireActor(uploadedBy);
    Order order = findOrderForUpdate(orderId);
    if (order.getStatus() != OrderStatus.PENDING_CONFIRM) {
      throw new InvalidStateTransitionException("Attachments can only be registered while order is pending confirmation");
    }
    attachmentFilePolicyValidator.validate(command.fileName(), command.fileSize());
    validateStoragePath(command.storagePath());
    return attachmentRepository.save(Attachment.register(order, command.fileName().trim(),
        command.fileSize(), command.storagePath().trim(), uploadedBy));
  }

  @Transactional
  @AuditedAction(action = AuditActionType.SUBMIT_ORDER, targetType = AuditTargetType.ORDER,
      targetId = "#orderId", afterState = "#result.status")
  public Order submitOrder(Long orderId) {
    Order order = findOrderForUpdate(orderId);
    validateSubmittable(order);
    stateMachine.transition(order, OrderStatus.PENDING_CONFIRM);
    Order savedOrder = orderRepository.save(order);
    publishAfterCommit(new OrderSubmittedEvent(List.of(), savedOrder.getId(), savedOrder.getOrderNo()));
    return savedOrder;
  }

  @Transactional
  @AuditedAction(action = AuditActionType.REJECT_ORDER, targetType = AuditTargetType.ORDER,
      targetId = "#command.orderId", afterState = "#result.status")
  public Order rejectOrder(RejectOrderCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    Order order = findOrderForUpdate(command.orderId());
    if (order.getStatus() != OrderStatus.PENDING_CONFIRM) {
      throw new InvalidStateTransitionException("Only pending-confirmation orders can be rejected");
    }
    if (command.reasonType() == null) {
      throw new IllegalArgumentException("A rejection reason type is required");
    }
    String remark = requireText(cleanOptional(command.remark()), "A rejection remark is required");
    stateMachine.transition(order, OrderStatus.PENDING_CONFIRM);
    Order savedOrder = orderRepository.save(order);
    publishAfterCommit(new OrderRejectedEvent(List.of(order.getAm().getId()), savedOrder.getId(), savedOrder.getOrderNo(),
        command.reasonType() + ": " + remark));
    return savedOrder;
  }

  /** Authorization of AM and BO cancellation callers is intentionally deferred to the RBAC task. */
  @Transactional
  @AuditedAction(action = AuditActionType.CANCEL_ORDER, targetType = AuditTargetType.ORDER,
      targetId = "#orderId", afterState = "#result.status")
  public Order cancelOrder(Long orderId) {
    Order order = findOrderForUpdate(orderId);
    stateMachine.transition(order, OrderStatus.CANCELLED);
    return orderRepository.save(order);
  }

  private Order findOrderForUpdate(Long orderId) {
    if (orderId == null || orderId <= 0) {
      throw new IllegalArgumentException("orderId must be a positive value");
    }
    return orderRepository.findByIdForUpdate(orderId)
        .orElseThrow(() -> new IllegalArgumentException("Order does not exist: " + orderId));
  }

  private void validateCreateCommand(CreateOrderCommand command) {
    requireText(command.customerName(), "Customer name is required");
    requireText(command.useScene(), "Use scene is required");
    if (command.deliveryDeadline() == null) {
      throw new IllegalArgumentException("Delivery deadline is required");
    }
    if (command.materialSpecs() == null || command.materialSpecs().isEmpty()) {
      throw new IllegalArgumentException("At least one material specification is required");
    }
    for (MaterialSpecCommand spec : command.materialSpecs()) {
      if (spec == null || spec.materialType() == null) {
        throw new IllegalArgumentException("Material type is required");
      }
      requireText(spec.specSize(), "Material specification size is required");
    }
  }

  private void validateSubmittable(Order order) {
    if (order.getStatus() != OrderStatus.PENDING_CONFIRM) {
      throw new InvalidStateTransitionException("Only pending-confirmation orders can be submitted");
    }
    requireText(order.getCustomerName(), "Customer name is required before submission");
    requireText(order.getUseScene(), "Use scene is required before submission");
    if (order.getDeliveryDeadline() == null) {
      throw new IllegalArgumentException("Delivery deadline is required before submission");
    }
    if (!requirementBaselineRepository.existsByOrderId(order.getId())
        || !materialSpecRepository.existsByOrderId(order.getId())) {
      throw new IllegalStateException("A requirement baseline is required before submission");
    }
    if (!attachmentRepository.existsByOrderId(order.getId())) {
      throw new IllegalStateException("At least one attachment is required before submission");
    }
  }

  private String serialize(List<MaterialSpec> materialSpecs) {
    List<MaterialSpecSnapshot> snapshot = materialSpecs.stream()
        .map(spec -> new MaterialSpecSnapshot(spec.getMaterialType().name(), spec.getSpecSize()))
        .toList();
    try {
      return objectMapper.writeValueAsString(snapshot);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not create requirement baseline snapshot", exception);
    }
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

  private void requireActor(User user) {
    if (user == null || user.getId() == null || user.getId() <= 0) {
      throw new IllegalArgumentException("A persisted acting user is required");
    }
  }

  private String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private String cleanOptional(String value) {
    return value == null ? null : HtmlUtil.cleanHtmlTag(value).trim();
  }

  private void validateStoragePath(String storagePath) {
    String path = requireText(storagePath, "Storage path is required");
    if (path.length() > 512 || path.startsWith("/") || path.endsWith("/") || path.contains("\\")
        || path.contains("..") || path.indexOf('?') >= 0 || path.indexOf('#') >= 0) {
      throw new IllegalArgumentException("Storage path must be a valid object key");
    }
  }

  private record MaterialSpecSnapshot(String materialType, String specSize) {
  }
}
