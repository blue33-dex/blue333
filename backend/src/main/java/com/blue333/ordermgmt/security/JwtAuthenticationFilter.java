package com.blue333.ordermgmt.security;

import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtUtils jwtUtils;
  private final UserDetailsService userDetailsService;
  private final UserRepository userRepository;

  public JwtAuthenticationFilter(
      JwtUtils jwtUtils, UserDetailsService userDetailsService, UserRepository userRepository) {
    this.jwtUtils = jwtUtils;
    this.userDetailsService = userDetailsService;
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
    if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = authorizationHeader.substring(BEARER_PREFIX.length());
    if (!token.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
      try {
        JwtUtils.TokenClaims claims = jwtUtils.parseAccessToken(token);
        User user = userRepository.findByUsername(claims.username())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        UserDetails userDetails = userDetailsService.loadUserByUsername(claims.username());
        if (jwtUtils.isValidForUser(claims, user)) {
          UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
          authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(authentication);
        }
      } catch (RuntimeException exception) {
        SecurityContextHolder.clearContext();
      }
    }

    filterChain.doFilter(request, response);
  }
}
