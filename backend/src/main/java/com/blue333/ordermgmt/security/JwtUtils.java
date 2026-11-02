package com.blue333.ordermgmt.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class JwtUtils {
  private static final String TOKEN_TYPE_CLAIM = "token_type";
  private static final String ACCESS_TOKEN_TYPE = "access";
  private static final String REFRESH_TOKEN_TYPE = "refresh";

  private final SecretKey signingKey;
  private final long accessTokenTtlMs;
  private final long refreshTokenTtlMs;

  public JwtUtils(
      @Value("${security.jwt.secret}") String base64Secret,
      @Value("${security.jwt.access-token-ttl-ms}") long accessTokenTtlMs,
      @Value("${security.jwt.refresh-token-ttl-ms}") long refreshTokenTtlMs) {
    byte[] keyBytes;
    try {
      keyBytes = Decoders.BASE64.decode(base64Secret);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("security.jwt.secret must be Base64-encoded", exception);
    }

    if (keyBytes.length < 32) {
      throw new IllegalStateException("security.jwt.secret must decode to at least 256 bits");
    }
    if (accessTokenTtlMs <= 0 || refreshTokenTtlMs <= 0) {
      throw new IllegalStateException("JWT token TTL values must be positive");
    }

    signingKey = Keys.hmacShaKeyFor(keyBytes);
    this.accessTokenTtlMs = accessTokenTtlMs;
    this.refreshTokenTtlMs = refreshTokenTtlMs;
  }

  public String generateAccessToken(UserDetails userDetails) {
    return generateToken(userDetails.getUsername(), ACCESS_TOKEN_TYPE, accessTokenTtlMs);
  }

  public String generateRefreshToken(UserDetails userDetails) {
    return generateToken(userDetails.getUsername(), REFRESH_TOKEN_TYPE, refreshTokenTtlMs);
  }

  public String getUsernameFromAccessToken(String token) {
    Claims claims = parseClaims(token);
    if (!ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
      throw new IllegalArgumentException("JWT is not an access token");
    }
    return claims.getSubject();
  }

  public boolean isValidAccessToken(String token, UserDetails userDetails) {
    try {
      return userDetails.isEnabled()
          && userDetails.getUsername().equals(getUsernameFromAccessToken(token));
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private String generateToken(String username, String tokenType, long ttlMs) {
    Date now = new Date();
    Date expiration = new Date(now.getTime() + ttlMs);
    return Jwts.builder()
        .subject(username)
        .claim(TOKEN_TYPE_CLAIM, tokenType)
        .issuedAt(now)
        .expiration(expiration)
        .signWith(signingKey)
        .compact();
  }

  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(signingKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}
