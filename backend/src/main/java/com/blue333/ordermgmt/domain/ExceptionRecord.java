package com.blue333.ordermgmt.domain;

import com.blue333.ordermgmt.domain.enums.ExceptionStatus;
import com.blue333.ordermgmt.domain.enums.ExceptionType;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Exception record (E-18): an order-level deviation such as an overdue material task. Records
 * created by the scheduled scan carry no {@code recorder}; manual records always do.
 */
@Entity
@Table(name = "exception_records")
public class ExceptionRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "task_id")
  private MaterialTask task;

  @Enumerated(EnumType.STRING)
  @Column(name = "exception_type", nullable = false, length = 30)
  private ExceptionType exceptionType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ExceptionStatus status;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(columnDefinition = "TEXT")
  private String resolution;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "recorder_id")
  private User recorder;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected ExceptionRecord() {
  }

  public static ExceptionRecord create(
      Order order,
      MaterialTask task,
      ExceptionType exceptionType,
      ExceptionStatus status,
      String description,
      User recorder) {
    ExceptionRecord record = new ExceptionRecord();
    record.order = order;
    record.task = task;
    record.exceptionType = exceptionType;
    record.status = status;
    record.description = description;
    record.recorder = recorder;
    return record;
  }

  public static ExceptionRecord create(
      Order order,
      MaterialTask task,
      ExceptionType exceptionType,
      String description,
      User recorder) {
    return create(order, task, exceptionType, ExceptionStatus.PENDING_CONFIRM, description, recorder);
  }

  /** A-21: only an open record can be closed; the resolution is kept for the archive report. */
  public void close(String resolution, LocalDateTime closedAt) {
    if (status == ExceptionStatus.CLOSED) {
      throw new IllegalStateException("Exception record is already closed");
    }
    this.status = ExceptionStatus.CLOSED;
    this.resolution = resolution;
    this.updatedAt = closedAt;
  }

  public boolean isClosed() {
    return status == ExceptionStatus.CLOSED;
  }

  public Long getId() { return id; }
  public Order getOrder() { return order; }
  public MaterialTask getTask() { return task; }
  public ExceptionType getExceptionType() { return exceptionType; }
  public ExceptionStatus getStatus() { return status; }
  public String getDescription() { return description; }
  public String getResolution() { return resolution; }
  public User getRecorder() { return recorder; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
}
