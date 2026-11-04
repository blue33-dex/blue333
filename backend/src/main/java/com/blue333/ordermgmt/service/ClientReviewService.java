package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.aop.AuditedAction;
import com.blue333.ordermgmt.domain.ClientReview;
import com.blue333.ordermgmt.domain.ConfirmationRecord;
import com.blue333.ordermgmt.domain.DesignVersion;
import com.blue333.ordermgmt.domain.FeedbackItem;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.AuditActionType;
import com.blue333.ordermgmt.domain.enums.AuditTargetType;
import com.blue333.ordermgmt.domain.enums.ClientReviewStatus;
import com.blue333.ordermgmt.domain.enums.FeedbackSource;
import com.blue333.ordermgmt.domain.enums.FeedbackStatus;
import com.blue333.ordermgmt.domain.enums.FeedbackType;
import com.blue333.ordermgmt.domain.enums.MaterialTaskStatus;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.domain.enums.VersionStatus;
import com.blue333.ordermgmt.dto.clientreview.ClientReviewResponses.ClientReviewResponse;
import com.blue333.ordermgmt.dto.clientreview.ClientReviewResponses.ConfirmVersionResponse;
import com.blue333.ordermgmt.dto.clientreview.ClientReviewResponses.FeedbackResponse;
import com.blue333.ordermgmt.dto.version.VersionResponses.VersionResponse;
import com.blue333.ordermgmt.repository.ClientReviewRepository;
import com.blue333.ordermgmt.repository.DesignVersionRepository;
import com.blue333.ordermgmt.repository.FeedbackItemRepository;
import com.blue333.ordermgmt.repository.MaterialTaskRepository;
import com.blue333.ordermgmt.repository.UserRepository;
import com.blue333.ordermgmt.util.XSSCleaner;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 3 client-review flow (P3-T04/T05/T06):
 *
 * <ol>
 *   <li>A-13 opens a client review for a RELEASED version (RELEASED → IN_CLIENT_REVIEW).</li>
 *   <li>A-14 records version-bound feedback; revision feedback returns the task to PENDING_REVISE.</li>
 *   <li>A-15 lets the assigned designer resolve an unresolved feedback item.</li>
 *   <li>A-16 confirms the version (IN_CLIENT_REVIEW → CLIENT_CONFIRMED), writes the immutable
 *       confirmation record, and — CONFLICT-01 — finally moves the task to APPROVED.</li>
 * </ol>
 */
@Service
public class ClientReviewService {
  public static final int MAX_TEXT_LENGTH = 5000;
  public static final int MAX_CONFIRMER_NAME_LENGTH = 255;
  private static final Set<FeedbackType> REVISION_FEEDBACK_TYPES =
      Set.of(FeedbackType.MODIFICATION_SUGGESTION, FeedbackType.REJECTION);

  private final MaterialTaskRepository taskRepository;
  private final DesignVersionRepository versionRepository;
  private final ClientReviewRepository clientReviewRepository;
  private final FeedbackItemRepository feedbackItemRepository;
  private final UserRepository userRepository;
  private final ConfirmationRecordService confirmationRecordService;
  private final VersionStateMachine versionStateMachine;
  private final MaterialTaskStateMachine taskStateMachine;
  private final VersionQueryService versionQueryService;
  private final AccessControlService accessControl;
  private final FeedbackNoGenerator feedbackNoGenerator;
  private final Clock clock;
  private final ZoneId businessZone;

  public ClientReviewService(
      MaterialTaskRepository taskRepository,
      DesignVersionRepository versionRepository,
      ClientReviewRepository clientReviewRepository,
      FeedbackItemRepository feedbackItemRepository,
      UserRepository userRepository,
      ConfirmationRecordService confirmationRecordService,
      VersionStateMachine versionStateMachine,
      MaterialTaskStateMachine taskStateMachine,
      VersionQueryService versionQueryService,
      AccessControlService accessControl,
      FeedbackNoGenerator feedbackNoGenerator,
      Clock clock,
      @Value("${app.business-time-zone:Asia/Shanghai}") String businessTimeZone) {
    this.taskRepository = taskRepository;
    this.versionRepository = versionRepository;
    this.clientReviewRepository = clientReviewRepository;
    this.feedbackItemRepository = feedbackItemRepository;
    this.userRepository = userRepository;
    this.confirmationRecordService = confirmationRecordService;
    this.versionStateMachine = versionStateMachine;
    this.taskStateMachine = taskStateMachine;
    this.versionQueryService = versionQueryService;
    this.accessControl = accessControl;
    this.feedbackNoGenerator = feedbackNoGenerator;
    this.clock = clock;
    this.businessZone = ZoneId.of(businessTimeZone);
  }

