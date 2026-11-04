package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.aop.AuditedAction;
import com.blue333.ordermgmt.domain.DesignVersion;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.ReleaseRecord;
import com.blue333.ordermgmt.domain.ReviewRecord;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.AuditActionType;
import com.blue333.ordermgmt.domain.enums.AuditTargetType;
import com.blue333.ordermgmt.domain.enums.FeedbackStatus;
import com.blue333.ordermgmt.domain.enums.MaterialTaskStatus;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.domain.enums.ReviewResult;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.domain.enums.VersionStatus;
import com.blue333.ordermgmt.event.VersionRejectedEvent;
import com.blue333.ordermgmt.event.VersionReleasedEvent;
import com.blue333.ordermgmt.repository.DesignVersionRepository;
import com.blue333.ordermgmt.repository.FeedbackItemRepository;
import com.blue333.ordermgmt.repository.MaterialTaskRepository;
import com.blue333.ordermgmt.repository.ReleaseRecordRepository;
import com.blue333.ordermgmt.repository.ReviewRecordRepository;
import com.blue333.ordermgmt.repository.UserRepository;
import com.blue333.ordermgmt.util.XSSCleaner;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * M4 internal review (BO-05, BO-06, DS-06): BO reads the version history and comparison, releases a
 * reviewed version to client review or rejects it back to the designer, and DS marks a
 * client-confirmed version as the delivered final version.
 *
 * <p><b>CONFLICT-01:</b> releasing a version never approves the material task. The task stays
 * {@code PENDING_APPROVAL} until client confirmation in Phase 3.</p>
 */
@Service
public class InternalReviewService {
  public static final int MAX_COMMENT_LENGTH = 5000;

  private final MaterialTaskRepository taskRepository;
  private final DesignVersionRepository versionRepository;
  private final ReviewRecordRepository reviewRecordRepository;
  private final ReleaseRecordRepository releaseRecordRepository;
  private final FeedbackItemRepository feedbackItemRepository;
  private final UserRepository userRepository;
  private final VersionStateMachine versionStateMachine;
  private final MaterialTaskStateMachine taskStateMachine;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;
  private final ZoneId businessZone;

  public InternalReviewService(
      MaterialTaskRepository taskRepository,
      DesignVersionRepository versionRepository,
      ReviewRecordRepository reviewRecordRepository,
      ReleaseRecordRepository releaseRecordRepository,
      FeedbackItemRepository feedbackItemRepository,
      UserRepository userRepository,
      VersionStateMachine versionStateMachine,
      MaterialTaskStateMachine taskStateMachine,
      ApplicationEventPublisher eventPublisher,
      Clock clock,
      @Value("${app.business-time-zone:Asia/Shanghai}") String businessTimeZone) {
    this.taskRepository = taskRepository;
    this.versionRepository = versionRepository;
    this.reviewRecordRepository = reviewRecordRepository;
    this.releaseRecordRepository = releaseRecordRepository;
    this.feedbackItemRepository = feedbackItemRepository;
    this.userRepository = userRepository;
    this.versionStateMachine = versionStateMachine;
    this.taskStateMachine = taskStateMachine;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
    this.businessZone = ZoneId.of(businessTimeZone);
  }

  /**
   * A-11 / BO-06. Version {@code PENDING_INTERNAL_REVIEW → RELEASED} with a PASS review record and a
   * release record. The material task deliberately keeps {@code PENDING_APPROVAL} (CONFLICT-01).
   */
  @Transactional
  @AuditedAction(action = AuditActionType.RELEASE_VERSION, targetType = AuditTargetType.DESIGN_VERSION,
      targetId = "#versionId", beforeState = "'PENDING_INTERNAL_REVIEW'", afterState = "#result.status",
      remark = "'version_no=' + #result.versionNo")
  public DesignVersion release(Long versionId, String releaseRemark, CurrentUser currentUser) {
    requireRole(currentUser, RoleType.BO, "Only a business owner can release versions");
    ReviewTarget target = lockVersionUnderReview(versionId);
    String remark = cleanOptional(releaseRemark, "release_remark");

    versionStateMachine.transition(target.version(), VersionStatus.RELEASED);
    DesignVersion saved = versionRepository.save(target.version());
    User reviewer = requireUser(currentUser);
    reviewRecordRepository.save(
        ReviewRecord.create(saved, target.task(), reviewer, ReviewResult.PASS, remark));
    releaseRecordRepository.save(ReleaseRecord.create(saved, reviewer, remark, now()));
    publishAfterCommit(new VersionReleasedEvent(recipients(target), saved.getId(),
        target.task().getOrder().getOrderNo(), saved.getVersionNo()));
    return saved;
  }

