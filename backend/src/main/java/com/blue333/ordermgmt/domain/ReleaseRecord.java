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

/** A BO decision to release a version to client review (GLOSSARY E-13). Written by the internal-review service. */
@Entity
@Table(name = "release_records")
public class ReleaseRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "version_id", nullable = false, unique = true, updatable = false)
  private DesignVersion version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "releaser_id", nullable = false, updatable = false)
  private User releaser;

  @Column(name = "release_remark", columnDefinition = "TEXT", updatable = false)
  private String releaseRemark;

  @Column(name = "released_at", nullable = false, updatable = false)
  private LocalDateTime releasedAt;

  protected ReleaseRecord() {
  }

  public static ReleaseRecord create(DesignVersion version, User releaser, String remark, LocalDateTime releasedAt) {
    ReleaseRecord rec = new ReleaseRecord();
    rec.version = Objects.requireNonNull(version, "version must not be null");
    rec.releaser = Objects.requireNonNull(releaser, "releaser must not be null");
    rec.releaseRemark = remark;
    rec.releasedAt = Objects.requireNonNull(releasedAt, "releasedAt must not be null");
    return rec;
  }

  public Long getId() { return id; }
  public DesignVersion getVersion() { return version; }
  public User getReleaser() { return releaser; }
  public String getReleaseRemark() { return releaseRemark; }
  public LocalDateTime getReleasedAt() { return releasedAt; }
}
