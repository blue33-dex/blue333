package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.blue333.ordermgmt.domain.Role;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.UserRole;
import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.repository.UserRepository;
import com.blue333.ordermgmt.repository.UserRoleRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class CurrentUserServiceTest {
  private UserRepository userRepository;
  private UserRoleRepository userRoleRepository;
  private CurrentUserService service;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    userRoleRepository = mock(UserRoleRepository.class);
    service = new CurrentUserService(userRepository, userRoleRepository);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void resolvesAuthenticatedEnabledUserWithRoles() {
    User user = user(9L, true);
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
    List<UserRole> userRoles = List.of(userRole(RoleType.AM), userRole(RoleType.DS));
    when(userRoleRepository.findByIdUserId(9L)).thenReturn(userRoles);
    authenticate("alice");

    CurrentUser current = service.requireCurrentUser();

    assertThat(current.user()).isSameAs(user);
    assertThat(current.roles()).containsExactlyInAnyOrder(RoleType.AM, RoleType.DS);
    assertThat(current.hasRole(RoleType.BO)).isFalse();
    assertThatThrownBy(() -> current.roles().add(RoleType.BO)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsMissingAnonymousUnknownAndDisabledUsersWith401() {
    assertUnauthorized();

    SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    assertUnauthorized();

    authenticate("ghost");
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
    assertUnauthorized();

    User disabled = user(3L, false);
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(disabled));
    authenticate("bob");
    assertUnauthorized();
  }

  private void assertUnauthorized() {
    assertThatThrownBy(service::requireCurrentUser)
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
  }

  private void authenticate(String username) {
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
  }

  private User user(Long id, boolean enabled) {
    User user = mock(User.class);
    when(user.getId()).thenReturn(id);
    when(user.getEnabled()).thenReturn(enabled);
    return user;
  }

  private UserRole userRole(RoleType roleType) {
    Role role = mock(Role.class);
    when(role.getRoleType()).thenReturn(roleType);
    UserRole userRole = mock(UserRole.class);
    when(userRole.getRole()).thenReturn(role);
    return userRole;
  }
}
