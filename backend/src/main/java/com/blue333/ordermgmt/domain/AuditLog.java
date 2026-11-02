package com.blue333.ordermgmt.domain;

import com.blue333.ordermgmt.domain.enums.AuditActionType;
import com.blue333.ordermgmt.domain.enums.AuditTargetType;
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
@Table(name = "audit_logs")
public class AuditLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "operator_id", nullable = false)
  private User operator;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_type", nullable = false, length = 30)
  private AuditActionType actionType;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false, length = 30)
  private AuditTargetType targetType;

  @Column(name = "target_id", nullable = false)
  private Long targetId;

  @Column(name = "before_state", length = 30)
  private String beforeState;

  @Column(name = "after_state", length = 30)
  private String afterState;

  @CreationTimestamp
  @Column(name = "operated_at", nullable = false, updatable = false)
  private LocalDateTime operatedAt;

  @Column(nullable = false, length = 20)
  private String result;

  @Column(columnDefinition = "TEXT")
  private String remark;

  protected AuditLog() {
  }

  public static AuditLog create(
      User operator,
      AuditActionType actionType,
      AuditTargetType targetType,
      Long targetId,
      String beforeState,
      String afterState,
      String result,
      String remark) {
    AuditLog auditLog = new AuditLog();
    auditLog.operator = operator;
    auditLog.actionType = actionType;
    auditLog.targetType = targetType;
    auditLog.targetId = targetId;
    auditLog.beforeState = beforeState;
    auditLog.afterState = afterState;
    auditLog.result = result;
    auditLog.remark = remark;
    return auditLog;
  }

  public Long getId() { return id; }
  public User getOperator() { return operator; }
  public AuditActionType getActionType() { return actionType; }
  public AuditTargetType getTargetType() { return targetType; }
  public Long getTargetId() { return targetId; }
  public String getBeforeState() { return beforeState; }
  public String getAfterState() { return afterState; }
  public LocalDateTime getOperatedAt() { return operatedAt; }
  public String getResult() { return result; }
  public String getRemark() { return remark; }
}
