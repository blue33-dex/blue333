package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.aop.AuditedAction;
import com.blue333.ordermgmt.domain.AuditLog;
import com.blue333.ordermgmt.domain.ClientReview;
import com.blue333.ordermgmt.domain.ConfirmationRecord;
import com.blue333.ordermgmt.domain.DeliveryArchive;
import com.blue333.ordermgmt.domain.DesignVersion;
import com.blue333.ordermgmt.domain.ExceptionRecord;
import com.blue333.ordermgmt.domain.FeedbackItem;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.AuditActionType;
import com.blue333.ordermgmt.domain.enums.AuditTargetType;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.dto.archive.ArchiveResponses.DeliveryArchiveResponse;
import com.blue333.ordermgmt.repository.AuditLogRepository;
import com.blue333.ordermgmt.repository.ClientReviewRepository;
import com.blue333.ordermgmt.repository.ConfirmationRecordRepository;
import com.blue333.ordermgmt.repository.DeliveryArchiveRepository;
import com.blue333.ordermgmt.repository.DesignVersionRepository;
import com.blue333.ordermgmt.repository.ExceptionRecordRepository;
import com.blue333.ordermgmt.repository.FeedbackItemRepository;
import com.blue333.ordermgmt.repository.MaterialTaskRepository;
import com.blue333.ordermgmt.repository.OrderRepository;
import com.blue333.ordermgmt.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A-19/A-28 delivery archive (P3-T07). Archiving is available only after the order reached
 * COMPLETED, every task is APPROVED, and the order was not archived before. The generated PDF is
 * stored in object storage and its key is kept in {@code delivery_archives}; the report can later be
 * exported through a presigned download (A-28).
 *
 * <p>Exception records (E-18) arrive with Phase 4; the "all exceptions CLOSED" rule will be added to
 * {@link DeliveryRules#requireDeliverable(Long)} together with that table.</p>
 */
@Service
public class ArchiveService {
  private static final String REPORT_OBJECT_PREFIX = "archives";
  private static final String REPORT_FILE_NAME = "archive-report.pdf";
  private static final String REPORT_CONTENT_TYPE = "application/pdf";

  private final OrderRepository orderRepository;
  private final MaterialTaskRepository taskRepository;
  private final DesignVersionRepository versionRepository;
  private final ClientReviewRepository clientReviewRepository;
  private final FeedbackItemRepository feedbackItemRepository;
  private final ConfirmationRecordRepository confirmationRecordRepository;
  private final DeliveryArchiveRepository deliveryArchiveRepository;
  private final AuditLogRepository auditLogRepository;
  private final UserRepository userRepository;
  private final DeliveryRules deliveryRules;
  private final ExceptionRecordService exceptionRecordService;
  private final ExceptionRecordRepository exceptionRecordRepository;
  private final FileStorageService fileStorageService;
  private final ArchiveReportGenerator reportGenerator;
  private final Clock clock;
  private final ZoneId businessZone;

  public ArchiveService(
      OrderRepository orderRepository,
      MaterialTaskRepository taskRepository,
      DesignVersionRepository versionRepository,
      ClientReviewRepository clientReviewRepository,
      FeedbackItemRepository feedbackItemRepository,
      ConfirmationRecordRepository confirmationRecordRepository,
      DeliveryArchiveRepository deliveryArchiveRepository,
      AuditLogRepository auditLogRepository,
      UserRepository userRepository,
      DeliveryRules deliveryRules,
      ExceptionRecordService exceptionRecordService,
      ExceptionRecordRepository exceptionRecordRepository,
      FileStorageService fileStorageService,
      ArchiveReportGenerator reportGenerator,
      Clock clock,
      @Value("${app.business-time-zone:Asia/Shanghai}") String businessTimeZone) {
    this.orderRepository = orderRepository;
    this.taskRepository = taskRepository;
    this.versionRepository = versionRepository;
    this.clientReviewRepository = clientReviewRepository;
    this.feedbackItemRepository = feedbackItemRepository;
    this.confirmationRecordRepository = confirmationRecordRepository;
    this.deliveryArchiveRepository = deliveryArchiveRepository;
    this.auditLogRepository = auditLogRepository;
    this.userRepository = userRepository;
    this.deliveryRules = deliveryRules;
    this.exceptionRecordService = exceptionRecordService;
    this.exceptionRecordRepository = exceptionRecordRepository;
    this.fileStorageService = fileStorageService;
    this.reportGenerator = reportGenerator;
    this.clock = clock;
    this.businessZone = ZoneId.of(businessTimeZone);
  }

  @Transactional
  @AuditedAction(action = AuditActionType.ARCHIVE_ORDER, targetType = AuditTargetType.ORDER,
      targetId = "#orderId", beforeState = "'COMPLETED'", afterState = "'COMPLETED'")
  public DeliveryArchiveResponse archiveOrder(Long orderId, CurrentUser currentUser) {
    requireRole(currentUser);
    Long positiveOrderId = requirePositive(orderId);
    Order order = orderRepository.findByIdForUpdate(positiveOrderId)
        .orElseThrow(() -> new ResourceNotFoundException("Order does not exist: " + positiveOrderId));
    if (order.getStatus() != OrderStatus.COMPLETED) {
      throw new InvalidStateTransitionException(
          "Only a COMPLETED order can be archived (current: " + order.getStatus() + ")");
    }
    deliveryRules.requireDeliverable(positiveOrderId);
    if (deliveryArchiveRepository.existsByOrderId(positiveOrderId)) {
      throw new InvalidStateTransitionException("This order is already archived");
    }
    // P4-T06: an archive is the final snapshot of the order, so open exceptions must be closed first.
    exceptionRecordService.requireAllClosed(positiveOrderId);
    User archiver = userRepository.findById(currentUser.id())
        .orElseThrow(() -> new AccessDeniedException("An authenticated user is required"));

    List<MaterialTask> tasks = taskRepository.findAllByOrderIdOrderByIdAsc(positiveOrderId);
    List<DesignVersion> versions = versionRepository.findAllForOrder(positiveOrderId);
    List<ClientReview> clientReviews = clientReviewRepository.findAllByOrderIdOrderByIdAsc(positiveOrderId);
    List<FeedbackItem> feedbacks = collectFeedbacks(versions);
    List<ConfirmationRecord> confirmations =
        confirmationRecordRepository.findAllByOrderIdOrderByIdAsc(positiveOrderId);
    ConfirmationRecord confirmation = confirmations.isEmpty() ? null : confirmations.get(confirmations.size() - 1);
    List<ExceptionRecord> exceptions = exceptionRecordRepository.findAllByOrderId(positiveOrderId);
    List<AuditLog> auditLogs = collectAuditLogs(order, tasks, versions, feedbacks, confirmations, exceptions);

    ArchiveReportData report = buildReport(order, tasks, versions, clientReviews, feedbacks, exceptions,
        auditLogs, confirmation);
    byte[] pdf = reportGenerator.generate(report);
    String objectKey = fileStorageService.generateObjectKey(
        REPORT_OBJECT_PREFIX + "/order-" + positiveOrderId, REPORT_FILE_NAME);
    fileStorageService.putObject(objectKey, pdf, REPORT_CONTENT_TYPE);

    DeliveryArchive archive = deliveryArchiveRepository.save(DeliveryArchive.create(
        order, archiver, objectKey, now()));
    return new DeliveryArchiveResponse(archive.getId(), order.getId(), archiver.getId(),
        archiver.getName(), archive.getArchivedAt(), true);
  }

  /** A-28: the stored object key for a completed archive, used by the presigned PDF export. */
  @Transactional(readOnly = true)
  public String archiveReportPath(Long orderId, CurrentUser currentUser) {
    requireRole(currentUser);
    Long positiveOrderId = requirePositive(orderId);
    return deliveryArchiveRepository.findByOrderId(positiveOrderId)
        .orElseThrow(() -> new ResourceNotFoundException("No delivery archive exists for order: " + positiveOrderId))
        .getArchiveReportPath();
  }

  private List<FeedbackItem> collectFeedbacks(List<DesignVersion> versions) {
    List<FeedbackItem> items = new ArrayList<>();
    for (DesignVersion version : versions) {
      items.addAll(feedbackItemRepository.findAllByVersionIdOrderByCreatedAtAscIdAsc(version.getId()));
    }
    return items;
  }

  private List<AuditLog> collectAuditLogs(
      Order order,
      List<MaterialTask> tasks,
      List<DesignVersion> versions,
      List<FeedbackItem> feedbacks,
      List<ConfirmationRecord> confirmations,
      List<ExceptionRecord> exceptions) {
    List<AuditLog> logs = new ArrayList<>();
    logs.addAll(auditLogRepository.findAllForTargets(AuditTargetType.ORDER, List.of(order.getId())));
    addTargetLogs(logs, AuditTargetType.MATERIAL_TASK,
        tasks.stream().map(MaterialTask::getId).toList());
    addTargetLogs(logs, AuditTargetType.DESIGN_VERSION,
        versions.stream().map(DesignVersion::getId).toList());
    addTargetLogs(logs, AuditTargetType.FEEDBACK_ITEM,
        feedbacks.stream().map(FeedbackItem::getId).toList());
    addTargetLogs(logs, AuditTargetType.CONFIRMATION_RECORD,
        confirmations.stream().map(ConfirmationRecord::getId).toList());
    addTargetLogs(logs, AuditTargetType.EXCEPTION_RECORD,
        exceptions.stream().map(ExceptionRecord::getId).toList());
    logs.sort(Comparator.comparing(AuditLog::getOperatedAt)
        .thenComparing(log -> log.getId() == null ? 0L : log.getId()));
    return logs;
  }

  private void addTargetLogs(List<AuditLog> target, AuditTargetType targetType, List<Long> targetIds) {
    if (targetIds.isEmpty()) {
      return;
    }
    target.addAll(auditLogRepository.findAllForTargets(targetType, targetIds));
  }

  private ArchiveReportData buildReport(
      Order order,
      List<MaterialTask> tasks,
      List<DesignVersion> versions,
      List<ClientReview> clientReviews,
      List<FeedbackItem> feedbacks,
      List<ExceptionRecord> exceptions,
      List<AuditLog> auditLogs,
      ConfirmationRecord confirmation) {
    List<ArchiveReportData.TaskRow> taskRows = tasks.stream()
        .map(task -> new ArchiveReportData.TaskRow(
            task.getMaterialSpec().getMaterialType().name(), task.getMaterialSpec().getSpecSize(),
            name(task.getAssignee()), task.getPriority() == null ? null : task.getPriority().name(),
            task.getStatus().name(), task.getTaskDeadline()))
        .toList();
    List<ArchiveReportData.VersionRow> versionRows = versions.stream()
        .map(version -> new ArchiveReportData.VersionRow(
            version.getVersionNo(), version.getTask().getMaterialSpec().getMaterialType().name(),
            version.getTask().getMaterialSpec().getSpecSize(), version.getStatus().name(),
            name(version.getUploader()), version.getSubmittedAt(), version.isOverdue()))
        .toList();
    List<ArchiveReportData.ClientReviewRow> reviewRows = clientReviews.stream()
        .map(review -> new ArchiveReportData.ClientReviewRow(
            review.getVersion().getVersionNo(), review.getStatus().name(), name(review.getInitiator()),
            review.getStartedAt(), review.getCompletedAt()))
        .toList();
    List<ArchiveReportData.FeedbackRow> feedbackRows = feedbacks.stream()
        .map(item -> new ArchiveReportData.FeedbackRow(
            item.getFeedbackNo(), item.getVersion().getVersionNo(), item.getFeedbackType().name(),
            item.getSource().name(), item.getStatus().name(), name(item.getCreatedBy()), item.getContent()))
        .toList();
    ArchiveReportData.ConfirmationRow confirmationRow = confirmation == null ? null
        : new ArchiveReportData.ConfirmationRow(confirmation.getVersion().getVersionNo(),
            confirmation.getConfirmerName(), confirmation.getConfirmedAt());
    List<ArchiveReportData.ExceptionRow> exceptionRows = exceptions.stream()
        .map(record -> new ArchiveReportData.ExceptionRow(
            record.getExceptionType().name(), record.getStatus().name(),
            record.getRecorder() == null ? "系统" : name(record.getRecorder()),
            record.getDescription(), record.getResolution(), record.getCreatedAt()))
        .toList();
    List<ArchiveReportData.TimelineRow> timelineRows = auditLogs.stream()
        .map(log -> new ArchiveReportData.TimelineRow(
            log.getOperatedAt(), log.getActionType().name(), name(log.getOperator()),
            log.getTargetType() + "#" + log.getTargetId(), state(log), log.getResult()))
        .toList();

    return new ArchiveReportData(order.getOrderNo(), order.getCustomerName(), order.getUseScene(),
        order.getDeliveryDeadline(), order.getRemark(), order.getStatus().name(),
        name(order.getAm()), order.getCreatedAt(), taskRows, versionRows, reviewRows, feedbackRows,
        exceptionRows, confirmationRow, timelineRows);
  }

  private static String state(AuditLog log) {
    if (log.getBeforeState() == null && log.getAfterState() == null) {
      return null;
    }
    return (log.getBeforeState() == null ? "-" : log.getBeforeState()) + " -> "
        + (log.getAfterState() == null ? "-" : log.getAfterState());
  }

  private static String name(User user) {
    return user == null ? null : user.getName();
  }

  private void requireRole(CurrentUser currentUser) {
    if (currentUser == null || currentUser.id() == null || !currentUser.hasRole(RoleType.BO)) {
      throw new AccessDeniedException("Only a business owner can archive orders or export archive reports");
    }
  }

  private static Long requirePositive(Long orderId) {
    if (orderId == null || orderId <= 0) {
      throw new IllegalArgumentException("orderId must be a positive value");
    }
    return orderId;
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock.withZone(businessZone));
  }
}
