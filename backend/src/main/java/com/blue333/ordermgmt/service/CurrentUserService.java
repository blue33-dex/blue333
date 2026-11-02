package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.repository.UserRepository;
import com.blue333.ordermgmt.repository.UserRoleRepository;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the authenticated principal in the SecurityContext into the domain user and its roles. */
@Service
public class CurrentUserService {
  private final UserRepository userRepository;
  private final UserRoleRepository userRoleRepository;

  public CurrentUserService(UserRepository userRepository, UserRoleRepository userRoleRepository) {
    this.userRepository = userRepository;
    this.userRoleRepository = userRoleRepository;
  }

  /**
   * Returns the current user. Missing or anonymous authentication, an unknown username, and a
   * disabled account all raise 401 with the same generic reason.
   */
  @Transactional(readOnly = true)
  public CurrentUser requireCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      throw unauthorized();
    }
    String username = authentication.getName();
    if (username == null || username.isBlank()) {
      throw unauthorized();
    }
    User user = userRepository.findByUsername(username).orElseThrow(this::unauthorized);
    if (!Boolean.TRUE.equals(user.getEnabled())) {
      throw unauthorized();
    }
    Set<RoleType> roles = EnumSet.noneOf(RoleType.class);
    userRoleRepository.findByIdUserId(user.getId())
        .forEach(userRole -> roles.add(userRole.getRole().getRoleType()));
    return new CurrentUser(user, roles);
  }

  private ResponseStatusException unauthorized() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
  }
}
