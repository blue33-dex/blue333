package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blue333.ordermgmt.domain.DesignVersion;
import com.blue333.ordermgmt.domain.MaterialSpec;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.VersionFile;
import com.blue333.ordermgmt.domain.enums.MaterialTaskStatus;
import com.blue333.ordermgmt.domain.enums.MaterialType;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.domain.enums.Priority;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.domain.enums.VersionStatus;
import com.blue333.ordermgmt.dto.storage.PresignedUrlResponse;
import com.blue333.ordermgmt.event.VersionSubmittedEvent;
import com.blue333.ordermgmt.repository.DesignVersionRepository;
import com.blue333.ordermgmt.repository.MaterialTaskRepository;
import com.blue333.ordermgmt.repository.UserRepository;
import com.blue333.ordermgmt.repository.VersionFileRepository;
import com.blue333.ordermgmt.service.VersionUploadSessionCodec.UploadSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

class DesignVersionServiceTest {
  private static final long TASK_ID = 10L;
  private static final long VERSION_ID = 50L;
  private static final long DS_ID = 3L;
  private static final long OTHER_DS_ID = 4L;
  private static final long BO_ID = 2L;
  private static final String KEY = "versions/task-10/abc.psd";
  // 08:00 UTC = 16:00 in the default business zone Asia/Shanghai.
  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");

  private MaterialTaskRepository taskRepository;
  private DesignVersionRepository versionRepository;
  private VersionFileRepository fileRepository;
  private UserRepository userRepository;
  private VersionNoGenerator versionNoGenerator;
  private FileStorageService storage;
  private VersionUploadSessionCodec codec;
  private ApplicationEventPublisher events;
  private DesignVersionService service;

  private User ds;
  private Order order;
  private MaterialTask task;

  @BeforeEach
  void setUp() {
    taskRepository = mock(MaterialTaskRepository.class);
    versionRepository = mock(DesignVersionRepository.class);
    fileRepository = mock(VersionFileRepository.class);
    userRepository = mock(UserRepository.class);
    versionNoGenerator = mock(VersionNoGenerator.class);
    storage = mock(FileStorageService.class);
    events = mock(ApplicationEventPublisher.class);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    codec = new VersionUploadSessionCodec("2XcqcBQNpAWbyzwOq/t2OemNfavFwSRAMiAdHk8kex0=", new ObjectMapper(), clock);
    service = new DesignVersionService(taskRepository, versionRepository, fileRepository, userRepository,
        versionNoGenerator, new VersionStateMachine(), new MaterialTaskStateMachine(),
        new AttachmentFilePolicyValidator(), storage, codec, events, clock, "Asia/Shanghai");

    ds = user(DS_ID);
    when(userRepository.findById(DS_ID)).thenReturn(Optional.of(ds));
    order = mock(Order.class);
    when(order.getStatus()).thenReturn(OrderStatus.IN_PRODUCTION);
    when(order.getOrderNo()).thenReturn("ORD-20261104-001");
    task = spyTask(LocalDateTime.of(2026, 12, 1, 18, 0));
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(taskRepository.findByIdForUpdate(TASK_ID)).thenReturn(Optional.of(task));
    when(versionRepository.save(any(DesignVersion.class))).thenAnswer(call -> call.getArgument(0));
    when(versionNoGenerator.next(TASK_ID)).thenReturn("V3");
    when(versionNoGenerator.peekNext(TASK_ID)).thenReturn("V3");
    when(storage.generateObjectKey(eq("versions/task-10"), anyString())).thenReturn(KEY);
    when(storage.generatePresignedPutUrl(KEY)).thenReturn(new PresignedUrlResponse("http://s3/put", KEY, 15));
    when(storage.findObjectSize(KEY)).thenReturn(Optional.of(2048L));
  }

  @Test
  void startUploadReturnsPresignedUrlSessionAndNextVersionWithoutWriting() {
    DesignVersionService.UploadStart start = service.startUpload(TASK_ID, "poster.psd", 2048L, designer(DS_ID));

    assertThat(start.presigned().presignedUrl()).isEqualTo("http://s3/put");
    assertThat(start.nextVersionNo()).isEqualTo("V3");
    UploadSession session = codec.verify(start.uploadSessionId());
    assertThat(session.taskId()).isEqualTo(TASK_ID);
    assertThat(session.uploaderId()).isEqualTo(DS_ID);
    assertThat(session.objectKey()).isEqualTo(KEY);
    verify(versionRepository, never()).save(any());
  }