  /**
   * A-12 / BO-06. Rejects a version back to the designer: the version keeps its status for history,
   * the review decision is recorded with the mandatory comment, and the task returns to revision.
   */
  @Transactional
  @AuditedAction(action = AuditActionType.REJECT_VERSION, targetType = AuditTargetType.DESIGN_VERSION,
      targetId = "#versionId", beforeState = "'PENDING_INTERNAL_REVIEW'", afterState = "#result.status",
      remark = "'version_no=' + #result.versionNo")
  public DesignVersion reject(Long versionId, String reviewComment, CurrentUser currentUser) {
    requireRole(currentUser, RoleType.BO, "Only a business owner can reject versions");
    ReviewTarget target = lockVersionUnderReview(versionId);
    String comment = requireComment(reviewComment, "A review comment is required when rejecting a version");

    // A-12: the rejected version is never rolled back; only the task re-enters revision.
    taskStateMachine.transition(target.task(), MaterialTaskStatus.PENDING_REVISE);
    taskRepository.save(target.task());
    User reviewer = requireUser(currentUser);
    reviewRecordRepository.save(
        ReviewRecord.create(target.version(), target.task(), reviewer, ReviewResult.REJECT, comment));
    publishAfterCommit(new VersionRejectedEvent(recipients(target), target.version().getId(),
        target.task().getOrder().getOrderNo(), target.version().getVersionNo(), comment));
    return target.version();
  }

  /**
   * A-17 / DS-06. Only a client-confirmed version can become the delivered final version, and an
   * order can hold at most one final version. The task is already approved by confirmVersion in
   * Phase 3, so this action never changes the task status. P3-T17: marking the final version also
   * expires every still-unresolved feedback of earlier versions of the same order.
   */
  @Transactional
  @AuditedAction(action = AuditActionType.MARK_FINAL, targetType = AuditTargetType.DESIGN_VERSION,
      targetId = "#versionId", beforeState = "'CLIENT_CONFIRMED'", afterState = "#result.status",
      remark = "'version_no=' + #result.versionNo")
  public DesignVersion markFinal(Long versionId, CurrentUser currentUser) {
    requireRole(currentUser, RoleType.DS, "Only a designer can mark a version as final");
    Long positiveVersionId = requirePositive(versionId, "versionId");
    Long taskId = versionRepository.findTaskIdById(positiveVersionId)
        .orElseThrow(() -> versionNotFound(positiveVersionId));
    MaterialTask task = taskRepository.findByIdForUpdate(taskId)
        .orElseThrow(() -> versionNotFound(positiveVersionId));
    DesignVersion version = versionRepository.findByIdForUpdate(positiveVersionId)
        .orElseThrow(() -> versionNotFound(positiveVersionId));

    // BR-13: a version outside the caller's task (and not uploaded by the caller) is reported as missing.
    if (!sameUser(task.getAssignee(), currentUser) && !sameUser(version.getUploader(), currentUser)) {
      throw versionNotFound(positiveVersionId);
    }
    if (version.isFinalVersion()) {
      throw new InvalidStateTransitionException("This version is already marked as final");
    }
    if (version.getStatus() != VersionStatus.CLIENT_CONFIRMED) {
      throw new InvalidStateTransitionException("Only a client-confirmed version can be marked as final (current: "
          + version.getStatus() + ")");
    }
    if (versionRepository.countFinalVersionsInOrder(task.getOrder().getId()) > 0) {
      throw new InvalidStateTransitionException("Another version of this order is already marked as final");
    }
    versionStateMachine.transition(version, VersionStatus.FINAL);
    version.markFinal();
    DesignVersion saved = versionRepository.save(version);
    // EN-04: earlier-version feedback is superseded by the order's final version.
    feedbackItemRepository.expireUnresolvedForOrder(task.getOrder().getId(), saved.getId(),
        FeedbackStatus.UNRESOLVED, FeedbackStatus.EXPIRED, now());
    return saved;
  }

