package com.blue333.ordermgmt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.Immutable;

/**
 * Immutable client-confirmation trail (GLOSSARY E-16, BR-10). Rows are only ever inserted: the
 * repository exposes no update/delete methods and database triggers reject both operations.
 */
@Entity
@Table(name = "confirmation_records")
@Immutable
public class ConfirmationRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "confirmer_name", nullable = false, length = 255, updatable = false)
  private String confirmerName;

  @Column(name = "confirmed_at", nullable = false, updatable = false)
  private LocalDateTime confirmedAt;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "version_id", nullable = false, unique = true, updatable = false)
  private DesignVersion version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false, updatable = false)
  private Order order;

  protected ConfirmationRecord() {
  }

  public static ConfirmationRecord create(
      String confirmerName, DesignVersion version, Order order, LocalDateTime confirmedAt) {
    ConfirmationRecord record = new ConfirmationRecord();
    record.confirmerName = Objects.requireNonNull(confirmerName, "confirmerName must not be null");
    record.version = Objects.requireNonNull(version, "version must not be null");
    record.order = Objects.requireNonNull(order, "order must not be null");
    record.confirmedAt = Objects.requireNonNull(confirmedAt, "confirmedAt must not be null");
    return record;
  }

  public Long getId() { return id; }
  public String getConfirmerName() { return confirmerName; }
  public LocalDateTime getConfirmedAt() { return confirmedAt; }
  public DesignVersion getVersion() { return version; }
  public Order getOrder() { return order; }
}
