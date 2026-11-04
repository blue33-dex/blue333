package com.blue333.ordermgmt.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies the {@code upload_session_id} that links upload Step 1 (presign) to Step 3
 * (S-09 confirm-upload) without a server-side session table.
 *
 * <p>The token is {@code base64url(json payload) + "." + base64url(HMAC-SHA256)}. The HMAC key is
 * derived from the JWT secret with a fixed context label, so an upload session can never be used as
 * an access token and vice versa. The payload pins the task, the uploader, the object key, and the
 * declared file size, so Step 3 cannot be pointed at a different object or task.</p>
 */
@Component
public class VersionUploadSessionCodec {
  public static final Duration SESSION_TTL = Duration.ofMinutes(60);
  private static final String KEY_CONTEXT = "ordermgmt:version-upload-session:v1";
  private static final String HMAC = "HmacSHA256";
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  private final byte[] key;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public VersionUploadSessionCodec(
      @Value("${security.jwt.secret}") String base64Secret, ObjectMapper objectMapper, Clock clock) {
    this.key = hmac(Base64.getDecoder().decode(base64Secret), KEY_CONTEXT.getBytes(StandardCharsets.UTF_8));
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public String issue(long taskId, long uploaderId, String objectKey, String fileName, long fileSize) {
    UploadSession session = new UploadSession(taskId, uploaderId, objectKey, fileName, fileSize,
        clock.instant().plus(SESSION_TTL).getEpochSecond());
    try {
      String payload = ENCODER.encodeToString(objectMapper.writeValueAsBytes(session));
      return payload + "." + ENCODER.encodeToString(hmac(key, payload.getBytes(StandardCharsets.US_ASCII)));
    } catch (Exception exception) {
      throw new IllegalStateException("Could not create an upload session", exception);
    }
  }

  /** Returns the session, or throws {@link IllegalArgumentException} when tampered with or expired. */
  public UploadSession verify(String token) {
    if (token == null || token.isBlank() || token.length() > 4096) {
      throw invalid();
    }
    int dot = token.indexOf('.');
    if (dot <= 0 || dot != token.lastIndexOf('.') || dot == token.length() - 1) {
      throw invalid();
    }
    String payload = token.substring(0, dot);
    byte[] signature;
    try {
      signature = DECODER.decode(token.substring(dot + 1));
    } catch (IllegalArgumentException exception) {
      throw invalid();
    }
    if (!MessageDigest.isEqual(signature, hmac(key, payload.getBytes(StandardCharsets.US_ASCII)))) {
      throw invalid();
    }
    UploadSession session;
    try {
      session = objectMapper.readValue(DECODER.decode(payload), UploadSession.class);
    } catch (Exception exception) {
      throw invalid();
    }
    if (clock.instant().getEpochSecond() >= session.expiresAt()) {
      throw new IllegalArgumentException("Upload session has expired; start the upload again");
    }
    return session;
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Upload session is invalid");
  }

  private static byte[] hmac(byte[] key, byte[] data) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(key, HMAC));
      return mac.doFinal(data);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
    }
  }

  public record UploadSession(
      @JsonProperty("t") long taskId,
      @JsonProperty("u") long uploaderId,
      @JsonProperty("k") String objectKey,
      @JsonProperty("n") String fileName,
      @JsonProperty("s") long fileSize,
      @JsonProperty("e") long expiresAt) {
  }
}
