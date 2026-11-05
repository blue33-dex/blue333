package com.blue333.ordermgmt.controller;

import com.blue333.ordermgmt.dto.ApiResponse;
import com.blue333.ordermgmt.dto.auth.AuthResponse;
import com.blue333.ordermgmt.dto.auth.AuthenticatedUserResponse;
import com.blue333.ordermgmt.dto.auth.LoginRequest;
import com.blue333.ordermgmt.dto.auth.RefreshTokenRequest;
import com.blue333.ordermgmt.dto.auth.RegisterRequest;
import com.blue333.ordermgmt.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody(required = false) LoginRequest request) {
    if (request == null) {
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok(ApiResponse.of(authService.login(request.username(), request.password())));
  }

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<AuthenticatedUserResponse>> register(
      @RequestBody(required = false) RegisterRequest request) {
    if (request == null) {
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(authService.register(request)));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<AuthResponse>> refresh(
      @RequestBody(required = false) RefreshTokenRequest request) {
    if (request == null) {
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok(ApiResponse.of(authService.refresh(request.refreshToken())));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
    authService.logout(authorizationHeader);
    return ResponseEntity.noContent().build();
  }
}