  /** A-13: the owning AM shares a released version with the client (RELEASED → IN_CLIENT_REVIEW). */
  @Transactional
  public ClientReviewResponse initiateClientReview(Long versionId, CurrentUser currentUser) {
    requireRole(currentUser, RoleType.AM, "Only an account manager can initiate a client review");
    ReviewTarget target = lockVersion(versionId);
    requireOrderOwner(target.task().getOrder(), currentUser, versionId);

    if (target.task().getOrder().getStatus() != OrderStatus.IN_PRODUCTION) {
      throw new InvalidStateTransitionException("Client review can only be initiated while the order is in production");
    }
    if (target.version().getStatus() != VersionStatus.RELEASED) {
      throw new InvalidStateTransitionException(
          "Only a released version can start a client review (current: " + target.version().getStatus() + ")");
    }
    // A newer version supersedes this one even if the old release hasn't been picked up yet.
    if (versionRepository.countNewerSubmittedVersions(target.task().getId(), target.version().getId()) > 0) {
      throw new InvalidStateTransitionException("Only the latest submitted version of this task can start a client review");
    }
    if (clientReviewRepository.existsByVersionId(target.version().getId())) {
      throw new InvalidStateTransitionException("A client review already exists for this version");
    }

    versionStateMachine.transition(target.version(), VersionStatus.IN_CLIENT_REVIEW);
    versionRepository.save(target.version());
    ClientReview review = clientReviewRepository.save(ClientReview.start(
        target.version(), target.task().getOrder(), requireUser(currentUser), now()));
    return toClientReview(review);
  }

  /**
   * A-14: records a version-bound feedback item. MODIFICATION_SUGGESTION and REJECTION send the
   * material task back to PENDING_REVISE so the designer can iterate (DS-05 linkage).
   */
  @Transactional
  @AuditedAction(action = AuditActionType.RECORD_FEEDBACK, targetType = AuditTargetType.FEEDBACK_ITEM,
      targetId = "#result.id", afterState = "#result.status",
      remark = "'feedback_no=' + #result.feedbackNo")
  public FeedbackResponse recordFeedback(
      Long versionId, FeedbackType feedbackType, FeedbackSource source, String content,
      CurrentUser currentUser) {
    requireRole(currentUser, RoleType.AM, "Only an account manager can record client feedback");
    if (feedbackType == null) {
      throw new IllegalArgumentException("A feedback type is required");
    }
    FeedbackSource effectiveSource = source == null ? FeedbackSource.CLIENT : source;

    ReviewTarget target = lockVersion(versionId);
    requireOrderOwner(target.task().getOrder(), currentUser, versionId);
    if (target.task().getOrder().getStatus() != OrderStatus.IN_PRODUCTION) {
      throw new InvalidStateTransitionException("Feedback can only be recorded while the order is in production");
    }
    if (target.version().getStatus() != VersionStatus.IN_CLIENT_REVIEW) {
      throw new InvalidStateTransitionException(
          "Feedback can only be recorded for a version in client review (current: "
              + target.version().getStatus() + ")");
    }
    ClientReview review = clientReviewRepository.findByVersionIdForUpdate(target.version().getId())
        .orElseThrow(() -> new InvalidStateTransitionException("No client review exists for this version"));
    if (review.getStatus() != ClientReviewStatus.IN_REVIEW) {
      throw new InvalidStateTransitionException("The client review for this version is already completed");
    }

    String cleaned = requireText(clean(content), "Feedback content is required");
    requireMaxLength(cleaned, "content", MAX_TEXT_LENGTH);
    FeedbackItem item = feedbackItemRepository.save(FeedbackItem.create(
        feedbackNoGenerator.next(), cleaned, feedbackType, effectiveSource,
        target.version(), review, requireUser(currentUser)));

    if (REVISION_FEEDBACK_TYPES.contains(feedbackType)) {
      requireRevisionTransition(target.task());
      taskStateMachine.transition(target.task(), MaterialTaskStatus.PENDING_REVISE);
      taskRepository.save(target.task());
    }
    return toFeedback(item);
  }

