package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.domain.Notification;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.NotificationType;
import com.blue333.ordermgmt.event.OrderRejectedEvent;
import com.blue333.ordermgmt.event.OrderSubmittedEvent;
import com.blue333.ordermgmt.event.TaskAssignedEvent;
import com.blue333.ordermgmt.repository.NotificationRepository;
import com.blue333.ordermgmt.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes in-application notifications as isolated, asynchronous event side effects. */
@Service
public class NotificationService {
  private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);
  private static final String ORDER_RESOURCE_TYPE = "ORDER";
  private static final String MATERIAL_TASK_RESOURCE_TYPE = "MATERIAL_TASK";
  private static final DateTimeFormatter DEADLINE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;

  public NotificationService(
      NotificationRepository notificationRepository, UserRepository userRepository) {
    this.notificationRepository = notificationRepository;
    this.userRepository = userRepository;
  }

  @Async("notificationExecutor")
  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleOrderSubmitted(OrderSubmittedEvent event) {
    try {
      if (!hasValidResource(event.orderId(), event.orderNo())) {
        LOGGER.warn("Skipping submitted-order notification because its order data is invalid");
        return;
      }
      saveNotifications(
          event.recipientIds(),
          NotificationType.STATUS_CHANGE,
          ORDER_RESOURCE_TYPE,
          event.orderId(),
          "订单 " + event.orderNo() + " 已提交",
          "订单 " + event.orderNo() + " 已提交，请及时处理。");
    } catch (RuntimeException exception) {
      LOGGER.error("Unable to persist submitted-order notification for order {}", event.orderId(), exception);
    }
  }

  @Async("notificationExecutor")
  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleOrderRejected(OrderRejectedEvent event) {
    try {
      if (!hasValidResource(event.orderId(), event.orderNo())) {
        LOGGER.warn("Skipping rejected-order notification because its order data is invalid");
        return;
      }
      String reason = event.rejectionReason() == null || event.rejectionReason().isBlank()
          ? ""
          : "，原因：" + event.rejectionReason();
      saveNotifications(
          event.recipientIds(),
          NotificationType.STATUS_CHANGE,
          ORDER_RESOURCE_TYPE,
          event.orderId(),
          "订单 " + event.orderNo() + " 已被退回",
          "订单 " + event.orderNo() + " 已被退回" + reason + "。");
    } catch (RuntimeException exception) {
      LOGGER.error("Unable to persist rejected-order notification for order {}", event.orderId(), exception);
    }
  }

  @Async("notificationExecutor")
  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleTaskAssigned(TaskAssignedEvent event) {
    try {
      if (!hasValidResource(event.taskId(), event.orderNo()) || event.deadline() == null) {
        LOGGER.warn("Skipping task-assigned notification because its task data is invalid");
        return;
      }
      String deadline = formatDeadline(event.deadline());
      saveNotifications(
          event.recipientIds(),
          NotificationType.TODO,
          MATERIAL_TASK_RESOURCE_TYPE,
          event.taskId(),
          "任务 " + event.orderNo() + " 已派发给你",
          "任务 " + event.orderNo() + " 已派发给你，截止 " + deadline);
    } catch (RuntimeException exception) {
      LOGGER.error("Unable to persist task-assigned notification for task {}", event.taskId(), exception);
    }
  }

  private void saveNotifications(
      List<Long> recipientIds,
      NotificationType notificationType,
      String resourceType,
      Long resourceId,
      String title,
      String content) {
    for (Long recipientId : recipientIds) {
      if (recipientId == null || recipientId <= 0) {
        LOGGER.warn("Skipping notification for invalid recipient ID {}", recipientId);
        continue;
      }
      userRepository.findById(recipientId).ifPresentOrElse(
          recipient -> saveNotification(recipient, notificationType, resourceType, resourceId, title, content),
          () -> LOGGER.warn("Skipping notification because recipient {} does not exist", recipientId));
    }
  }

  private void saveNotification(
      User recipient,
      NotificationType notificationType,
      String resourceType,
      Long resourceId,
      String title,
      String content) {
    notificationRepository.save(Notification.create(
        recipient, notificationType, resourceType, resourceId, title, content));
  }

  private boolean hasValidResource(Long resourceId, String orderNo) {
    return resourceId != null && resourceId > 0 && orderNo != null && !orderNo.isBlank();
  }

  private String formatDeadline(LocalDateTime deadline) {
    return DEADLINE_FORMATTER.format(deadline);
  }
}
