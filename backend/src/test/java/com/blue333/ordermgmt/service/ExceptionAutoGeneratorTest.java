package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blue333.ordermgmt.domain.ExceptionRecord;
import com.blue333.ordermgmt.domain.MaterialSpec;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.enums.ExceptionStatus;
import com.blue333.ordermgmt.domain.enums.ExceptionType;
import com.blue333.ordermgmt.domain.enums.MaterialType;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.dto.exception.ExceptionResponses.ExceptionScanResponse;
import com.blue333.ordermgmt.event.ExceptionRecordedEvent;
import com.blue333.ordermgmt.repository.ExceptionRecordRepository;
import com.blue333.ordermgmt.repository.MaterialTaskRepository;
import com.blue333.ordermgmt.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** P4-T05: overdue automatic DELAY generation with (task, DELAY) de-duplication. */
class ExceptionAutoGeneratorTest {
  private static final Long ORDER_ID = 1L;
  private static final Long TASK_ID = 10L;
  private static final Long BO_ID = 7L;
  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");

  private MaterialTaskRepository materialTaskRepository;
  private ExceptionRecordRepository exceptionRecordRepository;
  private UserRepository userRepository;
  private ApplicationEventPublisher eventPublisher;
  private ExceptionAutoGenerator generator;

  private MaterialTask task;
  private Order order;

  @BeforeEach
  void setUp() {
    materialTaskRepository = mock(MaterialTaskRepository.class);
    exceptionRecordRepository = mock(ExceptionRecordRepository.class);
    userRepository = mock(UserRepository.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    generator = new ExceptionAutoGenerator(materialTaskRepository, exceptionRecordRepository,
        userRepository, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Shanghai");

    when(exceptionRecordRepository.save(any(ExceptionRecord.class))).thenAnswer(call -> {
      ExceptionRecord record = call.getArgument(0);
      org.springframework.test.util.ReflectionTestUtils.setField(record, "id", 90L);
      return record;
    });
    when(userRepository.findEnabledIdsByRoleType(RoleType.BO)).thenReturn(List.of(BO_ID));

    order = mock(Order.class);
    when(order.getId()).thenReturn(ORDER_ID);
    when(order.getOrderNo()).thenReturn("ORD-20261104-001");

    MaterialSpec spec = mock(MaterialSpec.class);
    when(spec.getMaterialType()).thenReturn(MaterialType.POSTER);
    when(spec.getSpecSize()).thenReturn("A2");
    task = mock(MaterialTask.class);
    when(task.getId()).thenReturn(TASK_ID);
    when(task.getOrder()).thenReturn(order);
    when(task.getMaterialSpec()).thenReturn(spec);
    when(task.getTaskDeadline()).thenReturn(LocalDateTime.of(2026, 11, 3, 18, 0));
  }

  @Test
  void createsOnePendingDelayRecordPerNewlyOverdueTask() {
    when(materialTaskRepository.findOverdueTasks(any(), anyCollection(), eq(OrderStatus.IN_PRODUCTION)))
        .thenReturn(List.of(task));
    when(exceptionRecordRepository.existsByTaskIdAndExceptionType(TASK_ID, ExceptionType.DELAY))
        .thenReturn(false);

    ExceptionScanResponse response = generator.scanOverdue();

    assertThat(response.createdCount()).isEqualTo(1);
    assertThat(response.createdIds()).containsExactly(90L);
    ArgumentCaptor<ExceptionRecord> saved = ArgumentCaptor.forClass(ExceptionRecord.class);
    verify(exceptionRecordRepository).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(ExceptionStatus.PENDING_CONFIRM);
    assertThat(saved.getValue().getExceptionType()).isEqualTo(ExceptionType.DELAY);
    assertThat(saved.getValue().getTask()).isSameAs(task);
    assertThat(saved.getValue().getRecorder()).isNull();
    assertThat(saved.getValue().getDescription())
        .isEqualTo("任务 POSTER A2 已超过任务截止时间 2026-11-03 18:00");

    ArgumentCaptor<ExceptionRecordedEvent> event = ArgumentCaptor.forClass(ExceptionRecordedEvent.class);
    verify(eventPublisher).publishEvent(event.capture());
    assertThat(event.getValue().recipientIds()).containsExactly(BO_ID);
    assertThat(event.getValue().orderNo()).isEqualTo("ORD-20261104-001");
  }

  @Test
  void repeatedScansDoNotDuplicateAnExistingDelayRecord() {
    when(materialTaskRepository.findOverdueTasks(any(), anyCollection(), eq(OrderStatus.IN_PRODUCTION)))
        .thenReturn(List.of(task));
    when(exceptionRecordRepository.existsByTaskIdAndExceptionType(TASK_ID, ExceptionType.DELAY))
        .thenReturn(true);

    ExceptionScanResponse response = generator.scanOverdue();

    assertThat(response.createdCount()).isZero();
    assertThat(response.createdIds()).isEmpty();
    verify(exceptionRecordRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void scansOnlyActiveTasksOfInProductionOrdersInBusinessTime() {
    when(materialTaskRepository.findOverdueTasks(any(), anyCollection(), eq(OrderStatus.IN_PRODUCTION)))
        .thenReturn(List.of());

    ExceptionScanResponse response = generator.scanOverdue();

    // 08:00 UTC is 16:00 in Asia/Shanghai, the business-time reference used by the scan.
    assertThat(response.scannedAt()).isEqualTo(LocalDateTime.of(2026, 11, 4, 16, 0));
    assertThat(response.createdCount()).isZero();
  }
}
