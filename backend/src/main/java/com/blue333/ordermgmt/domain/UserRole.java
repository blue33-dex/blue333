package com.blue333.ordermgmt.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.Column;

@Entity
@Table(name = "user_roles")
public class UserRole {
  @EmbeddedId
  private UserRoleId id;

  @MapsId("userId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @MapsId("roleId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "role_id", nullable = false)
  private Role role;

  @CreationTimestamp
  @Column(name = "assigned_at", nullable = false, updatable = false)
  private LocalDateTime assignedAt;

  protected UserRole() {
  }

  /** 绑定已持久化的用户与角色。 */
  public UserRole(User user, Role role) {
    this.id = new UserRoleId(user.getId(), role.getId());
    this.user = user;
    this.role = role;
  }

  public UserRoleId getId() { return id; }
  public User getUser() { return user; }
  public Role getRole() { return role; }
  public LocalDateTime getAssignedAt() { return assignedAt; }
}