  @Test
  void startUploadEnforcesOwnershipStatusAndFilePolicy() {
    assertThatThrownBy(() -> service.startUpload(TASK_ID, "poster.psd", 10, designer(OTHER_DS_ID)))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.startUpload(TASK_ID, "virus.exe", 10, designer(DS_ID)));
    assertThatIllegalArgumentException().isThrownBy(() -> service.startUpload(TASK_ID, "big.psd",
        AttachmentFilePolicyValidator.MAX_FILE_SIZE_BYTES + 1, designer(DS_ID)));

    when(task.getStatus()).thenReturn(MaterialTaskStatus.PENDING_APPROVAL);
    assertThatThrownBy(() -> service.startUpload(TASK_ID, "poster.psd", 10, designer(DS_ID)))
        .isInstanceOf(InvalidStateTransitionException.class);
  }

  @Test
  void confirmUploadCreatesCleanDraftVersionAndFile() {
    String session = codec.issue(TASK_ID, DS_ID, KEY, "poster.psd", 2048L);

    DesignVersion version = service.confirmUpload(session, " <b>Bigger logo</b> ", "<i>logo</i>", designer(DS_ID));

    assertThat(version.getVersionNo()).isEqualTo("V3");
    assertThat(version.getStatus()).isEqualTo(VersionStatus.DRAFT);
    assertThat(version.getDesignNote()).isEqualTo("Bigger logo");
    assertThat(version.getChangeSummary()).isEqualTo("logo");
    assertThat(version.isOverdue()).isFalse();
    assertThat(version.getUploader()).isSameAs(ds);
    ArgumentCaptor<VersionFile> file = ArgumentCaptor.forClass(VersionFile.class);
    verify(fileRepository).save(file.capture());
    assertThat(file.getValue().getStoragePath()).isEqualTo(KEY);
    assertThat(file.getValue().getFileSize()).isEqualTo(2048L);
    // The number is allocated under the task row lock.
    verify(taskRepository).findByIdForUpdate(TASK_ID);
  }

  @Test
  void confirmUploadFlagsVersionsUploadedAfterTheDeadline() {
    // 15:00 Shanghai time is before "now" (16:00 Shanghai) although it is after 08:00 UTC.
    task = spyTask(LocalDateTime.of(2026, 11, 4, 15, 0));
    when(taskRepository.findByIdForUpdate(TASK_ID)).thenReturn(Optional.of(task));

    DesignVersion version = service.confirmUpload(codec.issue(TASK_ID, DS_ID, KEY, "poster.psd", 2048L),
        "note", null, designer(DS_ID));

    assertThat(version.isOverdue()).isTrue();
  }

  @Test
  void confirmUploadRejectsMissingNoteForeignSessionReplayAndSizeMismatch() {
    String session = codec.issue(TASK_ID, DS_ID, KEY, "poster.psd", 2048L);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.confirmUpload(session, "<p></p>", null, designer(DS_ID)));
    assertThatThrownBy(() -> service.confirmUpload(session, "note", null, designer(OTHER_DS_ID)))
        .isInstanceOf(AccessDeniedException.class);

    when(fileRepository.existsByStoragePath(KEY)).thenReturn(true);
    assertThatThrownBy(() -> service.confirmUpload(session, "note", null, designer(DS_ID)))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("already been confirmed");

    when(fileRepository.existsByStoragePath(KEY)).thenReturn(false);
    when(storage.findObjectSize(KEY)).thenReturn(Optional.of(1L));
    assertThatThrownBy(() -> service.confirmUpload(session, "note", null, designer(DS_ID)))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("size");

    when(storage.findObjectSize(KEY)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.confirmUpload(session, "note", null, designer(DS_ID)))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("does not exist");
    verify(versionRepository, never()).save(any());
  }

  @Test
  void submitMovesVersionToReviewAndTaskToPendingApprovalAndNotifiesBo() {
    MaterialTask realTask = realTask();
    DesignVersion version = DesignVersion.draft(realTask, "V1", "note", null, ds, false);
    stubVersion(realTask, version);
    when(userRepository.findEnabledIdsByRoleType(RoleType.BO)).thenReturn(List.of(BO_ID));

    DesignVersion submitted = service.submitForReview(VERSION_ID, designer(DS_ID));

    assertThat(submitted.getStatus()).isEqualTo(VersionStatus.PENDING_INTERNAL_REVIEW);
    assertThat(submitted.getSubmittedAt()).isEqualTo(LocalDateTime.of(2026, 11, 4, 16, 0));
    assertThat(realTask.getStatus()).isEqualTo(MaterialTaskStatus.PENDING_APPROVAL);
    ArgumentCaptor<VersionSubmittedEvent> event = ArgumentCaptor.forClass(VersionSubmittedEvent.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue().recipientIds()).containsExactly(BO_ID);
    assertThat(event.getValue().versionNo()).isEqualTo("V1");
  }

  @Test
  void submitAllowsResubmissionAfterRevisionButOnlyOneVersionUnderReview() {
    MaterialTask realTask = realTask();
    realTask.changeStatus(MaterialTaskStatus.PENDING_REVISE);
    DesignVersion v2 = DesignVersion.draft(realTask, "V2", "fixed colours", null, ds, false);
    stubVersion(realTask, v2);

    service.submitForReview(VERSION_ID, designer(DS_ID));
    assertThat(realTask.getStatus()).isEqualTo(MaterialTaskStatus.PENDING_APPROVAL);

    DesignVersion v3 = DesignVersion.draft(realTask, "V3", "another draft", null, ds, false);
    when(versionRepository.findByIdForUpdate(VERSION_ID)).thenReturn(Optional.of(v3));
    assertThatThrownBy(() -> service.submitForReview(VERSION_ID, designer(DS_ID)))
        .isInstanceOf(InvalidStateTransitionException.class).hasMessageContaining("already under internal review");
    assertThat(v3.getStatus()).isEqualTo(VersionStatus.DRAFT);
  }

  @Test
  void submitRejectsNonDraftForeignAndInvisibleVersions() {
    MaterialTask realTask = realTask();
    DesignVersion version = DesignVersion.draft(realTask, "V1", "note", null, ds, false);
    stubVersion(realTask, version);

    assertThatThrownBy(() -> service.submitForReview(VERSION_ID, designer(OTHER_DS_ID)))
        .isInstanceOf(ResourceNotFoundException.class);

    version.changeStatus(VersionStatus.PENDING_INTERNAL_REVIEW);
    assertThatThrownBy(() -> service.submitForReview(VERSION_ID, designer(DS_ID)))
        .isInstanceOf(InvalidStateTransitionException.class);
    assertThat(realTask.getStatus()).isEqualTo(MaterialTaskStatus.PENDING_DESIGN);

    User otherDs = user(OTHER_DS_ID);
    DesignVersion foreign = DesignVersion.draft(realTask, "V2", "note", null, otherDs, false);
    when(versionRepository.findByIdForUpdate(VERSION_ID)).thenReturn(Optional.of(foreign));
    assertThatThrownBy(() -> service.submitForReview(VERSION_ID, designer(DS_ID)))
        .isInstanceOf(AccessDeniedException.class);
    verify(events, never()).publishEvent(any());
  }

  private void stubVersion(MaterialTask realTask, DesignVersion version) {
    when(versionRepository.findTaskIdById(VERSION_ID)).thenReturn(Optional.of(TASK_ID));
    when(taskRepository.findByIdForUpdate(TASK_ID)).thenReturn(Optional.of(realTask));
    when(versionRepository.findByIdForUpdate(VERSION_ID)).thenReturn(Optional.of(version));
  }

  private MaterialTask realTask() {
    return MaterialTask.create(order, MaterialSpec.create(order, MaterialType.POSTER, "A3"), ds,
        LocalDateTime.of(2026, 12, 1, 18, 0), Priority.HIGH);
  }

  private MaterialTask spyTask(LocalDateTime deadline) {
    MaterialTask mocked = mock(MaterialTask.class);
    when(mocked.getId()).thenReturn(TASK_ID);
    when(mocked.getAssignee()).thenReturn(ds);
    when(mocked.getOrder()).thenReturn(order);
    when(mocked.getStatus()).thenReturn(MaterialTaskStatus.PENDING_DESIGN);
    when(mocked.getTaskDeadline()).thenReturn(deadline);
    return mocked;
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
