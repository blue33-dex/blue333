package com.blue333.ordermgmt.domain;

import com.blue333.ordermgmt.domain.enums.VersionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A designer's uploaded version of one material task (GLOSSARY E-10).
 * The version number is assigned once by the system (BR-05) and rows are never deleted (BR-07).
 */
@Entity
@Table(name = "design_versions")
public class DesignVersion {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "task_id", nullable = false, updatable = false)
  private MaterialTask task;

  @Column(name = "version_no", nullable = false, length = 10, updatable = false)
  private String versionNo;

  @Column(name = "design_note", nullable = false, columnDefinition = "TEXT")
  private String designNote;

  @Column(name = "change_summary", columnDefinition = "TEXT")
  private String changeSummary;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "uploader_id", nullable = false, updatable = false)
  private User uploader;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private VersionStatus status;

  @Column(name = "is_final", nullable = false)
  private boolean finalVersion;

  @Column(name = "is_overdue", nullable = false)
  private boolean overdue;

  @Column(name = "submitted_at")
  private LocalDateTime submittedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected DesignVersion() {
  }

  public static DesignVersion draft(
      MaterialTask task,
      String versionNo,
      String designNote,
      String changeSummary,
      User uploader,
      boolean overdue) {
    DesignVersion version = new DesignVersion();
    version.task = Objects.requireNonNull(task, "task must not be null");
    version.versionNo = Objects.requireNonNull(versionNo, "versionNo must not be null");
    version.designNote = Objects.requireNonNull(designNote, "designNote must not be null");
    version.changeSummary = changeSummary;
    version.uploader = Objects.requireNonNull(uploader, "uploader must not be null");
    version.status = VersionStatus.DRAFT;
    version.overdue = overdue;
    return version;
  }

  /** Changes status only after {@code VersionStateMachine} has validated the transition. */
  public void changeStatus(VersionStatus status) {
    this.status = Objects.requireNonNull(status, "status must not be null");
  }

  public void markSubmitted(LocalDateTime submittedAt) {
    this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt must not be null");
  }

  /** Flags this version as the delivered final version (DS-06). Status changes only via the state machine. */
  public void markFinal() {
    this.finalVersion = true;
  }

  public Long getId() { return id; }
  public MaterialTask getTask() { return task; }
  public String getVersionNo() { return versionNo; }
  public String getDesignNote() { return designNote; }
  public String getChangeSummary() { return changeSummary; }
  public User getUploader() { return uploader; }
  public VersionStatus getStatus() { return status; }
  public boolean isFinalVersion() { return finalVersion; }
  public boolean isOverdue() { return overdue; }
  public LocalDateTime getSubmittedAt() { return submittedAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
}
