package com.blue333.ordermgmt.domain;

import com.blue333.ordermgmt.domain.enums.MaterialTaskStatus;
import com.blue333.ordermgmt.domain.enums.Priority;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "material_tasks")
public class MaterialTask {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "material_spec_id", nullable = false, unique = true)
  private MaterialSpec materialSpec;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "assignee_id", nullable = false)
  private User assignee;

  @Column(name = "task_deadline", nullable = false)
  private LocalDateTime taskDeadline;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Priority priority;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MaterialTaskStatus status;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected MaterialTask() {
  }

  public static MaterialTask create(
      Order order,
      MaterialSpec materialSpec,
      User assignee,
      LocalDateTime taskDeadline,
      Priority priority) {
    MaterialTask task = new MaterialTask();
    task.order = Objects.requireNonNull(order, "order must not be null");
    task.materialSpec = Objects.requireNonNull(materialSpec, "materialSpec must not be null");
    task.assignee = Objects.requireNonNull(assignee, "assignee must not be null");
    task.taskDeadline = Objects.requireNonNull(taskDeadline, "taskDeadline must not be null");
    task.priority = Objects.requireNonNull(priority, "priority must not be null");
    task.status = MaterialTaskStatus.PENDING_DESIGN;
    return task;
  }

  /** Changes status only after the service has validated the state transition. */
  public void changeStatus(MaterialTaskStatus status) {
    this.status = Objects.requireNonNull(status, "status must not be null");
  }

  /** A-07: moves the task to another designer after the service has validated the reassignment. */
  public void reassign(User assignee, LocalDateTime taskDeadline, Priority priority) {
    this.assignee = Objects.requireNonNull(assignee, "assignee must not be null");
    this.taskDeadline = Objects.requireNonNull(taskDeadline, "taskDeadline must not be null");
    this.priority = Objects.requireNonNull(priority, "priority must not be null");
  }

  public Long getId() { return id; }
  public Order getOrder() { return order; }
  public MaterialSpec getMaterialSpec() { return materialSpec; }
  public User getAssignee() { return assignee; }
  public LocalDateTime getTaskDeadline() { return taskDeadline; }
  public Priority getPriority() { return priority; }
  public MaterialTaskStatus getStatus() { return status; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
}
