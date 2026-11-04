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

/**
 * The closed-loop archive record of a delivered order (GLOSSARY E-17, A-19). Archived orders are
 * COMPLETED forever (BR-12); the stored report is the exportable PDF timeline (A-28).
 */
@Entity
@Table(name = "delivery_archives")
public class DeliveryArchive {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "archiver_id", nullable = false, updatable = false)
  private User archiver;

  @Column(name = "archived_at", nullable = false, updatable = false)
  private LocalDateTime archivedAt;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false, unique = true, updatable = false)
  private Order order;

  @Column(name = "archive_report_path", nullable = false, unique = true, length = 512, updatable = false)
  private String archiveReportPath;

  protected DeliveryArchive() {
  }

  public static DeliveryArchive create(
      Order order, User archiver, String archiveReportPath, LocalDateTime archivedAt) {
    DeliveryArchive archive = new DeliveryArchive();
    archive.order = Objects.requireNonNull(order, "order must not be null");
    archive.archiver = Objects.requireNonNull(archiver, "archiver must not be null");
    archive.archiveReportPath = Objects.requireNonNull(archiveReportPath, "archiveReportPath must not be null");
    archive.archivedAt = Objects.requireNonNull(archivedAt, "archivedAt must not be null");
    return archive;
  }

  public Long getId() { return id; }
  public User getArchiver() { return archiver; }
  public LocalDateTime getArchivedAt() { return archivedAt; }
  public Order getOrder() { return order; }
  public String getArchiveReportPath() { return archiveReportPath; }
}
