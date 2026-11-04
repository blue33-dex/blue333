package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** P4-T09: SSE hub registration, push and heartbeat behaviour. */
class NotificationStreamServiceTest {
  private static final Long USER_ID = 7L;

  private final NotificationStreamService service = new NotificationStreamService();

  @Test
  void registersOneEmitterPerOpenTab() {
    SseEmitter first = service.subscribe(USER_ID);
    service.subscribe(USER_ID);

    assertThat(service.openStreamCount(USER_ID)).isEqualTo(2);
    assertThat(first.getTimeout()).isNotNull();
  }

  @Test
  void rejectsSubscriptionsWithoutAPersistedUser() {
    assertThatIllegalArgumentException().isThrownBy(() -> service.subscribe(null));
    assertThatIllegalArgumentException().isThrownBy(() -> service.subscribe(0L));
  }

  @Test
  void pushesOnlyToSubscribersOfTheRecipient() {
    service.subscribe(USER_ID);

    service.publish(USER_ID, payload());
    service.publish(8L, payload());

    assertThat(service.openStreamCount(USER_ID)).isEqualTo(1);
    service.heartbeat();
    assertThat(service.openStreamCount(USER_ID)).isEqualTo(1);
  }

  @Test
  void ignoresNullPushes() {
    service.subscribe(USER_ID);

    service.publish(null, payload());
    service.publish(USER_ID, null);

    assertThat(service.openStreamCount(USER_ID)).isEqualTo(1);
  }

  private com.blue333.ordermgmt.dto.notification.NotificationResponses.NotificationResponse payload() {
    return new com.blue333.ordermgmt.dto.notification.NotificationResponses.NotificationResponse(
        1L, "TODO", "EXCEPTION_RECORD", 90L, "异常待确认", "late", false, null, null);
  }
}
