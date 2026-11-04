package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.domain.Notification;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.NotificationType;
import com.blue333.ordermgmt.dto.notification.NotificationResponses.MarkAllReadResponse;
import com.blue333.ordermgmt.dto.notification.NotificationResponses.NotificationResponse;
import com.blue333.ordermgmt.dto.notification.NotificationResponses.UnreadCountResponse;
import com.blue333.ordermgmt.event.ExceptionRecordedEvent;
import com.blue333.ordermgmt.event.OrderRejectedEvent;
import com.blue333.ordermgmt.event.OrderSubmittedEvent;
import com.blue333.ordermgmt.event.TaskAssignedEvent;
import com.blue333.ordermgmt.event.TaskReassignedEvent;
import com.blue333.ordermgmt.event.VersionRejectedEvent;
import com.blue333.ordermgmt.event.VersionReleasedEvent;
import com.blue333.ordermgmt.event.VersionSubmittedEvent;
import com.blue333.ordermgmt.repository.NotificationRepository;
import com.blue333.ordermgmt.repository.UserRepository;
import java.time.Clock;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Writes in-application notifications as isolated, asynchronous event side effects. */
@Service
public class NotificationService {
  private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);
  private static final String ORDER_RESOURCE_TYPE = "ORDER";
  private static final String MATERIAL_TASK_RESOURCE_TYPE = "MATERIAL_TASK";
  private static final String DESIGN_VERSION_RESOURCE_TYPE = "DESIGN_VERSION";
  private static final String EXCEPTION_RECORD_RESOURCE_TYPE = "EXCEPTION_RECORD";
  private static final DateTimeFormatter DEADLINE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;
  private final NotificationStreamService streamService;
  private final Clock clock;

  public NotificationService(
      NotificationRepository notificationRepository,
      UserRepository userRepository,
      NotificationStreamService streamService,
      Clock clock) {
    this.notificationRepository = notificationRepository;
    this.userRepository = userRepository;
    this.streamService = streamService;
    this.clock = clock;
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

  @Async("notificationExecutor")
  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleTaskReassigned(TaskReassignedEvent event) {
    try {
      if (!hasValidResource(event.taskId(), event.orderNo()) || event.deadline() == null) {
        LOGGER.warn("Skipping task-reassigned notification because its task data is invalid");
        return;
      }
      String deadline = formatDeadline(event.deadline());
      saveNotifications(
          event.recipientIds(),
          NotificationType.TODO,
          MATERIAL_TASK_RESOURCE_TYPE,
          event.taskId(),
          "任务 " + event.orderNo() + " 已重新分派给你",
          "任务 " + event.orderNo() + " 已转派给你，截止 " + deadline + "，请及时处理。");
      if (event.previousAssigneeId() != null && event.previousAssigneeId() > 0) {
        saveNotifications(
            List.of(event.previousAssigneeId()),
            NotificationType.STATUS_CHANGE,
            MATERIAL_TASK_RESOURCE_TYPE,
            event.taskId(),
            "任务 " + event.orderNo() + " 已转派给其他设计人员",
            "任务 " + event.orderNo() + " 已由业务负责人转派给其他设计人员。");
      }
    } catch (RuntimeException exception) {
      LOGGER.error("Unable to persist task-reassigned notification for task {}", event.taskId(), exception);
    }
  }

  @Async("notificationExecutor")
  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleVersionSubmitted(VersionSubmittedEvent event) {
    try {
      if (!hasValidResource(event.versionId(), event.orderNo()) || event.versionNo() == null) {
        LOGGER.warn("Skipping version-submitted notification because its version data is invalid");
        return;
      }
      String material = event.materialLabel() == null || event.materialLabel().isBlank()
          ? "" : "（" + event.materialLabel() + "）";
      saveNotifications(
          event.recipientIds(),
          NotificationType.TODO,
          DESIGN_VERSION_RESOURCE_TYPE,
          event.versionId(),
          "订单 " + event.orderNo() + " 有待内部评审的版本 " + event.versionNo(),
          "订单 " + event.orderNo() + material + " 的设计版本 " + event.versionNo() + " 已提交内部评审，请及时处理。");
    } catch (RuntimeException exception) {
      LOGGER.error("Unable to persist version-submitted notification for version {}", event.versionId(), exception);
    }
  }

  @Async("notificationExecutor")
  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleVersionReleased(VersionReleasedEvent event) {
    try {
      if (!hasValidResource(event.versionId(), event.orderNo()) || event.versionNo() == null) {
        LOGGER.warn("Skipping version-released notification because its version data is invalid");
        return;
      }
      saveNotifications(
          event.recipientIds(),
          NotificationType.STATUS_CHANGE,
          DESIGN_VERSION_RESOURCE_TYPE,
          event.versionId(),
          "订单 " + event.orderNo() + " 的版本 " + event.versionNo() + " 已放行",
          "订单 " + event.orderNo() + " 的设计版本 " + event.versionNo() + " 已通过内部评审并放行，可进入客户评审。");
    } catch (RuntimeException exception) {
      LOGGER.error("Unable to persist version-released notification for version {}", event.versionId(), exception);
    }
  }

  @Async("notificationExecutor")
  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleVersionRejected(VersionRejectedEvent event) {
    try {
      if (!hasValidResource(event.versionId(), event.orderNo()) || event.versionNo() == null) {
        LOGGER.warn("Skipping version-rejected notification because its version data is invalid");
        return;
      }
      String comment = event.reviewComment() == null || event.reviewComment().isBlank()
          ? "" : "，退回意见：" + event.reviewComment();
      saveNotifications(
          event.recipientIds(),
          NotificationType.TODO,
          DESIGN_VERSION_RESOURCE_TYPE,
          event.versionId(),
          "订单 " + event.orderNo() + " 的版本 " + event.versionNo() + " 被退回修改",
          "订单 " + event.orderNo() + " 的设计版本 " + event.versionNo() + " 未通过内部评审" + comment + "。");
    } catch (RuntimeException exception) {
      LOGGER.error("Unable to persist version-rejected notification for version {}", event.versionId(), exception);
    }
  }

  @Async("notificationExecutor")
  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleExceptionRecorded(ExceptionRecordedEvent event) {
    try {
      if (!hasValidResource(event.exceptionId(), event.orderNo())) {
        LOGGER.warn("Skipping exception notification because its exception data is invalid");
        return;
      }
      saveNotifications(
          event.recipientIds(),
          NotificationType.TODO,
          EXCEPTION_RECORD_RESOURCE_TYPE,
          event.exceptionId(),
          "订单 " + event.orderNo() + " 存在异常待确认",
          "订单 " + event.orderNo() + " 记录了异常（" + event.exceptionType() + "）：" + event.description());
    } catch (RuntimeException exception) {
      LOGGER.error("Unable to persist exception notification for record {}", event.exceptionId(), exception);
    }
  }

  /** Notification center list for the current user, newest first. */
  @Transactional(readOnly = true)
  public CursorPage<NotificationResponse> listMine(CurrentUser currentUser, String cursor, Integer limit,
      boolean unreadOnly) {
    Long recipientId = requireRecipientId(currentUser);
    int pageLimit = CursorPagination.normalizeLimit(limit);
    List<Notification> rows = notificationRepository.findPageForRecipient(recipientId, unreadOnly,
        CursorPagination.decodeCursor(cursor), CursorPagination.pageable(pageLimit));
    return CursorPage.fromFetched(rows, pageLimit, Notification::getId).map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public UnreadCountResponse unreadCount(CurrentUser currentUser) {
    return new UnreadCountResponse(
        notificationRepository.countByRecipientIdAndReadFalse(requireRecipientId(currentUser)));
  }

  @Transactional
  public NotificationResponse markRead(Long notificationId, CurrentUser currentUser) {
    Long recipientId = requireRecipientId(currentUser);
    Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, recipientId)
        .orElseThrow(() -> new ResourceNotFoundException("Notification does not exist: " + notificationId));
    notification.markRead(LocalDateTime.now(clock));
    return toResponse(notificationRepository.save(notification));
  }

  @Transactional
  public MarkAllReadResponse markAllRead(CurrentUser currentUser) {
    int updated = notificationRepository.markAllRead(requireRecipientId(currentUser), LocalDateTime.now(clock));
    return new MarkAllReadResponse(updated);
  }

  private Long requireRecipientId(CurrentUser currentUser) {
    if (currentUser == null || currentUser.id() == null || currentUser.id() <= 0) {
      throw new IllegalArgumentException("A persisted user is required");
    }
    return currentUser.id();
  }

  public NotificationResponse toResponse(Notification notification) {
    return new NotificationResponse(notification.getId(), notification.getNotificationType().name(),
        notification.getResourceType(), notification.getResourceId(), notification.getTitle(),
        notification.getContent(), Boolean.TRUE.equals(notification.getRead()), notification.getReadAt(),
        notification.getCreatedAt());
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
    Notification saved = notificationRepository.save(Notification.create(
        recipient, notificationType, resourceType, resourceId, title, content));
    NotificationResponse payload = toResponse(saved);
    Long recipientId = recipient.getId();
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      streamService.publish(recipientId, payload);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        streamService.publish(recipientId, payload);
      }
    });
  }

  private boolean hasValidResource(Long resourceId, String orderNo) {
    return resourceId != null && resourceId > 0 && orderNo != null && !orderNo.isBlank();
  }

  private String formatDeadline(LocalDateTime deadline) {
    return DEADLINE_FORMATTER.format(deadline);
  }
}
