package com.blue333.ordermgmt.domain;

import com.blue333.ordermgmt.domain.enums.ClientReviewStatus;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A client-review session opened by the AM for one released version (GLOSSARY E-14, A-13).
 * A version has at most one session; the session closes when the client confirms the version (A-16).
 */
@Entity
@Table(name = "client_reviews")
public class ClientReview {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "version_id", nullable = false, unique = true, updatable = false)
  private DesignVersion version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false, updatable = false)
  private Order order;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ClientReviewStatus status;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "initiator_id", nullable = false, updatable = false)
  private User initiator;

  @Column(name = "started_at", nullable = false, updatable = false)
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  protected ClientReview() {
  }

  public static ClientReview start(
      DesignVersion version, Order order, User initiator, LocalDateTime startedAt) {
    ClientReview review = new ClientReview();
    review.version = Objects.requireNonNull(version, "version must not be null");
    review.order = Objects.requireNonNull(order, "order must not be null");
    review.initiator = Objects.requireNonNull(initiator, "initiator must not be null");
    review.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
    review.status = ClientReviewStatus.IN_REVIEW;
    return review;
  }

  /** Closes the session after the client confirmed the version (A-16). */
  public void complete(LocalDateTime completedAt) {
    this.status = ClientReviewStatus.COMPLETED;
    this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
  }

  public Long getId() { return id; }
  public DesignVersion getVersion() { return version; }
  public Order getOrder() { return order; }
  public ClientReviewStatus getStatus() { return status; }
  public User getInitiator() { return initiator; }
  public LocalDateTime getStartedAt() { return startedAt; }
  public LocalDateTime getCompletedAt() { return completedAt; }
}
