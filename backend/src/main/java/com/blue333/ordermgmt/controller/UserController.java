package com.blue333.ordermgmt.controller;

import com.blue333.ordermgmt.domain.enums.RoleType;
import com.blue333.ordermgmt.dto.ApiResponse;
import com.blue333.ordermgmt.repository.UserRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
  private final UserRepository userRepository;
  public UserController(UserRepository userRepository) { this.userRepository = userRepository; }
  @GetMapping("/designers")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<List<DesignerResponse>> designers() {
    return ApiResponse.of(userRepository.findEnabledByRoleType(RoleType.DS).stream()
        .map(user -> new DesignerResponse(user.getId(), user.getUsername(), user.getName())).toList());
  }
  public record DesignerResponse(@JsonProperty("id") Long id, @JsonProperty("username") String username, @JsonProperty("name") String name) {}
}
