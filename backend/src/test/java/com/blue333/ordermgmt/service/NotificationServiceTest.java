package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blue333.ordermgmt.domain.Notification;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.NotificationType;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.dto.notification.NotificationResponses.MarkAllReadResponse;
import com.blue333.ordermgmt.dto.notification.NotificationResponses.NotificationResponse;
import com.blue333.ordermgmt.dto.notification.NotificationResponses.UnreadCountResponse;
import com.blue333.ordermgmt.event.ExceptionRecordedEvent;
import com.blue333.ordermgmt.repository.NotificationRepository;
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
import org.springframework.data.domain.PageRequest;

/** P4-T09: notification persistence, SSE push, list/unread/read endpoints and the exception event. */
class NotificationServiceTest {
  private static final Long ORDER_ID = 1L;
  private static final Long BO_ID = 7L;
  private static final Long NOTIFICATION_ID = 90L;
  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");

  private NotificationRepository notificationRepository;
  private UserRepository userRepository;
  private NotificationStreamService streamService;
  private NotificationService service;
  private User bo;

  @BeforeEach
  void setUp() {
    notificationRepository = mock(NotificationRepository.class);
    userRepository = mock(UserRepository.class);
    streamService = mock(NotificationStreamService.class);
    service = new NotificationService(notificationRepository, userRepository, streamService,
        Clock.fixed(NOW, ZoneOffset.UTC));

    bo = mock(User.class);
    when(bo.getId()).thenReturn(BO_ID);
    when(userRepository.findById(BO_ID)).thenReturn(Optional.of(bo));
    when(notificationRepository.save(any(Notification.class))).thenAnswer(call -> call.getArgument(0));
  }

  @Test
  void exceptionEventsCreateOneNotificationAndPushItOverSse() {
    service.handleExceptionRecorded(new ExceptionRecordedEvent(90L, ORDER_ID, "ORD-20261104-001",
        "DELAY", "任务 POSTER A2 已超过任务截止时间", List.of(BO_ID)));

    ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).save(saved.capture());
    Notification notification = saved.getValue();
    assertThat(notification.getNotificationType()).isEqualTo(NotificationType.TODO);
    assertThat(notification.getResourceType()).isEqualTo("EXCEPTION_RECORD");
    assertThat(notification.getResourceId()).isEqualTo(90L);
    assertThat(notification.getTitle()).isEqualTo("订单 ORD-20261104-001 存在异常待确认");
    assertThat(notification.getContent()).contains("DELAY").contains("已超过任务截止时间");

    ArgumentCaptor<NotificationResponse> payload = ArgumentCaptor.forClass(NotificationResponse.class);
    verify(streamService).publish(eq(BO_ID), payload.capture());
    assertThat(payload.getValue().resourceType()).isEqualTo("EXCEPTION_RECORD");
  }

  @Test
  void invalidEventsAreSkippedWithoutSideEffects() {
    service.handleExceptionRecorded(new ExceptionRecordedEvent(null, ORDER_ID, "ORD-20261104-001",
        "DELAY", "late", List.of(BO_ID)));
    service.handleExceptionRecorded(new ExceptionRecordedEvent(90L, ORDER_ID, "  ", "DELAY", "late",
        List.of(BO_ID)));

    verify(notificationRepository, never()).save(any());
    verify(streamService, never()).publish(any(), any());
  }

  @Test
  void listsTheCurrentUsersNotificationsNewestFirst() {
    Notification notification = Notification.create(bo, NotificationType.STATUS_CHANGE, "ORDER",
        ORDER_ID, "订单已提交", "请及时处理");
    when(notificationRepository.findPageForRecipient(BO_ID, true, null, PageRequest.of(0, 3)))
        .thenReturn(List.of(notification));

    CursorPage<NotificationResponse> page = service.listMine(businessOwner(), null, 2, true);

    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).title()).isEqualTo("订单已提交");
    assertThat(page.items().get(0).read()).isFalse();
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  void unreadCountAndMarkAllReadUseTheRecipientScope() {
    when(notificationRepository.countByRecipientIdAndReadFalse(BO_ID)).thenReturn(4L);
    when(notificationRepository.markAllRead(eq(BO_ID), any(LocalDateTime.class))).thenReturn(3);

    UnreadCountResponse count = service.unreadCount(businessOwner());
    MarkAllReadResponse updated = service.markAllRead(businessOwner());

    assertThat(count.unreadCount()).isEqualTo(4L);
    assertThat(updated.updatedCount()).isEqualTo(3);
    verify(notificationRepository).markAllRead(BO_ID, LocalDateTime.of(2026, 11, 4, 8, 0));
  }

  @Test
  void markingReadOnlyTouchesTheOwnersNotification() {
    Notification notification = Notification.create(bo, NotificationType.TODO, "EXCEPTION_RECORD",
        ORDER_ID, "异常待确认", "late");
    when(notificationRepository.findByIdAndRecipientId(NOTIFICATION_ID, BO_ID))
        .thenReturn(Optional.of(notification));

    NotificationResponse response = service.markRead(NOTIFICATION_ID, businessOwner());

    assertThat(response.read()).isTrue();
    assertThat(response.readAt()).isEqualTo(LocalDateTime.of(2026, 11, 4, 8, 0));
    verify(notificationRepository).save(notification);

    when(notificationRepository.findByIdAndRecipientId(404L, BO_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.markRead(404L, businessOwner()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void markingAnAlreadyReadNotificationKeepsItsOriginalTimestamp() {
    Notification notification = Notification.create(bo, NotificationType.TODO, "ORDER", ORDER_ID,
        "title", "content");
    notification.markRead(LocalDateTime.of(2026, 11, 3, 9, 0));
    notification.markRead(LocalDateTime.of(2026, 11, 5, 9, 0));

    assertThat(notification.getRead()).isTrue();
    assertThat(notification.getReadAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 9, 0));
  }

  @Test
  void endpointsRequireAPersistedUser() {
    User anonymous = mock(User.class);
    assertThatIllegalArgumentException().isThrownBy(
        () -> service.unreadCount(new CurrentUser(anonymous, Set.of(RoleType.BO))));
  }

  private CurrentUser businessOwner() {
    return new CurrentUser(bo, Set.of(RoleType.BO));
  }
}
