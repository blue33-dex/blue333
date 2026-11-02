package com.blue333.ordermgmt.domain;

import com.blue333.ordermgmt.domain.enums.RoleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "roles")
public class Role {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "role_type", nullable = false, unique = true, length = 5)
  private RoleType roleType;

  @Column(name = "role_name", nullable = false, length = 50)
  private String roleName;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected Role() {
  }

  public Long getId() { return id; }
  public RoleType getRoleType() { return roleType; }
  public String getRoleName() { return roleName; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