  /** A-15: the designer assigned to the task marks an unresolved item as resolved. */
  @Transactional
  public FeedbackResponse resolveFeedback(Long feedbackId, CurrentUser currentUser) {
    requireRole(currentUser, RoleType.DS, "Only a designer can resolve feedback");
    Long positiveFeedbackId = requirePositive(feedbackId, "feedbackId");
    FeedbackItem reference = feedbackItemRepository.findById(positiveFeedbackId)
        .orElseThrow(() -> feedbackNotFound(positiveFeedbackId));
    Long versionId = reference.getVersion().getId();
    Long taskId = versionRepository.findTaskIdById(versionId)
        .orElseThrow(() -> versionNotFound(versionId));
    MaterialTask task = taskRepository.findByIdForUpdate(taskId)
        .orElseThrow(() -> feedbackNotFound(positiveFeedbackId));
    DesignVersion version = versionRepository.findByIdForUpdate(versionId)
        .orElseThrow(() -> feedbackNotFound(positiveFeedbackId));
    FeedbackItem item = feedbackItemRepository.findByIdForUpdate(positiveFeedbackId)
        .orElseThrow(() -> feedbackNotFound(positiveFeedbackId));

    // BR-13: a designer outside the task must not learn that the feedback exists.
    if (!sameUser(task.getAssignee(), currentUser)) {
      throw feedbackNotFound(positiveFeedbackId);
    }
    if (task.getOrder().getStatus() != OrderStatus.IN_PRODUCTION) {
      throw new InvalidStateTransitionException("Feedback can only be resolved while the order is in production");
    }
    if (version.getStatus() == VersionStatus.FINAL) {
      throw new InvalidStateTransitionException("Feedback of a final version cannot be changed");
    }
    if (item.getStatus() == FeedbackStatus.EXPIRED) {
      throw new InvalidStateTransitionException("Expired feedback cannot be marked as resolved");
    }
    if (item.getStatus() == FeedbackStatus.RESOLVED) {
      throw new InvalidStateTransitionException("This feedback item is already resolved");
    }
    item.resolve();
    return toFeedback(feedbackItemRepository.save(item));
  }

  /** A-27: feedback list of one version, filtered by the caller's visibility. */
  @Transactional(readOnly = true)
  public List<FeedbackResponse> listFeedbacks(Long versionId, CurrentUser currentUser) {
    DesignVersion version = accessControl.findVisibleVersion(versionId, currentUser);
    return feedbackItemRepository.findAllByVersionIdOrderByCreatedAtAscIdAsc(version.getId()).stream()
        .map(this::toFeedback)
        .toList();
  }

  /**
   * A-16 / CONFLICT-01: confirms the client-approved version and — for the first time in the
   * lifecycle — moves the material task to APPROVED. Unresolved feedback only produces a warning.
   */
  @Transactional
  @AuditedAction(action = AuditActionType.CONFIRM_VERSION, targetType = AuditTargetType.CONFIRMATION_RECORD,
      targetId = "#result.confirmationRecordId", beforeState = "'IN_CLIENT_REVIEW'",
      afterState = "#result.version.status",
      remark = "'version_no=' + #result.version.versionNo")
  public ConfirmVersionResponse confirmVersion(
      Long versionId, String confirmerName, CurrentUser currentUser) {
    requireRole(currentUser, RoleType.AM, "Only an account manager can confirm a client version");
    ReviewTarget target = lockVersion(versionId);
    requireOrderOwner(target.task().getOrder(), currentUser, versionId);

    if (target.task().getOrder().getStatus() != OrderStatus.IN_PRODUCTION) {
      throw new InvalidStateTransitionException("A version can only be confirmed while the order is in production");
    }
    if (target.version().getStatus() != VersionStatus.IN_CLIENT_REVIEW) {
      throw new InvalidStateTransitionException(
          "Only a version in client review can be confirmed (current: " + target.version().getStatus() + ")");
    }
    if (versionRepository.countNewerSubmittedVersions(target.task().getId(), target.version().getId()) > 0) {
      throw new InvalidStateTransitionException("Only the latest submitted version of this task can be confirmed");
    }
    if (target.task().getStatus() != MaterialTaskStatus.PENDING_APPROVAL) {
      throw new InvalidStateTransitionException(
          "The material task must be awaiting approval before confirmation (current: "
              + target.task().getStatus() + ")");
    }
    ClientReview review = clientReviewRepository.findByVersionIdForUpdate(target.version().getId())
        .orElseThrow(() -> new InvalidStateTransitionException("No client review exists for this version"));
    if (review.getStatus() != ClientReviewStatus.IN_REVIEW) {
      throw new InvalidStateTransitionException("The client review for this version is already completed");
    }

    String confirmer = requireText(clean(confirmerName), "A confirmer name is required");
    requireMaxLength(confirmer, "confirmer_name", MAX_CONFIRMER_NAME_LENGTH);

    LocalDateTime confirmedAt = now();
    ConfirmationRecord record = confirmationRecordService.create(
        target.version(), target.task().getOrder(), confirmer, confirmedAt);

    versionStateMachine.transition(target.version(), VersionStatus.CLIENT_CONFIRMED);
    versionRepository.save(target.version());
    // CONFLICT-01: this is the only legitimate PENDING_APPROVAL → APPROVED trigger.
    taskStateMachine.transition(target.task(), MaterialTaskStatus.APPROVED);
    taskRepository.save(target.task());
    review.complete(confirmedAt);
    clientReviewRepository.save(review);

    long unresolved = feedbackItemRepository.countByVersionIdAndStatus(
        target.version().getId(), FeedbackStatus.UNRESOLVED);
    String warning = unresolved == 0 ? null
        : unresolved + " unresolved feedback item(s) remain on version " + target.version().getVersionNo();
    VersionResponse version = versionQueryService.detail(target.version().getId(), currentUser);
    return new ConfirmVersionResponse(version, record.getId(), confirmer, confirmedAt, unresolved, warning);
  }

