package com.blue333.ordermgmt.security;

import com.blue333.ordermgmt.repository.UserRepository;
import com.blue333.ordermgmt.repository.UserRoleRepository;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {
  private final UserRepository userRepository;
  private final UserRoleRepository userRoleRepository;

  public CustomUserDetailsService(
      UserRepository userRepository, UserRoleRepository userRoleRepository) {
    this.userRepository = userRepository;
    this.userRoleRepository = userRoleRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    com.blue333.ordermgmt.domain.User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new UsernameNotFoundException("User not found"));

    List<SimpleGrantedAuthority> authorities = userRoleRepository.findByIdUserId(user.getId())
        .stream()
        .map(userRole -> new SimpleGrantedAuthority(
            "ROLE_" + userRole.getRole().getRoleType().name()))
        .toList();

    return org.springframework.security.core.userdetails.User.builder()
        .username(user.getUsername())
        .password(user.getPasswordHash())
        .authorities(authorities)
        .disabled(!Boolean.TRUE.equals(user.getEnabled()))
        .build();
  }
}
