package com.blue333.ordermgmt.domain;

import com.blue333.ordermgmt.domain.enums.FeedbackSource;
import com.blue333.ordermgmt.domain.enums.FeedbackStatus;
import com.blue333.ordermgmt.domain.enums.FeedbackType;
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
 * One client feedback item bound to a specific version number (GLOSSARY E-15, BR-09).
 * The designer resolves it (A-15); the system expires it when a newer version becomes FINAL.
 */
@Entity
@Table(name = "feedback_items")
public class FeedbackItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "feedback_no", nullable = false, unique = true, length = 10, updatable = false)
  private String feedbackNo;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(name = "feedback_type", nullable = false, length = 30)
  private FeedbackType feedbackType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FeedbackSource source;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "version_id", nullable = false, updatable = false)
  private DesignVersion version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_review_id", nullable = false, updatable = false)
  private ClientReview clientReview;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by", nullable = false, updatable = false)
  private User createdBy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FeedbackStatus status;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected FeedbackItem() {
  }

  public static FeedbackItem create(
      String feedbackNo,
      String content,
      FeedbackType feedbackType,
      FeedbackSource source,
      DesignVersion version,
      ClientReview clientReview,
      User createdBy) {
    FeedbackItem item = new FeedbackItem();
    item.feedbackNo = Objects.requireNonNull(feedbackNo, "feedbackNo must not be null");
    item.content = Objects.requireNonNull(content, "content must not be null");
    item.feedbackType = Objects.requireNonNull(feedbackType, "feedbackType must not be null");
    item.source = Objects.requireNonNull(source, "source must not be null");
    item.version = Objects.requireNonNull(version, "version must not be null");
    item.clientReview = Objects.requireNonNull(clientReview, "clientReview must not be null");
    item.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
    item.status = FeedbackStatus.UNRESOLVED;
    return item;
  }

  /** A-15: UNRESOLVED → RESOLVED. The service rejects EXPIRED items before calling this method. */
  public void resolve() {
    this.status = FeedbackStatus.RESOLVED;
  }

  /** System expiry when a newer version of the same order becomes FINAL (EN-04). */
  public void expire() {
    this.status = FeedbackStatus.EXPIRED;
  }

  public Long getId() { return id; }
  public String getFeedbackNo() { return feedbackNo; }
  public String getContent() { return content; }
  public FeedbackType getFeedbackType() { return feedbackType; }
  public FeedbackSource getSource() { return source; }
  public DesignVersion getVersion() { return version; }
  public ClientReview getClientReview() { return clientReview; }
  public User getCreatedBy() { return createdBy; }
  public FeedbackStatus getStatus() { return status; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
}
