package com.blue333.ordermgmt.domain;

import com.blue333.ordermgmt.domain.enums.OrderStatus;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "orders")
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_no", nullable = false, unique = true, length = 20)
  private String orderNo;

  @Column(name = "customer_name", nullable = false, length = 255)
  private String customerName;

  @Column(name = "use_scene", nullable = false, columnDefinition = "TEXT")
  private String useScene;

  @Column(name = "delivery_deadline", nullable = false)
  private LocalDate deliveryDeadline;

  @Column(columnDefinition = "TEXT")
  private String remark;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderStatus status;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "am_id", nullable = false)
  private User am;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected Order() {
  }

  public Long getId() { return id; }
  public String getOrderNo() { return orderNo; }
  public String getCustomerName() { return customerName; }
  public String getUseScene() { return useScene; }
  public LocalDate getDeliveryDeadline() { return deliveryDeadline; }
  public String getRemark() { return remark; }
  public OrderStatus getStatus() { return status; }
  public User getAm() { return am; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
}
