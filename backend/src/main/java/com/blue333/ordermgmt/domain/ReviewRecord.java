package com.blue333.ordermgmt.domain;

import com.blue333.ordermgmt.domain.enums.ReviewResult;
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

/** A BO internal review decision on one design version (GLOSSARY E-12). Written by the internal-review service. */
@Entity
@Table(name = "review_records")
public class ReviewRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "version_id", nullable = false, updatable = false)
  private DesignVersion version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "task_id", nullable = false, updatable = false)
  private MaterialTask task;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reviewer_id", nullable = false, updatable = false)
  private User reviewer;

  @Enumerated(EnumType.STRING)
  @Column(name = "review_result", nullable = false, length = 10, updatable = false)
  private ReviewResult reviewResult;

  @Column(name = "review_comment", columnDefinition = "TEXT", updatable = false)
  private String reviewComment;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected ReviewRecord() {
  }

  public static ReviewRecord create(
      DesignVersion version, MaterialTask task, User reviewer, ReviewResult result, String comment) {
    ReviewRecord rec = new ReviewRecord();
    rec.version = Objects.requireNonNull(version, "version must not be null");
    rec.task = Objects.requireNonNull(task, "task must not be null");
    rec.reviewer = Objects.requireNonNull(reviewer, "reviewer must not be null");
    rec.reviewResult = Objects.requireNonNull(result, "result must not be null");
    rec.reviewComment = comment;
    return rec;
  }

  public Long getId() { return id; }
  public DesignVersion getVersion() { return version; }
  public MaterialTask getTask() { return task; }
  public User getReviewer() { return reviewer; }
  public ReviewResult getReviewResult() { return reviewResult; }
  public String getReviewComment() { return reviewComment; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
