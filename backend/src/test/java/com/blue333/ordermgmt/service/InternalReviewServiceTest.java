package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blue333.ordermgmt.domain.DesignVersion;
import com.blue333.ordermgmt.domain.MaterialSpec;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.ReleaseRecord;
import com.blue333.ordermgmt.domain.ReviewRecord;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.MaterialTaskStatus;
import com.blue333.ordermgmt.domain.enums.FeedbackStatus;
import com.blue333.ordermgmt.domain.enums.MaterialType;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.domain.enums.Priority;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

/** Service 7 rules: A-11 release, A-12 reject, A-17 mark-final and the CONFLICT-01 guarantee. */
class InternalReviewServiceTest {
  private static final long TASK_ID = 10L;
  private static final long VERSION_ID = 50L;
  private static final long ORDER_ID = 1L;
  private static final long DS_ID = 3L;
  private static final long OTHER_DS_ID = 4L;
  private static final long BO_ID = 2L;
  private static final long AM_ID = 5L;
  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");

  private MaterialTaskRepository taskRepository;
  private DesignVersionRepository versionRepository;
  private ReviewRecordRepository reviewRecordRepository;
  private ReleaseRecordRepository releaseRecordRepository;
  private FeedbackItemRepository feedbackItemRepository;
  private UserRepository userRepository;
  private ApplicationEventPublisher events;
  private InternalReviewService service;

  private User ds;
  private User bo;
  private Order order;
  private MaterialTask task;

