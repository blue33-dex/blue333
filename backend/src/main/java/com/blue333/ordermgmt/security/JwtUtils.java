package com.blue333.ordermgmt.security;

import com.blue333.ordermgmt.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtils {
  private static final String TOKEN_TYPE_CLAIM = "token_type";
  private static final String TOKEN_VERSION_CLAIM = "token_version";
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

  public String generateAccessToken(User user) {
    return generateToken(user, ACCESS_TOKEN_TYPE, accessTokenTtlMs);
  }

  public String generateRefreshToken(User user) {
    return generateToken(user, REFRESH_TOKEN_TYPE, refreshTokenTtlMs);
  }

  public TokenClaims parseAccessToken(String token) {
    return parseToken(token, ACCESS_TOKEN_TYPE);
  }

  public TokenClaims parseRefreshToken(String token) {
    return parseToken(token, REFRESH_TOKEN_TYPE);
  }

  public boolean isValidForUser(TokenClaims claims, User user) {
    return Boolean.TRUE.equals(user.getEnabled())
        && user.getUsername().equals(claims.username())
        && user.getTokenVersion().equals(claims.tokenVersion());
  }

  private String generateToken(User user, String tokenType, long ttlMs) {
    Date now = new Date();
    return Jwts.builder()
        .subject(user.getUsername())
        .claim(TOKEN_TYPE_CLAIM, tokenType)
        .claim(TOKEN_VERSION_CLAIM, user.getTokenVersion())
        .issuedAt(now)
        .expiration(new Date(now.getTime() + ttlMs))
        .signWith(signingKey)
        .compact();
  }

  private TokenClaims parseToken(String token, String expectedType) {
    Claims claims = Jwts.parser().verifyWith(signingKey).build()
        .parseSignedClaims(token).getPayload();
    if (!expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
      throw new IllegalArgumentException("JWT has an unexpected token type");
    }
    Number version = claims.get(TOKEN_VERSION_CLAIM, Number.class);
    if (claims.getSubject() == null || version == null) {
      throw new IllegalArgumentException("JWT is missing required claims");
    }
    return new TokenClaims(claims.getSubject(), version.longValue());
  }

  public record TokenClaims(String username, long tokenVersion) {
  }
}
