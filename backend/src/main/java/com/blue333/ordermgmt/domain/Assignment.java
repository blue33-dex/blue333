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
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "assignments")
public class Assignment {
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
