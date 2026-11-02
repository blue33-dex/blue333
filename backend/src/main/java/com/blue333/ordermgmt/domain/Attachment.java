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
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "attachments")
public class Attachment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  @Column(name = "storage_path", nullable = false, unique = true, length = 512)
  private String storagePath;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "uploaded_by", nullable = false)
  private User uploadedBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected Attachment() {
  }

  public static Attachment register(
      Order order, String fileName, long fileSize, String storagePath, User uploadedBy) {
    Attachment attachment = new Attachment();
    attachment.order = Objects.requireNonNull(order, "order must not be null");
    attachment.fileName = Objects.requireNonNull(fileName, "fileName must not be null");
    attachment.fileSize = fileSize;
    attachment.storagePath = Objects.requireNonNull(storagePath, "storagePath must not be null");
    attachment.uploadedBy = Objects.requireNonNull(uploadedBy, "uploadedBy must not be null");
    return attachment;
  }

  public Long getId() { return id; }
  public Order getOrder() { return order; }
  public String getFileName() { return fileName; }
  public Long getFileSize() { return fileSize; }
  public String getStoragePath() { return storagePath; }
  public User getUploadedBy() { return uploadedBy; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