  @BeforeEach
  void setUp() {
    taskRepository = mock(MaterialTaskRepository.class);
    versionRepository = mock(DesignVersionRepository.class);
    reviewRecordRepository = mock(ReviewRecordRepository.class);
    releaseRecordRepository = mock(ReleaseRecordRepository.class);
    feedbackItemRepository = mock(FeedbackItemRepository.class);
    userRepository = mock(UserRepository.class);
    events = mock(ApplicationEventPublisher.class);
    service = new InternalReviewService(taskRepository, versionRepository, reviewRecordRepository,
        releaseRecordRepository, feedbackItemRepository, userRepository, new VersionStateMachine(),
        new MaterialTaskStateMachine(), events, Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Shanghai");

    ds = user(DS_ID);
    bo = user(BO_ID);
    when(userRepository.findById(BO_ID)).thenReturn(Optional.of(bo));
    when(userRepository.findById(DS_ID)).thenReturn(Optional.of(ds));
    order = mock(Order.class);
    when(order.getId()).thenReturn(ORDER_ID);
    when(order.getOrderNo()).thenReturn("ORD-20261104-001");
    when(order.getStatus()).thenReturn(OrderStatus.IN_PRODUCTION);
    User am = user(AM_ID);
    when(order.getAm()).thenReturn(am);
    task = MaterialTask.create(order, MaterialSpec.create(order, MaterialType.POSTER, "A3"), ds,
        LocalDateTime.of(2026, 12, 1, 18, 0), Priority.HIGH);
    task.changeStatus(MaterialTaskStatus.PENDING_APPROVAL);
    when(taskRepository.findByIdForUpdate(TASK_ID)).thenReturn(Optional.of(task));
    when(versionRepository.findTaskIdById(VERSION_ID)).thenReturn(Optional.of(TASK_ID));
    when(versionRepository.save(any(DesignVersion.class))).thenAnswer(call -> call.getArgument(0));
  }

  @Test
  void releaseRecordsPassAndKeepsTaskPendingApproval() {
    DesignVersion version = versionUnderReview();
    stubVersion(version);

    DesignVersion released = service.release(VERSION_ID, " <b>Looks good</b> ", reviewer());

    assertThat(released.getStatus()).isEqualTo(VersionStatus.RELEASED);
    // CONFLICT-01: releasing never approves the material task in Phase 2.
    assertThat(task.getStatus()).isEqualTo(MaterialTaskStatus.PENDING_APPROVAL);
    ArgumentCaptor<ReviewRecord> review = ArgumentCaptor.forClass(ReviewRecord.class);
    verify(reviewRecordRepository).save(review.capture());
    assertThat(review.getValue().getReviewResult()).isEqualTo(ReviewResult.PASS);
    assertThat(review.getValue().getReviewComment()).isEqualTo("Looks good");
    assertThat(review.getValue().getTask()).isSameAs(task);
    ArgumentCaptor<ReleaseRecord> release = ArgumentCaptor.forClass(ReleaseRecord.class);
    verify(releaseRecordRepository).save(release.capture());
    assertThat(release.getValue().getVersion()).isSameAs(released);
    assertThat(release.getValue().getReleaseRemark()).isEqualTo("Looks good");
    assertThat(release.getValue().getReleasedAt()).isEqualTo(LocalDateTime.of(2026, 11, 4, 16, 0));
    ArgumentCaptor<VersionReleasedEvent> event = ArgumentCaptor.forClass(VersionReleasedEvent.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue().recipientIds()).containsExactlyInAnyOrder(DS_ID, AM_ID);
  }

  @Test
  void releaseAcceptsMissingRemarkAndRejectsOversizedRemark() {
    DesignVersion version = versionUnderReview();
    stubVersion(version);

    assertThat(service.release(VERSION_ID, "   ", reviewer()).getStatus())
        .isEqualTo(VersionStatus.RELEASED);

    stubVersion(versionUnderReview());
    assertThatIllegalArgumentException().isThrownBy(() -> service.release(VERSION_ID,
        "x".repeat(InternalReviewService.MAX_COMMENT_LENGTH + 1), reviewer()));
  }

  @Test
  void releaseRejectsWrongVersionTaskOrderStateAndStaleVersions() {
    DesignVersion draft = DesignVersion.draft(task, "V1", "note", null, ds, false);
    stubVersion(draft);
    assertThatThrownBy(() -> service.release(VERSION_ID, null, reviewer()))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("pending internal review");

    DesignVersion pending = versionUnderReview();
    stubVersion(pending);
    task.changeStatus(MaterialTaskStatus.PENDING_REVISE);
    assertThatThrownBy(() -> service.release(VERSION_ID, null, reviewer()))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("not awaiting internal review");
    task.changeStatus(MaterialTaskStatus.PENDING_APPROVAL);

    when(order.getStatus()).thenReturn(OrderStatus.COMPLETED);
    assertThatThrownBy(() -> service.release(VERSION_ID, null, reviewer()))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("in production");
    when(order.getStatus()).thenReturn(OrderStatus.IN_PRODUCTION);

    when(versionRepository.countNewerSubmittedVersions(TASK_ID, VERSION_ID)).thenReturn(1L);
    assertThatThrownBy(() -> service.release(VERSION_ID, null, reviewer()))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("latest submitted version");

    assertThatThrownBy(() -> service.release(VERSION_ID, null, designer(OTHER_DS_ID)))
        .isInstanceOf(AccessDeniedException.class);
    verify(reviewRecordRepository, never()).save(any());
  }

  @Test
  void rejectMovesTaskToRevisionButKeepsVersionStatusAndRequiresComment() {
    DesignVersion version = versionUnderReview();
    stubVersion(version);

    DesignVersion rejected = service.reject(VERSION_ID, " <i>Logo too small</i> ", reviewer());

    assertThat(rejected.getStatus()).isEqualTo(VersionStatus.PENDING_INTERNAL_REVIEW);
    assertThat(task.getStatus()).isEqualTo(MaterialTaskStatus.PENDING_REVISE);
    ArgumentCaptor<ReviewRecord> review = ArgumentCaptor.forClass(ReviewRecord.class);
    verify(reviewRecordRepository).save(review.capture());
    assertThat(review.getValue().getReviewResult()).isEqualTo(ReviewResult.REJECT);
    assertThat(review.getValue().getReviewComment()).isEqualTo("Logo too small");
    ArgumentCaptor<VersionRejectedEvent> event = ArgumentCaptor.forClass(VersionRejectedEvent.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue().reviewComment()).isEqualTo("Logo too small");
    verify(releaseRecordRepository, never()).save(any());
  }

  @Test
  void rejectRejectsBlankCommentDraftVersionAndMissingTask() {
    DesignVersion version = versionUnderReview();
    stubVersion(version);
    assertThatIllegalArgumentException().isThrownBy(() -> service.reject(VERSION_ID, "  ", reviewer()));
    assertThat(task.getStatus()).isEqualTo(MaterialTaskStatus.PENDING_APPROVAL);

    DesignVersion draft = DesignVersion.draft(task, "V1", "note", null, ds, false);
    stubVersion(draft);
    assertThatThrownBy(() -> service.reject(VERSION_ID, "fix it", reviewer()))
        .isInstanceOf(InvalidStateTransitionException.class);

    when(versionRepository.findTaskIdById(VERSION_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.release(VERSION_ID, null, reviewer()))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(reviewRecordRepository, never()).save(any());
  }

  @Test
  void markFinalRequiresClientConfirmedVersionAndAllowsOnlyOneFinalPerOrder() {
    DesignVersion version = clientConfirmedVersion();
    stubVersion(version);

    DesignVersion finalized = service.markFinal(VERSION_ID, designer(DS_ID));

    assertThat(finalized.getStatus()).isEqualTo(VersionStatus.FINAL);
    assertThat(finalized.isFinalVersion()).isTrue();
    verify(feedbackItemRepository).expireUnresolvedForOrder(
        org.mockito.ArgumentMatchers.eq(ORDER_ID), any(),
        org.mockito.ArgumentMatchers.eq(FeedbackStatus.UNRESOLVED),
        org.mockito.ArgumentMatchers.eq(FeedbackStatus.EXPIRED), any());

    assertThatThrownBy(() -> service.markFinal(VERSION_ID, designer(DS_ID)))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("already marked as final");
  }

  @Test
  void markFinalRejectsWrongStatusForeignOwnerExistingFinalAndNonDs() {
    DesignVersion released = versionUnderReview();
    released.changeStatus(VersionStatus.RELEASED);
    stubVersion(released);
    assertThatThrownBy(() -> service.markFinal(VERSION_ID, designer(DS_ID)))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("client-confirmed");

    stubVersion(clientConfirmedVersion());
    assertThatThrownBy(() -> service.markFinal(VERSION_ID, designer(OTHER_DS_ID)))
        .isInstanceOf(ResourceNotFoundException.class);

    when(versionRepository.countFinalVersionsInOrder(ORDER_ID)).thenReturn(1L);
    assertThatThrownBy(() -> service.markFinal(VERSION_ID, designer(DS_ID)))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("already marked as final");

    assertThatThrownBy(() -> service.markFinal(VERSION_ID, reviewer()))
        .isInstanceOf(AccessDeniedException.class);
  }

  private DesignVersion versionUnderReview() {
    DesignVersion version = DesignVersion.draft(task, "V1", "note", null, ds, false);
    version.changeStatus(VersionStatus.PENDING_INTERNAL_REVIEW);
    version.markSubmitted(LocalDateTime.of(2026, 11, 1, 10, 0));
    return version;
  }

  private DesignVersion clientConfirmedVersion() {
    DesignVersion version = versionUnderReview();
    version.changeStatus(VersionStatus.RELEASED);
    version.changeStatus(VersionStatus.IN_CLIENT_REVIEW);
    version.changeStatus(VersionStatus.CLIENT_CONFIRMED);
    return version;
  }

  private void stubVersion(DesignVersion version) {
    when(versionRepository.findByIdForUpdate(VERSION_ID)).thenReturn(Optional.of(version));
  }

  private CurrentUser reviewer() {
    return new CurrentUser(bo, Set.of(RoleType.BO));
  }

  private CurrentUser designer(long id) {
    return new CurrentUser(user(id), Set.of(RoleType.DS));
  }

  private static User user(long id) {
    User user = mock(User.class);
    when(user.getId()).thenReturn(id);
    return user;
  }
}
