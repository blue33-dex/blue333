package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.dto.auth.AuthResponse;
import com.blue333.ordermgmt.dto.auth.AuthenticatedUserResponse;
import com.blue333.ordermgmt.repository.UserRepository;
import com.blue333.ordermgmt.repository.UserRoleRepository;
import com.blue333.ordermgmt.security.JwtUtils;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  private final UserRepository userRepository;
  private final UserRoleRepository userRoleRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtils jwtUtils;

  public AuthService(
      UserRepository userRepository,
      UserRoleRepository userRoleRepository,
      PasswordEncoder passwordEncoder,
      JwtUtils jwtUtils) {
    this.userRepository = userRepository;
    this.userRoleRepository = userRoleRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtils = jwtUtils;
  }

  @Transactional(readOnly = true)
  public AuthResponse login(String username, String password) {
    if (isBlank(username) || isBlank(password)) {
      throw badRequest("Username and password are required");
    }
    User user = userRepository.findByUsername(username).orElseThrow(this::invalidCredentials);
    if (!Boolean.TRUE.equals(user.getEnabled()) || !passwordEncoder.matches(password, user.getPasswordHash())) {
      throw invalidCredentials();
    }
    return issueTokens(user);
  }

  @Transactional
  public AuthResponse refresh(String refreshToken) {
    if (isBlank(refreshToken)) {
      throw badRequest("Refresh token is required");
    }
    JwtUtils.TokenClaims claims = parseRefreshToken(refreshToken);
    User user = userRepository.findByUsernameForUpdate(claims.username()).orElseThrow(this::unauthorized);
    if (!jwtUtils.isValidForUser(claims, user)) {
      throw unauthorized();
    }
    user.incrementTokenVersion();
    return issueTokens(user);
  }

  @Transactional
  public void logout(String authorizationHeader) {
    if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
      throw unauthorized();
    }
    String accessToken = authorizationHeader.substring("Bearer ".length());
    if (isBlank(accessToken)) {
      throw unauthorized();
    }
    JwtUtils.TokenClaims claims;
    try {
      claims = jwtUtils.parseAccessToken(accessToken);
    } catch (RuntimeException exception) {
      throw unauthorized();
    }
    User user = userRepository.findByUsernameForUpdate(claims.username()).orElseThrow(this::unauthorized);
    if (!jwtUtils.isValidForUser(claims, user)) {
      throw unauthorized();
    }
    user.incrementTokenVersion();
  }

  private JwtUtils.TokenClaims parseRefreshToken(String refreshToken) {
    try {
      return jwtUtils.parseRefreshToken(refreshToken);
    } catch (RuntimeException exception) {
      throw unauthorized();
    }
  }

  private AuthResponse issueTokens(User user) {
    return new AuthResponse(
        jwtUtils.generateAccessToken(user),
        jwtUtils.generateRefreshToken(user),
        new AuthenticatedUserResponse(user.getId(), user.getUsername(), user.getName(), rolesFor(user)));
  }

  private List<String> rolesFor(User user) {
    return userRoleRepository.findByIdUserId(user.getId()).stream()
        .map(userRole -> userRole.getRole().getRoleType().name())
        .toList();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private ResponseStatusException invalidCredentials() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
  }

  private ResponseStatusException unauthorized() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
  }

  private ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
