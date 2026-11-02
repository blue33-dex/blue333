package com.blue333.ordermgmt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "requirement_baselines",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_requirement_baselines_order_version",
        columnNames = {"order_id", "baseline_version"}))
public class RequirementBaseline {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(name = "baseline_version", nullable = false)
  private Integer baselineVersion;

  @Column(name = "customer_name", nullable = false, length = 255)
  private String customerName;

  @Column(name = "use_scene", nullable = false, columnDefinition = "TEXT")
  private String useScene;

  @Column(name = "delivery_deadline", nullable = false)
  private LocalDate deliveryDeadline;

  @Column(columnDefinition = "TEXT")
  private String remark;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "material_specs_snapshot", nullable = false, columnDefinition = "json")
  private String materialSpecsSnapshot;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by", nullable = false)
  private User createdBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected RequirementBaseline() {
  }

  public Long getId() { return id; }
  public Order getOrder() { return order; }
  public Integer getBaselineVersion() { return baselineVersion; }
  public String getCustomerName() { return customerName; }
  public String getUseScene() { return useScene; }
  public LocalDate getDeliveryDeadline() { return deliveryDeadline; }
  public String getRemark() { return remark; }
  public String getMaterialSpecsSnapshot() { return materialSpecsSnapshot; }
  public User getCreatedBy() { return createdBy; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
