package com.blue333.ordermgmt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 64)
  private String username;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(unique = true, length = 255)
  private String email;

  @Column(length = 32)
  private String phone;

  @Column(nullable = false)
  private Boolean enabled = true;

  @Column(name = "token_version", nullable = false)
  private Long tokenVersion = 0L;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected User() {
  }

  public Long getId() { return id; }
  public String getUsername() { return username; }
  public String getPasswordHash() { return passwordHash; }
  public String getName() { return name; }
  public String getEmail() { return email; }
  public String getPhone() { return phone; }
  public Boolean getEnabled() { return enabled; }
  public Long getTokenVersion() { return tokenVersion; }

  public void incrementTokenVersion() {
    tokenVersion++;
  }

  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
}
