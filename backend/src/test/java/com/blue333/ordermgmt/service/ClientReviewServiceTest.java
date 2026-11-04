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

import com.blue333.ordermgmt.domain.ClientReview;
import com.blue333.ordermgmt.domain.ConfirmationRecord;
import com.blue333.ordermgmt.domain.DesignVersion;
import com.blue333.ordermgmt.domain.FeedbackItem;
import com.blue333.ordermgmt.domain.MaterialSpec;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.ClientReviewStatus;
import com.blue333.ordermgmt.domain.enums.FeedbackSource;
import com.blue333.ordermgmt.domain.enums.FeedbackStatus;
import com.blue333.ordermgmt.domain.enums.FeedbackType;
import com.blue333.ordermgmt.domain.enums.MaterialTaskStatus;
import com.blue333.ordermgmt.domain.enums.MaterialType;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.domain.enums.Priority;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** P3-T04/T05: A-13 open review, A-14 feedback, A-15 resolve, A-16 confirm. */
class ClientReviewServiceTest {
  private static final long TASK_ID = 10L;
  private static final long VERSION_ID = 50L;
  private static final long FEEDBACK_ID = 70L;
  private static final long ORDER_ID = 1L;
  private static final long AM_ID = 5L;
  private static final long OTHER_AM_ID = 6L;
  private static final long DS_ID = 3L;
  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");
  private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 11, 4, 16, 0);

  private MaterialTaskRepository taskRepository;
  private DesignVersionRepository versionRepository;
  private ClientReviewRepository clientReviewRepository;
  private FeedbackItemRepository feedbackItemRepository;
  private UserRepository userRepository;
  private ConfirmationRecordService confirmationRecordService;
  private VersionQueryService versionQueryService;
  private AccessControlService accessControl;
  private FeedbackNoGenerator feedbackNoGenerator;
  private ClientReviewService service;

  private User am;
  private User ds;
  private Order order;
  private MaterialTask task;
  private DesignVersion version;
  private ClientReview review;

  @BeforeEach
  void setUp() {
    taskRepository = mock(MaterialTaskRepository.class);
    versionRepository = mock(DesignVersionRepository.class);
    clientReviewRepository = mock(ClientReviewRepository.class);
    feedbackItemRepository = mock(FeedbackItemRepository.class);
    userRepository = mock(UserRepository.class);
    confirmationRecordService = mock(ConfirmationRecordService.class);
    versionQueryService = mock(VersionQueryService.class);
    accessControl = mock(AccessControlService.class);
    feedbackNoGenerator = mock(FeedbackNoGenerator.class);

    service = new ClientReviewService(taskRepository, versionRepository, clientReviewRepository,
        feedbackItemRepository, userRepository, confirmationRecordService, new VersionStateMachine(),
        new MaterialTaskStateMachine(), versionQueryService, accessControl, feedbackNoGenerator,
        Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Shanghai");

    am = user(AM_ID, "Account Manager");
    ds = user(DS_ID, "Designer");
    when(userRepository.findById(AM_ID)).thenReturn(Optional.of(am));
    when(userRepository.findById(DS_ID)).thenReturn(Optional.of(ds));
    when(feedbackNoGenerator.next()).thenReturn("FB-001");
    when(versionRepository.findTaskIdById(any())).thenReturn(Optional.of(TASK_ID));
    when(versionRepository.findByIdForUpdate(any())).thenAnswer(call -> Optional.of(version));
    when(versionRepository.save(any(DesignVersion.class))).thenAnswer(call -> call.getArgument(0));
    when(taskRepository.findByIdForUpdate(any())).thenAnswer(call -> Optional.of(task));
    when(taskRepository.save(any(MaterialTask.class))).thenAnswer(call -> call.getArgument(0));

    order = mock(Order.class);
    when(order.getId()).thenReturn(ORDER_ID);
    when(order.getStatus()).thenReturn(OrderStatus.IN_PRODUCTION);
    when(order.getAm()).thenReturn(am);
    task = MaterialTask.create(order, mock(MaterialSpec.class), ds,
        LocalDateTime.of(2026, 12, 1, 18, 0), Priority.HIGH);
    task.changeStatus(MaterialTaskStatus.PENDING_APPROVAL);
    version = releasedVersion();
    review = ClientReview.start(version, order, am, CONFIRMED_AT.minusHours(2));
    when(clientReviewRepository.findByVersionIdForUpdate(any())).thenReturn(Optional.of(review));
    when(feedbackItemRepository.findByIdForUpdate(any()))
        .thenAnswer(call -> Optional.of(FeedbackItem.create("FB-001", "content",
            FeedbackType.APPROVAL, FeedbackSource.CLIENT, version, review, am)));
    when(clientReviewRepository.save(any(ClientReview.class))).thenAnswer(call -> call.getArgument(0));
    when(feedbackItemRepository.save(any(FeedbackItem.class))).thenAnswer(call -> call.getArgument(0));
    when(versionQueryService.detail(any(), any())).thenReturn(versionResponse());
  }

  @Test
  void initiateMovesReleasedVersionToClientReview() {
    ClientReviewResponse response = service.initiateClientReview(VERSION_ID, amUser(AM_ID));

    assertThat(version.getStatus()).isEqualTo(VersionStatus.IN_CLIENT_REVIEW);
    assertThat(response.status()).isEqualTo(ClientReviewStatus.IN_REVIEW.name());
    assertThat(response.versionNo()).isEqualTo("V1");
    assertThat(response.initiatorId()).isEqualTo(AM_ID);
    verify(clientReviewRepository).save(any(ClientReview.class));
  }

  @Test
  void initiateRejectsStaleVersionsExistingReviewsAndForeignOrders() {
    when(clientReviewRepository.existsByVersionId(any())).thenReturn(true);
    assertThatThrownBy(() -> service.initiateClientReview(VERSION_ID, amUser(AM_ID)))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("already exists");

    when(clientReviewRepository.existsByVersionId(any())).thenReturn(false);
    when(versionRepository.countNewerSubmittedVersions(any(), any())).thenReturn(1L);
    assertThatThrownBy(() -> service.initiateClientReview(VERSION_ID, amUser(AM_ID)))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("latest submitted version");

    when(versionRepository.countNewerSubmittedVersions(any(), any())).thenReturn(0L);
    version.changeStatus(VersionStatus.PENDING_INTERNAL_REVIEW);
    DesignVersion notReleased = version;
    when(versionRepository.findByIdForUpdate(any())).thenReturn(Optional.of(notReleased));
    assertThatThrownBy(() -> service.initiateClientReview(VERSION_ID, amUser(AM_ID)))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("released");

    when(versionRepository.findByIdForUpdate(any())).thenReturn(Optional.of(releasedVersion()));
    assertThatThrownBy(() -> service.initiateClientReview(VERSION_ID, amUser(OTHER_AM_ID)))
        .isInstanceOf(ResourceNotFoundException.class);

    when(order.getStatus()).thenReturn(OrderStatus.COMPLETED);
    assertThatThrownBy(() -> service.initiateClientReview(VERSION_ID, amUser(AM_ID)))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("in production");
    verify(clientReviewRepository, never()).save(any());
  }

  @Test
  void recordModificationFeedbackReturnsTaskToRevision() {
    version.changeStatus(VersionStatus.IN_CLIENT_REVIEW);

    FeedbackResponse response = service.recordFeedback(VERSION_ID, FeedbackType.MODIFICATION_SUGGESTION,
        FeedbackSource.CLIENT, "  <b>Logo</b> is too small  ", amUser(AM_ID));

    assertThat(response.feedbackNo()).isEqualTo("FB-001");
    assertThat(response.content()).isEqualTo("Logo is too small");
    assertThat(response.status()).isEqualTo(FeedbackStatus.UNRESOLVED.name());
    assertThat(task.getStatus()).isEqualTo(MaterialTaskStatus.PENDING_REVISE);
    verify(feedbackItemRepository).save(any(FeedbackItem.class));
    verify(taskRepository).save(task);
  }

  @Test
  void approvalFeedbackKeepsTaskPendingApproval() {
    version.changeStatus(VersionStatus.IN_CLIENT_REVIEW);

    FeedbackResponse response = service.recordFeedback(VERSION_ID, FeedbackType.APPROVAL, null,
        "Client likes it", amUser(AM_ID));

    assertThat(response.source()).isEqualTo(FeedbackSource.CLIENT.name());
    assertThat(task.getStatus()).isEqualTo(MaterialTaskStatus.PENDING_APPROVAL);
    verify(taskRepository, never()).save(any());
  }

  @Test
  void recordFeedbackRequiresClientReviewStateAndContent() {
    version.changeStatus(VersionStatus.IN_CLIENT_REVIEW);
    assertThatIllegalArgumentException().isThrownBy(() -> service.recordFeedback(VERSION_ID,
        FeedbackType.APPROVAL, FeedbackSource.CLIENT, "  ", amUser(AM_ID)));

    assertThatIllegalArgumentException().isThrownBy(() -> service.recordFeedback(VERSION_ID,
        null, FeedbackSource.CLIENT, "content", amUser(AM_ID)));

    review.complete(CONFIRMED_AT);
    assertThatThrownBy(() -> service.recordFeedback(VERSION_ID, FeedbackType.APPROVAL,
        FeedbackSource.CLIENT, "content", amUser(AM_ID)))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("already completed");

    when(clientReviewRepository.findByVersionIdForUpdate(any()))
        .thenReturn(Optional.of(ClientReview.start(version, order, am, CONFIRMED_AT)));
    assertThatThrownBy(() -> service.recordFeedback(VERSION_ID, FeedbackType.APPROVAL,
        FeedbackSource.CLIENT, "content", amUser(OTHER_AM_ID)))
        .isInstanceOf(ResourceNotFoundException.class);

    assertThatThrownBy(() -> service.recordFeedback(VERSION_ID, FeedbackType.APPROVAL,
        FeedbackSource.CLIENT, "content", designerUser()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void resolveFeedbackRequiresTheAssignedDesignerAndAnUnresolvedItem() {
    FeedbackItem item = unresolvedFeedback();
    when(feedbackItemRepository.findById(FEEDBACK_ID)).thenReturn(Optional.of(item));
    when(feedbackItemRepository.findByIdForUpdate(any())).thenReturn(Optional.of(item));

    FeedbackResponse response = service.resolveFeedback(FEEDBACK_ID, designerUser());

    assertThat(response.status()).isEqualTo(FeedbackStatus.RESOLVED.name());
    assertThat(item.getStatus()).isEqualTo(FeedbackStatus.RESOLVED);
    verify(feedbackItemRepository).save(item);
  }

  @Test
  void resolveRejectsExpiredItemsForeignDesignersAndClosedOrders() {
    FeedbackItem expired = unresolvedFeedback();
    expired.expire();
    when(feedbackItemRepository.findById(FEEDBACK_ID)).thenReturn(Optional.of(expired));
    when(feedbackItemRepository.findByIdForUpdate(any())).thenReturn(Optional.of(expired));
    assertThatThrownBy(() -> service.resolveFeedback(FEEDBACK_ID, designerUser()))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("Expired");

    FeedbackItem foreign = unresolvedFeedback();
    when(feedbackItemRepository.findById(FEEDBACK_ID)).thenReturn(Optional.of(foreign));
    when(feedbackItemRepository.findByIdForUpdate(any())).thenReturn(Optional.of(foreign));
    when(taskRepository.findByIdForUpdate(any())).thenAnswer(call -> Optional.of(task));
    User otherDs = user(99L, "Other Designer");
    assertThatThrownBy(() -> service.resolveFeedback(FEEDBACK_ID,
        new CurrentUser(otherDs, Set.of(RoleType.DS))))
        .isInstanceOf(ResourceNotFoundException.class);

    when(order.getStatus()).thenReturn(OrderStatus.COMPLETED);
    FeedbackItem unresolved = unresolvedFeedback();
    when(feedbackItemRepository.findById(FEEDBACK_ID)).thenReturn(Optional.of(unresolved));
    when(feedbackItemRepository.findByIdForUpdate(any())).thenReturn(Optional.of(unresolved));
    assertThatThrownBy(() -> service.resolveFeedback(FEEDBACK_ID, designerUser()))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("in production");
  }

  @Test
  void confirmWritesTheConfirmationRecordApprovesTheTaskAndWarnsAboutUnresolvedFeedback() {
    version.changeStatus(VersionStatus.IN_CLIENT_REVIEW);
    ConfirmationRecord record = mock(ConfirmationRecord.class);
    when(record.getId()).thenReturn(90L);
    when(confirmationRecordService.create(any(), any(), any(), any())).thenReturn(record);
    when(feedbackItemRepository.countByVersionIdAndStatus(any(), eq(FeedbackStatus.UNRESOLVED)))
        .thenReturn(1L);

    ConfirmVersionResponse response = service.confirmVersion(VERSION_ID, "  Client Jane  ", amUser(AM_ID));

    assertThat(response.confirmationRecordId()).isEqualTo(90L);
    assertThat(response.confirmerName()).isEqualTo("Client Jane");
    assertThat(response.unresolvedFeedbackCount()).isEqualTo(1L);
    assertThat(response.warning()).contains("1 unresolved feedback");
    assertThat(version.getStatus()).isEqualTo(VersionStatus.CLIENT_CONFIRMED);
    assertThat(task.getStatus()).isEqualTo(MaterialTaskStatus.APPROVED);
    assertThat(review.getStatus()).isEqualTo(ClientReviewStatus.COMPLETED);
    verify(confirmationRecordService).create(any(), any(), eq("Client Jane"), any());
    verify(taskRepository).save(task);
  }

  @Test
  void confirmRequiresClientReviewStateAndTaskPendingApproval() {
    when(confirmationRecordService.create(any(), any(), any(), any()))
        .thenReturn(mock(ConfirmationRecord.class));

    assertThatThrownBy(() -> service.confirmVersion(VERSION_ID, "Jane", amUser(AM_ID)))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("client review");

    version.changeStatus(VersionStatus.IN_CLIENT_REVIEW);
    task.changeStatus(MaterialTaskStatus.PENDING_REVISE);
    assertThatThrownBy(() -> service.confirmVersion(VERSION_ID, "Jane", amUser(AM_ID)))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("awaiting approval");

    task.changeStatus(MaterialTaskStatus.PENDING_APPROVAL);
    assertThatIllegalArgumentException().isThrownBy(() ->
        service.confirmVersion(VERSION_ID, "  ", amUser(AM_ID)));

    assertThatThrownBy(() -> service.confirmVersion(VERSION_ID, "Jane", amUser(OTHER_AM_ID)))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.confirmVersion(VERSION_ID, "Jane", designerUser()))
        .isInstanceOf(AccessDeniedException.class);
    verify(confirmationRecordService, never()).create(any(), any(), any(), any());
  }

  @Test
  void listFeedbacksUsesVisibilityAndReturnsVersionOrdering() {
    version.changeStatus(VersionStatus.IN_CLIENT_REVIEW);
    FeedbackItem item = unresolvedFeedback();
    when(accessControl.findVisibleVersion(any(), any())).thenReturn(version);
    when(feedbackItemRepository.findAllByVersionIdOrderByCreatedAtAscIdAsc(any())).thenReturn(List.of(item));

    List<FeedbackResponse> feedbacks = service.listFeedbacks(VERSION_ID, designerUser());

    assertThat(feedbacks).hasSize(1);
    assertThat(feedbacks.get(0).feedbackNo()).isEqualTo("FB-001");
  }

  private DesignVersion releasedVersion() {
    DesignVersion created = DesignVersion.draft(task, "V1", "note", null, ds, false);
    created.changeStatus(VersionStatus.PENDING_INTERNAL_REVIEW);
    created.changeStatus(VersionStatus.RELEASED);
    return created;
  }

  private FeedbackItem unresolvedFeedback() {
    return FeedbackItem.create("FB-001", "content", FeedbackType.MODIFICATION_SUGGESTION,
        FeedbackSource.CLIENT, version, review, am);
  }

  private CurrentUser amUser(long id) {
    return new CurrentUser(user(id, "Account Manager " + id), Set.of(RoleType.AM));
  }

  private CurrentUser designerUser() {
    return new CurrentUser(ds, Set.of(RoleType.DS));
  }

  private VersionResponse versionResponse() {
    return new VersionResponse(VERSION_ID, TASK_ID, ORDER_ID, MaterialType.POSTER.name(), "A3",
        "V1", VersionStatus.CLIENT_CONFIRMED.name(), "note", null, DS_ID, "Designer", false,
        false, null, CONFIRMED_AT, CONFIRMED_AT);
  }

  private static User user(long id, String name) {
    User user = mock(User.class);
    when(user.getId()).thenReturn(id);
    when(user.getName()).thenReturn(name);
    return user;
  }
}