  /**
   * Locks the task first and then the version (the same order used by upload and submit), and
   * validates that the version is the one currently awaiting internal review.
   */
  private ReviewTarget lockVersionUnderReview(Long versionId) {
    Long positiveVersionId = requirePositive(versionId, "versionId");
    Long taskId = versionRepository.findTaskIdById(positiveVersionId)
        .orElseThrow(() -> versionNotFound(positiveVersionId));
    MaterialTask task = taskRepository.findByIdForUpdate(taskId)
        .orElseThrow(() -> versionNotFound(positiveVersionId));
    DesignVersion version = versionRepository.findByIdForUpdate(positiveVersionId)
        .orElseThrow(() -> versionNotFound(positiveVersionId));

    if (version.getStatus() != VersionStatus.PENDING_INTERNAL_REVIEW) {
      throw new InvalidStateTransitionException(
          "Only a version pending internal review can be reviewed (current: " + version.getStatus() + ")");
    }
    if (task.getStatus() != MaterialTaskStatus.PENDING_APPROVAL) {
      throw new InvalidStateTransitionException(
          "The material task is not awaiting internal review (current: " + task.getStatus() + ")");
    }
    if (task.getOrder().getStatus() != OrderStatus.IN_PRODUCTION) {
      throw new InvalidStateTransitionException("Versions can only be reviewed while the order is in production");
    }
    // A rejected older version stays PENDING_INTERNAL_REVIEW for history, so stale ones must be refused.
    if (versionRepository.countNewerSubmittedVersions(taskId, positiveVersionId) > 0) {
      throw new InvalidStateTransitionException("Only the latest submitted version of this task can be reviewed");
    }
    return new ReviewTarget(task, version);
  }

  private void requireRole(CurrentUser currentUser, RoleType roleType, String message) {
    if (currentUser == null || currentUser.id() == null || !currentUser.hasRole(roleType)) {
      throw new AccessDeniedException(message);
    }
  }

  private User requireUser(CurrentUser currentUser) {
    return userRepository.findById(currentUser.id())
        .orElseThrow(() -> new AccessDeniedException("An authenticated user is required"));
  }

  private List<Long> recipients(ReviewTarget target) {
    List<Long> recipientIds = new ArrayList<>();
    recipientIds.add(target.version().getUploader().getId());
    recipientIds.add(target.task().getAssignee().getId());
    recipientIds.add(target.task().getOrder().getAm().getId());
    return recipientIds.stream().filter(Objects::nonNull).distinct().toList();
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock.withZone(businessZone));
  }

  private boolean sameUser(User user, CurrentUser currentUser) {
    return user != null && user.getId() != null && Objects.equals(user.getId(), currentUser.id());
  }

  private static Long requirePositive(Long id, String label) {
    if (id == null || id <= 0) {
      throw new IllegalArgumentException(label + " must be a positive value");
    }
    return id;
  }

  private static String cleanOptional(String value, String field) {
    if (value == null) {
      return null;
    }
    String cleaned = XSSCleaner.clean(value).trim();
    if (cleaned.isEmpty()) {
      return null;
    }
    requireMaxLength(cleaned, field);
    return cleaned;
  }

  private static String requireComment(String value, String message) {
    String cleaned = cleanOptional(value, "review_comment");
    if (cleaned == null) {
      throw new IllegalArgumentException(message);
    }
    return cleaned;
  }

  private static void requireMaxLength(String value, String field) {
    if (value.length() > MAX_COMMENT_LENGTH) {
      throw new IllegalArgumentException(field + " must be at most " + MAX_COMMENT_LENGTH + " characters");
    }
  }

  private static ResourceNotFoundException versionNotFound(Long versionId) {
    return new ResourceNotFoundException("Design version does not exist: " + versionId);
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

  private record ReviewTarget(MaterialTask task, DesignVersion version) {
  }
}
