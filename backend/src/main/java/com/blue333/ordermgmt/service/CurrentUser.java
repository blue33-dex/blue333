package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.RoleType;
import java.util.Objects;
import java.util.Set;

/** The authenticated, enabled domain user together with an immutable snapshot of its roles. */
public record CurrentUser(User user, Set<RoleType> roles) {
  public CurrentUser {
    Objects.requireNonNull(user, "user must not be null");
    roles = roles == null ? Set.of() : Set.copyOf(roles);
  }

  public Long id() {
    return user.getId();
  }

  public boolean hasRole(RoleType roleType) {
    return roleType != null && roles.contains(roleType);
  }
}