  private ReviewTarget lockVersion(Long versionId) {
    Long positiveVersionId = requirePositive(versionId, "versionId");
    Long taskId = versionRepository.findTaskIdById(positiveVersionId)
        .orElseThrow(() -> versionNotFound(positiveVersionId));
    MaterialTask task = taskRepository.findByIdForUpdate(taskId)
        .orElseThrow(() -> versionNotFound(positiveVersionId));
    DesignVersion version = versionRepository.findByIdForUpdate(positiveVersionId)
        .orElseThrow(() -> versionNotFound(positiveVersionId));
    return new ReviewTarget(task, version);
  }

  /**
   * A revision-requiring feedback is only meaningful while the task awaits approval. If the task is
   * already PENDING_REVISE the feedback simply adds to the existing rework loop.
   */
  private void requireRevisionTransition(MaterialTask task) {
    if (task.getStatus() == MaterialTaskStatus.PENDING_REVISE) {
      return;
    }
    if (task.getStatus() != MaterialTaskStatus.PENDING_APPROVAL) {
      throw new InvalidStateTransitionException(
          "Feedback requiring changes cannot be recorded for a task in " + task.getStatus());
    }
  }

  private void requireOrderOwner(Order order, CurrentUser currentUser, Long versionId) {
    if (!sameUser(order.getAm(), currentUser)) {
      // BR-13: an order outside the AM's scope is reported as a missing version.
      throw versionNotFound(versionId);
    }
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

  private ClientReviewResponse toClientReview(ClientReview review) {
    return new ClientReviewResponse(review.getId(), review.getVersion().getId(),
        review.getVersion().getVersionNo(), review.getOrder().getId(), review.getStatus().name(),
        review.getInitiator().getId(), review.getInitiator().getName(),
        review.getStartedAt(), review.getCompletedAt());
  }

  private FeedbackResponse toFeedback(FeedbackItem item) {
    return new FeedbackResponse(item.getId(), item.getFeedbackNo(), item.getVersion().getId(),
        item.getVersion().getVersionNo(), item.getClientReview().getId(), item.getContent(),
        item.getFeedbackType().name(), item.getSource().name(), item.getStatus().name(),
        item.getCreatedBy().getId(), item.getCreatedBy().getName(),
        item.getCreatedAt(), item.getUpdatedAt());
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

  private static String clean(String value) {
    if (value == null) {
      return null;
    }
    String cleaned = XSSCleaner.clean(value).trim();
    return cleaned.isEmpty() ? null : cleaned;
  }

  private static String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }

  private static void requireMaxLength(String value, String field, int maxLength) {
    if (value != null && value.length() > maxLength) {
      throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
    }
  }

  private static ResourceNotFoundException versionNotFound(Long versionId) {
    return new ResourceNotFoundException("Design version does not exist: " + versionId);
  }

  private static ResourceNotFoundException feedbackNotFound(Long feedbackId) {
    return new ResourceNotFoundException("Feedback item does not exist: " + feedbackId);
  }

  private record ReviewTarget(MaterialTask task, DesignVersion version) {
  }
}
