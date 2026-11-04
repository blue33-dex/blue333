package com.blue333.ordermgmt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;

/** The stored file of a design version (GLOSSARY E-11); one file per version. */
@Entity
@Table(name = "version_files")
public class VersionFile {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "version_id", nullable = false, unique = true, updatable = false)
  private DesignVersion version;

  @Column(name = "file_name", nullable = false, length = 255, updatable = false)
  private String fileName;

  @Column(name = "file_size", nullable = false, updatable = false)
  private Long fileSize;

  @Column(name = "storage_path", nullable = false, unique = true, length = 512, updatable = false)
  private String storagePath;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected VersionFile() {
  }

  public static VersionFile register(DesignVersion version, String fileName, long fileSize, String storagePath) {
    VersionFile file = new VersionFile();
    file.version = Objects.requireNonNull(version, "version must not be null");
    file.fileName = Objects.requireNonNull(fileName, "fileName must not be null");
    file.fileSize = fileSize;
    file.storagePath = Objects.requireNonNull(storagePath, "storagePath must not be null");
    return file;
  }

  public Long getId() { return id; }
  public DesignVersion getVersion() { return version; }
  public String getFileName() { return fileName; }
  public Long getFileSize() { return fileSize; }
  public String getStoragePath() { return storagePath; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
