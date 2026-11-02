package com.blue333.ordermgmt.domain;

import com.blue333.ordermgmt.domain.enums.MaterialType;
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
@Table(name = "material_specs")
public class MaterialSpec {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Enumerated(EnumType.STRING)
  @Column(name = "material_type", nullable = false, length = 20)
  private MaterialType materialType;

  @Column(name = "spec_size", nullable = false, length = 255)
  private String specSize;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected MaterialSpec() {
  }

  public Long getId() { return id; }
  public Order getOrder() { return order; }
  public MaterialType getMaterialType() { return materialType; }
  public String getSpecSize() { return specSize; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
