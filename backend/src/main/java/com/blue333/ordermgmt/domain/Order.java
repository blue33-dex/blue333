package com.blue333.ordermgmt.domain;

import com.blue333.ordermgmt.domain.enums.OrderStatus;
import com.blue333.ordermgmt.domain.enums.RejectReasonType;
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
import java.util.Objects;
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

  @Enumerated(EnumType.STRING)
  @Column(name = "last_reject_reason_type", length = 30)
  private RejectReasonType lastRejectReasonType;

  @Column(name = "last_reject_remark", columnDefinition = "TEXT")
  private String lastRejectRemark;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "last_rejected_by")
  private User lastRejectedBy;

  @Column(name = "last_rejected_at")
  private LocalDateTime lastRejectedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected Order() {
  }

  public static Order create(
      String orderNo,
      String customerName,
      String useScene,
      LocalDate deliveryDeadline,
      String remark,
      User am) {
    Order order = new Order();
    order.orderNo = Objects.requireNonNull(orderNo, "orderNo must not be null");
    order.customerName = Objects.requireNonNull(customerName, "customerName must not be null");
    order.useScene = Objects.requireNonNull(useScene, "useScene must not be null");
    order.deliveryDeadline = Objects.requireNonNull(deliveryDeadline, "deliveryDeadline must not be null");
    order.remark = remark;
    order.am = Objects.requireNonNull(am, "am must not be null");
    order.status = OrderStatus.PENDING_CONFIRM;
    return order;
  }

  /** Changes status only after the service has validated the transition. */
  public void changeStatus(OrderStatus status) {
    this.status = Objects.requireNonNull(status, "status must not be null");
  }

  /**
   * Records the most recent rejection (BO-02). The service validates the order state and sanitizes
   * the remark before calling this method; a later rejection overwrites the previous one.
   */
  public void recordRejection(
      RejectReasonType reasonType, String remark, User rejectedBy, LocalDateTime rejectedAt) {
    this.lastRejectReasonType = Objects.requireNonNull(reasonType, "reasonType must not be null");
    this.lastRejectRemark = Objects.requireNonNull(remark, "remark must not be null");
    this.lastRejectedBy = Objects.requireNonNull(rejectedBy, "rejectedBy must not be null");
    this.lastRejectedAt = Objects.requireNonNull(rejectedAt, "rejectedAt must not be null");
  }

  public Long getId() { return id; }
  public String getOrderNo() { return orderNo; }
  public String getCustomerName() { return customerName; }
  public String getUseScene() { return useScene; }
  public LocalDate getDeliveryDeadline() { return deliveryDeadline; }
  public String getRemark() { return remark; }
  public OrderStatus getStatus() { return status; }
  public User getAm() { return am; }
  public RejectReasonType getLastRejectReasonType() { return lastRejectReasonType; }
  public String getLastRejectRemark() { return lastRejectRemark; }
  public User getLastRejectedBy() { return lastRejectedBy; }
  public LocalDateTime getLastRejectedAt() { return lastRejectedAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
}
