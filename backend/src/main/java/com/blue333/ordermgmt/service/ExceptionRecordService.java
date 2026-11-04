package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.aop.AuditedAction;
import com.blue333.ordermgmt.domain.ExceptionRecord;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.AuditActionType;
import com.blue333.ordermgmt.domain.enums.AuditTargetType;
import com.blue333.ordermgmt.domain.enums.ExceptionStatus;
import com.blue333.ordermgmt.domain.enums.ExceptionType;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.dto.exception.ExceptionResponses.ExceptionRecordResponse;
import com.blue333.ordermgmt.event.ExceptionRecordedEvent;
import com.blue333.ordermgmt.repository.ExceptionRecordRepository;
import com.blue333.ordermgmt.repository.OrderRepository;
import com.blue333.ordermgmt.repository.UserRepository;
import com.blue333.ordermgmt.util.XSSCleaner;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A-20 record exception / A-21 close exception, plus the archive precondition (P4-T06).
 *
 * <p>Only a BO may record or close; the automatic overdue scan writes records without a recorder.
 * Archiving an order is blocked while any exception of that order is not CLOSED.</p>
 */
@Service
public class ExceptionRecordService {
  private static final int MAX_TEXT_LENGTH = 5000;

  private final ExceptionRecordRepository exceptionRecordRepository;
  private final OrderRepository orderRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public ExceptionRecordService(
      ExceptionRecordRepository exceptionRecordRepository,
      OrderRepository orderRepository,
      UserRepository userRepository,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.exceptionRecordRepository = exceptionRecordRepository;
    this.orderRepository = orderRepository;
    this.userRepository = userRepository;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  /** A-20: a BO records a manual exception (DELAY or OTHER) against an order. */
  @Transactional
  @AuditedAction(action = AuditActionType.RECORD_EXCEPTION, targetType = AuditTargetType.EXCEPTION_RECORD,
      targetId = "#result.id", afterState = "#result.status",
      remark = "T(com.blue333.ordermgmt.util.XSSCleaner).clean(#description)")
  public ExceptionRecordResponse record(
      Long orderId, ExceptionType exceptionType, String description, CurrentUser currentUser) {
    requireBusinessOwner(currentUser);
    if (exceptionType == null) {
      throw new IllegalArgumentException("exception_type is required");
    }
    String cleanDescription = requireText(description, "description is required");
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new ResourceNotFoundException("Order does not exist: " + orderId));
    if (order.getStatus() == OrderStatus.CANCELLED) {
      throw new InvalidStateTransitionException("Cancelled orders cannot receive exception records");
    }
    ExceptionRecord saved = exceptionRecordRepository.save(ExceptionRecord.create(order, null,
        exceptionType, ExceptionStatus.PENDING_CONFIRM, cleanDescription, currentUser.user()));
    publishAfterCommit(new ExceptionRecordedEvent(saved.getId(), order.getId(), order.getOrderNo(),
        exceptionType.name(), cleanDescription, userRepository.findEnabledIdsByRoleType(RoleType.BO)));
    return toResponse(saved);
  }

  /** A-21: a BO closes an open exception, keeping the handling note for the archive report. */
  @Transactional
  @AuditedAction(action = AuditActionType.CLOSE_EXCEPTION, targetType = AuditTargetType.EXCEPTION_RECORD,
      targetId = "#exceptionId", afterState = "#result.status",
      remark = "T(com.blue333.ordermgmt.util.XSSCleaner).clean(#resolution)")
  public ExceptionRecordResponse close(Long exceptionId, String resolution, CurrentUser currentUser) {
    requireBusinessOwner(currentUser);
    ExceptionRecord record = exceptionRecordRepository.findByIdForUpdate(exceptionId)
        .orElseThrow(() -> new ResourceNotFoundException("Exception record does not exist: " + exceptionId));
    if (record.isClosed()) {
      throw new InvalidStateTransitionException("Exception record is already closed");
    }
    record.close(cleanOptional(resolution), LocalDateTime.now(clock));
    return toResponse(exceptionRecordRepository.save(record));
  }

  @Transactional(readOnly = true)
  public List<ExceptionRecordResponse> listForOrder(Long orderId, CurrentUser currentUser) {
    requireBusinessOwner(currentUser);
    if (!orderRepository.existsById(orderId)) {
      throw new ResourceNotFoundException("Order does not exist: " + orderId);
    }
    return exceptionRecordRepository.findAllByOrderId(orderId).stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public CursorPage<ExceptionRecordResponse> list(ExceptionStatus status, String cursor, Integer limit,
      CurrentUser currentUser) {
    requireBusinessOwner(currentUser);
    int pageLimit = CursorPagination.normalizeLimit(limit);
    List<ExceptionRecord> rows = exceptionRecordRepository.findPage(status,
        CursorPagination.decodeCursor(cursor), CursorPagination.pageable(pageLimit));
    return CursorPage.fromFetched(rows, pageLimit, ExceptionRecord::getId).map(this::toResponse);
  }

  /** P4-T06: archiving requires every exception of the order to be closed. Throws 422 otherwise. */
  @Transactional(readOnly = true)
  public void requireAllClosed(Long orderId) {
    long open = exceptionRecordRepository.countByOrderIdAndStatusNot(orderId, ExceptionStatus.CLOSED);
    if (open > 0) {
      throw new IllegalStateException(
          "All exception records must be closed before archiving (" + open + " still open)");
    }
  }

  public ExceptionRecordResponse toResponse(ExceptionRecord record) {
    User recorder = record.getRecorder();
    Long taskId = record.getTask() == null ? null : record.getTask().getId();
    return new ExceptionRecordResponse(record.getId(), record.getOrder().getId(),
        record.getOrder().getOrderNo(), taskId, record.getExceptionType().name(), record.getStatus().name(),
        record.getDescription(), record.getResolution(),
        recorder == null ? null : recorder.getId(),
        recorder == null ? "系统" : recorder.getName(),
        record.getCreatedAt(), record.getUpdatedAt());
  }

  private void requireBusinessOwner(CurrentUser currentUser) {
    if (currentUser == null || !currentUser.hasRole(RoleType.BO)) {
      throw new IllegalArgumentException("Only a business owner (BO) can manage exception records");
    }
  }

  private String requireText(String value, String message) {
    String clean = cleanOptional(value);
    if (clean == null || clean.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    if (clean.length() > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException("Text must be at most " + MAX_TEXT_LENGTH + " characters");
    }
    return clean;
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
