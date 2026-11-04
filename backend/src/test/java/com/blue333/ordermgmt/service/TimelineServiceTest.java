package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blue333.ordermgmt.domain.AuditLog;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.AuditActionType;
import com.blue333.ordermgmt.domain.enums.AuditTargetType;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.dto.timeline.TimelineResponses.TimelineEntryResponse;
import com.blue333.ordermgmt.repository.AuditLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

/** P4-T08: A-29 timeline mapping under BR-13 visibility. */
class TimelineServiceTest {
  private static final Long ORDER_ID = 1L;
  private static final Long USER_ID = 7L;

  private AuditLogRepository auditLogRepository;
  private AccessControlService accessControl;
  private TimelineService service;
  private CurrentUser businessOwner;

  @BeforeEach
  void setUp() {
    auditLogRepository = mock(AuditLogRepository.class);
    accessControl = mock(AccessControlService.class);
    service = new TimelineService(auditLogRepository, accessControl);
    User bo = mock(User.class);
    when(bo.getId()).thenReturn(USER_ID);
    businessOwner = new CurrentUser(bo, Set.of(RoleType.BO));
    when(accessControl.findVisibleOrder(eq(ORDER_ID), any())).thenReturn(mock(Order.class));
  }

  @Test
  void mapsAuditRowsToChineseTimelineEntries() {
    AuditLog exception = auditLog(AuditActionType.RECORD_EXCEPTION, AuditTargetType.EXCEPTION_RECORD, 90L);
    when(auditLogRepository.findForOrderTimeline(eq(ORDER_ID), eq(AuditTargetType.ORDER),
        eq(AuditTargetType.MATERIAL_TASK), eq(AuditTargetType.DESIGN_VERSION),
        eq(AuditTargetType.FEEDBACK_ITEM), eq(AuditTargetType.CONFIRMATION_RECORD),
        eq(AuditTargetType.EXCEPTION_RECORD), any(), any())).thenReturn(List.of(exception));

    CursorPage<TimelineEntryResponse> page = service.timeline(ORDER_ID, businessOwner, null, 20);

    assertThat(page.items()).hasSize(1);
    TimelineEntryResponse entry = page.items().get(0);
    assertThat(entry.id()).isEqualTo(90L);
    assertThat(entry.actionType()).isEqualTo("RECORD_EXCEPTION");
    assertThat(entry.actionLabel()).isEqualTo("记录异常");
    assertThat(entry.targetType()).isEqualTo("EXCEPTION_RECORD");
    assertThat(entry.operatorId()).isEqualTo(USER_ID);
    assertThat(entry.operatorName()).isEqualTo("Business Owner");
    assertThat(entry.beforeState()).isNull();
    assertThat(entry.afterState()).isEqualTo("PENDING_CONFIRM");
    assertThat(entry.operatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 4, 8, 0));
    verify(accessControl).findVisibleOrder(ORDER_ID, businessOwner);
  }

  @Test
  void rejectsOrdersOutsideTheCallerScopeBeforeQuerying() {
    when(accessControl.findVisibleOrder(eq(404L), any()))
        .thenThrow(new AccessDeniedException("Order is not visible"));

    assertThatThrownBy(() -> service.timeline(404L, businessOwner, null, 20))
        .isInstanceOf(AccessDeniedException.class);
    verify(auditLogRepository, never()).findForOrderTimeline(any(), any(), any(), any(), any(), any(),
        any(), any(), any());
  }

  @Test
  void requestsOneExtraRowForCursorDetection() {
    when(auditLogRepository.findForOrderTimeline(any(), any(), any(), any(), any(), any(), any(), any(),
        any())).thenReturn(List.of());

    service.timeline(ORDER_ID, businessOwner, null, 2);

    verify(auditLogRepository).findForOrderTimeline(eq(ORDER_ID), eq(AuditTargetType.ORDER),
        eq(AuditTargetType.MATERIAL_TASK), eq(AuditTargetType.DESIGN_VERSION),
        eq(AuditTargetType.FEEDBACK_ITEM), eq(AuditTargetType.CONFIRMATION_RECORD),
        eq(AuditTargetType.EXCEPTION_RECORD), isNull(), eq(PageRequest.of(0, 3)));
  }

  private AuditLog auditLog(AuditActionType action, AuditTargetType target, Long targetId) {
    User operator = mock(User.class);
    when(operator.getId()).thenReturn(USER_ID);
    when(operator.getName()).thenReturn("Business Owner");
    AuditLog log = mock(AuditLog.class);
    when(log.getId()).thenReturn(90L);
    when(log.getActionType()).thenReturn(action);
    when(log.getTargetType()).thenReturn(target);
    when(log.getTargetId()).thenReturn(targetId);
    when(log.getBeforeState()).thenReturn(null);
    when(log.getAfterState()).thenReturn("PENDING_CONFIRM");
    when(log.getResult()).thenReturn("SUCCESS");
    when(log.getRemark()).thenReturn("late");
    when(log.getOperator()).thenReturn(operator);
    when(log.getOperatedAt()).thenReturn(LocalDateTime.of(2026, 11, 4, 8, 0));
    return log;
  }
}
