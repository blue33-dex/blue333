package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.domain.ExceptionRecord;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.enums.ExceptionStatus;
import com.blue333.ordermgmt.domain.enums.ExceptionType;
import com.blue333.ordermgmt.domain.enums.MaterialTaskStatus;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.dto.exception.ExceptionResponses.ExceptionScanResponse;
import com.blue333.ordermgmt.event.ExceptionRecordedEvent;
import com.blue333.ordermgmt.repository.ExceptionRecordRepository;
import com.blue333.ordermgmt.repository.MaterialTaskRepository;
import com.blue333.ordermgmt.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * P4-T05: daily overdue scan. Every in-production task whose deadline passed while it still has work
 * ahead produces one DELAY exception in {@code PENDING_CONFIRM}. Re-runs never duplicate because the
 * pair (task, DELAY) is unique by application check.
 *
 * <p>Records created here carry no recorder: attributing an automatic finding to a random account
 * would falsify the audit trail. {@code scanOverdue()} is also exposed to BO as an operational
 * trigger, which keeps the behaviour verifiable in end-to-end tests without waiting for 04:00.</p>
 */
@Service
public class ExceptionAutoGenerator {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionAutoGenerator.class);
  private static final Set<MaterialTaskStatus> ACTIVE_STATUSES = Set.of(
      MaterialTaskStatus.PENDING_DESIGN, MaterialTaskStatus.PENDING_APPROVAL,
      MaterialTaskStatus.PENDING_REVISE);
  private static final DateTimeFormatter DEADLINE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final MaterialTaskRepository materialTaskRepository;
  private final ExceptionRecordRepository exceptionRecordRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;
  private final ZoneId businessZone;

  public ExceptionAutoGenerator(
      MaterialTaskRepository materialTaskRepository,
      ExceptionRecordRepository exceptionRecordRepository,
      UserRepository userRepository,
      ApplicationEventPublisher eventPublisher,
      Clock clock,
      @Value("${app.business-time-zone:Asia/Shanghai}") String businessTimeZone) {
    this.materialTaskRepository = materialTaskRepository;
    this.exceptionRecordRepository = exceptionRecordRepository;
    this.userRepository = userRepository;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
    this.businessZone = ZoneId.of(businessTimeZone);
  }

  @Scheduled(cron = "${app.exception-scan.cron:0 0 4 * * ?}")
  @Transactional
  public ExceptionScanResponse scanOverdue() {
    LocalDateTime now = now();
    List<MaterialTask> overdueTasks = materialTaskRepository.findOverdueTasks(now,
        ACTIVE_STATUSES, OrderStatus.IN_PRODUCTION);
    List<Long> createdIds = new ArrayList<>();
    for (MaterialTask task : overdueTasks) {
      if (exceptionRecordRepository.existsByTaskIdAndExceptionType(task.getId(), ExceptionType.DELAY)) {
        continue;
      }
      ExceptionRecord saved = exceptionRecordRepository.save(ExceptionRecord.create(task.getOrder(), task,
          ExceptionType.DELAY, ExceptionStatus.PENDING_CONFIRM, describe(task), null));
      createdIds.add(saved.getId());
      publishAfterCommit(new ExceptionRecordedEvent(saved.getId(), task.getOrder().getId(),
          task.getOrder().getOrderNo(), ExceptionType.DELAY.name(), saved.getDescription(),
          userRepository.findEnabledIdsByRoleType(RoleType.BO)));
    }
    if (!createdIds.isEmpty()) {
      LOGGER.info("Overdue scan created {} DELAY exception record(s): {}", createdIds.size(), createdIds);
    }
    return new ExceptionScanResponse(now, createdIds.size(), List.copyOf(createdIds));
  }

  private String describe(MaterialTask task) {
    return "任务 " + task.getMaterialSpec().getMaterialType().name() + " "
        + task.getMaterialSpec().getSpecSize() + " 已超过任务截止时间 "
        + DEADLINE_FORMATTER.format(task.getTaskDeadline());
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock.withZone(businessZone));
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
