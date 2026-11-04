package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.aop.AuditedAction;
import com.blue333.ordermgmt.domain.DesignVersion;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.VersionFile;
import com.blue333.ordermgmt.domain.enums.AuditActionType;
import com.blue333.ordermgmt.domain.enums.AuditTargetType;
import com.blue333.ordermgmt.domain.enums.MaterialTaskStatus;
import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.domain.enums.VersionStatus;
import com.blue333.ordermgmt.dto.storage.PresignedUrlResponse;
import com.blue333.ordermgmt.event.VersionSubmittedEvent;
import com.blue333.ordermgmt.repository.DesignVersionRepository;
import com.blue333.ordermgmt.repository.MaterialTaskRepository;
import com.blue333.ordermgmt.repository.UserRepository;
import com.blue333.ordermgmt.repository.VersionFileRepository;
import com.blue333.ordermgmt.service.VersionUploadSessionCodec.UploadSession;
import com.blue333.ordermgmt.util.XSSCleaner;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * M3 design-version management (DS-02, DS-03, DS-04): three-step upload and submit for internal review.
 *
 * <ol>
 *   <li>{@link #startUpload} (A-08) validates the file and returns a presigned PUT URL plus a signed
 *       {@code upload_session_id}. Nothing is written to the database.</li>
 *   <li>The browser PUTs the file directly to object storage (NFR-F3).</li>
 *   <li>{@link #confirmUpload} (S-09) checks the stored object, allocates the version number and writes
 *       the DRAFT version and its file row.</li>
 * </ol>
 */
@Service
public class DesignVersionService {
  public static final int MAX_TEXT_LENGTH = 5000;
  private static final Set<MaterialTaskStatus> UPLOADABLE_TASK_STATUSES =
      Set.of(MaterialTaskStatus.PENDING_DESIGN, MaterialTaskStatus.PENDING_REVISE);

  private final MaterialTaskRepository taskRepository;
  private final DesignVersionRepository versionRepository;
  private final VersionFileRepository fileRepository;
  private final UserRepository userRepository;
  private final VersionNoGenerator versionNoGenerator;
  private final VersionStateMachine versionStateMachine;
  private final MaterialTaskStateMachine taskStateMachine;
  private final AttachmentFilePolicyValidator filePolicy;
  private final FileStorageService fileStorageService;
  private final VersionUploadSessionCodec sessionCodec;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;
  private final ZoneId businessZone;

  public DesignVersionService(
      MaterialTaskRepository taskRepository,
      DesignVersionRepository versionRepository,
      VersionFileRepository fileRepository,
      UserRepository userRepository,
      VersionNoGenerator versionNoGenerator,
      VersionStateMachine versionStateMachine,
      MaterialTaskStateMachine taskStateMachine,
      AttachmentFilePolicyValidator filePolicy,
      FileStorageService fileStorageService,
      VersionUploadSessionCodec sessionCodec,
      ApplicationEventPublisher eventPublisher,
      Clock clock,
      @Value("${app.business-time-zone:Asia/Shanghai}") String businessTimeZone) {
    this.taskRepository = taskRepository;
    this.versionRepository = versionRepository;
    this.fileRepository = fileRepository;
    this.userRepository = userRepository;
    this.versionNoGenerator = versionNoGenerator;
    this.versionStateMachine = versionStateMachine;
    this.taskStateMachine = taskStateMachine;
    this.filePolicy = filePolicy;
    this.fileStorageService = fileStorageService;
    this.sessionCodec = sessionCodec;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
    this.businessZone = ZoneId.of(businessTimeZone);
  }

  /** A-08 Step 1. Only the DS the task is assigned to may upload, and only while the task awaits a design. */
  @Transactional(readOnly = true)
  public UploadStart startUpload(Long taskId, String fileName, long fileSize, CurrentUser currentUser) {
    MaterialTask task = taskRepository.findById(requirePositive(taskId, "taskId"))
        .orElseThrow(() -> taskNotFound(taskId));
    requireUploadable(task, currentUser);
    filePolicy.validate(fileName, fileSize);
    String cleanFileName = fileName.trim();
    String objectKey = fileStorageService.generateObjectKey("versions/task-" + task.getId(), cleanFileName);
    PresignedUrlResponse presigned = fileStorageService.generatePresignedPutUrl(objectKey);
    String session = sessionCodec.issue(task.getId(), currentUser.id(), objectKey, cleanFileName, fileSize);
    return new UploadStart(session, presigned, versionNoGenerator.peekNext(task.getId()));
  }

  /** S-09 Step 3. Writes the DRAFT version once the object exists with the declared size. */
  @Transactional
  @AuditedAction(action = AuditActionType.UPLOAD_VERSION, targetType = AuditTargetType.DESIGN_VERSION,
      targetId = "#result.id", afterState = "#result.status",
      remark = "#result == null ? null : 'version_no=' + #result.versionNo")
  public DesignVersion confirmUpload(
      String uploadSessionId, String designNote, String changeSummary, CurrentUser currentUser) {
    UploadSession session = sessionCodec.verify(uploadSessionId);
    if (!Objects.equals(session.uploaderId(), currentUser.id())) {
      throw new AccessDeniedException("The upload session belongs to another user");
    }
    // BR-06: the design note is mandatory; it is cleaned before storage (NFR-S3).
    String note = requireText(clean(designNote), "A design note is required");
    String summary = clean(changeSummary);
    requireMaxLength(note, "design_note");
    requireMaxLength(summary, "change_summary");

    MaterialTask task = taskRepository.findByIdForUpdate(session.taskId())
        .orElseThrow(() -> taskNotFound(session.taskId()));
    requireUploadable(task, currentUser);
    if (fileRepository.existsByStoragePath(session.objectKey())) {
      throw new IllegalStateException("This upload has already been confirmed");
    }
    long storedSize = fileStorageService.findObjectSize(session.objectKey())
        .orElseThrow(() -> new IllegalStateException("Uploaded object does not exist"));
    if (storedSize != session.fileSize()) {
      throw new IllegalStateException("Uploaded object size does not match file_size");
    }

    User uploader = userRepository.findById(currentUser.id())
        .orElseThrow(() -> new AccessDeniedException("An authenticated user is required"));
    // P2-T08: a version uploaded after the task deadline is flagged as overdue.
    boolean overdue = now().isAfter(task.getTaskDeadline());
    DesignVersion version = versionRepository.save(DesignVersion.draft(task,
        versionNoGenerator.next(task.getId()), note, summary, uploader, overdue));
    fileRepository.save(VersionFile.register(version, session.fileName(), session.fileSize(),
        session.objectKey()));
    return version;
  }

  /**
   * A-09 (DS-04). Version DRAFT → PENDING_INTERNAL_REVIEW and task → PENDING_APPROVAL in one
   * transaction. Only one version of a task can be under review: a task already PENDING_APPROVAL
   * rejects the transition.
   */
  @Transactional
  @AuditedAction(action = AuditActionType.SUBMIT_REVIEW, targetType = AuditTargetType.DESIGN_VERSION,
      targetId = "#versionId", beforeState = "'DRAFT'", afterState = "#result.status")
  public DesignVersion submitForReview(Long versionId, CurrentUser currentUser) {
    requirePositive(versionId, "versionId");
    // Lock order is task, then version, matching confirmUpload, which locks only the task.
    Long taskId = versionRepository.findTaskIdById(versionId).orElseThrow(() -> versionNotFound(versionId));
    MaterialTask task = taskRepository.findByIdForUpdate(taskId).orElseThrow(() -> versionNotFound(versionId));
    DesignVersion version = versionRepository.findByIdForUpdate(versionId)
        .orElseThrow(() -> versionNotFound(versionId));

    boolean assignedToCaller = sameUser(task.getAssignee(), currentUser);
    boolean uploadedByCaller = sameUser(version.getUploader(), currentUser);
    if (!currentUser.hasRole(RoleType.DS) || (!assignedToCaller && !uploadedByCaller)) {
      throw versionNotFound(versionId);
    }
    if (!uploadedByCaller) {
      throw new AccessDeniedException("Only the designer who uploaded the version can submit it");
    }
    if (!assignedToCaller) {
      throw new AccessDeniedException("The task is no longer assigned to you");
    }
    if (task.getOrder().getStatus() != OrderStatus.IN_PRODUCTION) {
      throw new InvalidStateTransitionException("Versions can only be submitted while the order is in production");
    }
    if (version.getDesignNote() == null || version.getDesignNote().isBlank()) {
      throw new IllegalStateException("A design note is required before submitting for review");
    }
    if (task.getStatus() == MaterialTaskStatus.PENDING_APPROVAL) {
      throw new InvalidStateTransitionException("Another version of this task is already under internal review");
    }

    versionStateMachine.transition(version, VersionStatus.PENDING_INTERNAL_REVIEW);
    taskStateMachine.transition(task, MaterialTaskStatus.PENDING_APPROVAL);
    version.markSubmitted(now());
    taskRepository.save(task);
    DesignVersion saved = versionRepository.save(version);

    String material = task.getMaterialSpec().getMaterialType().name() + " " + task.getMaterialSpec().getSpecSize();
    publishAfterCommit(new VersionSubmittedEvent(userRepository.findEnabledIdsByRoleType(RoleType.BO),
        saved.getId(), task.getOrder().getOrderNo(), saved.getVersionNo(), material));
    return saved;
  }

  private void requireUploadable(MaterialTask task, CurrentUser currentUser) {
    if (!currentUser.hasRole(RoleType.DS) || !sameUser(task.getAssignee(), currentUser)) {
      // PRD 2.2.3: DS uploads only to own tasks; other tasks are reported as missing (BR-13).
      throw taskNotFound(task.getId());
    }
    if (task.getOrder().getStatus() != OrderStatus.IN_PRODUCTION) {
      throw new InvalidStateTransitionException("Versions can only be uploaded while the order is in production");
    }
    if (!UPLOADABLE_TASK_STATUSES.contains(task.getStatus())) {
      throw new InvalidStateTransitionException(
          "Versions can only be uploaded while the task is PENDING_DESIGN or PENDING_REVISE (current: "
              + task.getStatus() + ")");
    }
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

  private static void requireMaxLength(String value, String field) {
    if (value != null && value.length() > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException(field + " must be at most " + MAX_TEXT_LENGTH + " characters");
    }
  }

  private static ResourceNotFoundException taskNotFound(Long taskId) {
    return new ResourceNotFoundException("Material task does not exist: " + taskId);
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

  /** Result of upload Step 1. {@code nextVersionNo} is informational; the number is fixed at Step 3. */
  public record UploadStart(String uploadSessionId, PresignedUrlResponse presigned, String nextVersionNo) {
  }
}
