package com.blue333.ordermgmt.domain;

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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "assignments")
public class Assignment {
  /**
   * Kept as a controlled persistence value until Phase 2 introduces the assignment-type enum.
   * The column is intentionally a String today to remain compatible with the existing schema.
   */
  public static final String INITIAL_ASSIGNMENT_TYPE = "INITIAL";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "task_id", nullable = false)
  private MaterialTask task;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "assigner_id", nullable = false)
  private User assigner;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "assignee_id", nullable = false)
  private User assignee;

  @Column(name = "task_deadline", nullable = false)
  private LocalDateTime taskDeadline;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Priority priority;

  @Column(name = "remark", columnDefinition = "TEXT")
  private String remark;

  @Column(name = "assignment_type", nullable = false, length = 10)
  private String assignmentType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "previous_assignee_id")
  private User previousAssignee;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected Assignment() {
  }

  public static Assignment initial(
      MaterialTask task,
      User assigner,
      User assignee,
      LocalDateTime taskDeadline,
      Priority priority,
      String remark) {
    Assignment assignment = new Assignment();
    assignment.task = Objects.requireNonNull(task, "task must not be null");
    assignment.assigner = Objects.requireNonNull(assigner, "assigner must not be null");
    assignment.assignee = Objects.requireNonNull(assignee, "assignee must not be null");
    assignment.taskDeadline = Objects.requireNonNull(taskDeadline, "taskDeadline must not be null");
    assignment.priority = Objects.requireNonNull(priority, "priority must not be null");
    assignment.remark = remark;
    assignment.assignmentType = INITIAL_ASSIGNMENT_TYPE;
    return assignment;
  }

  public Long getId() { return id; }
  public MaterialTask getTask() { return task; }
  public User getAssigner() { return assigner; }
  public User getAssignee() { return assignee; }
  public LocalDateTime getTaskDeadline() { return taskDeadline; }
  public Priority getPriority() { return priority; }
  public String getRemark() { return remark; }
  public String getAssignmentType() { return assignmentType; }
  public User getPreviousAssignee() { return previousAssignee; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
