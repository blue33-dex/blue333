package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.blue333.ordermgmt.service.VersionUploadSessionCodec.UploadSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class VersionUploadSessionCodecTest {
  private static final String SECRET = "2XcqcBQNpAWbyzwOq/t2OemNfavFwSRAMiAdHk8kex0=";
  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");

  private final VersionUploadSessionCodec codec = codecAt(NOW);

  @Test
  void roundTripsTheSession() {
    String token = codec.issue(7L, 3L, "versions/task-7/a.pdf", "brief.pdf", 1234L);

    UploadSession session = codec.verify(token);
    assertThat(session.taskId()).isEqualTo(7L);
    assertThat(session.uploaderId()).isEqualTo(3L);
    assertThat(session.objectKey()).isEqualTo("versions/task-7/a.pdf");
    assertThat(session.fileName()).isEqualTo("brief.pdf");
    assertThat(session.fileSize()).isEqualTo(1234L);
  }

  @Test
  void rejectsAModifiedPayload() {
    String token = codec.issue(7L, 3L, "versions/task-7/a.pdf", "brief.pdf", 1234L);
    String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
        "{\"t\":8,\"u\":3,\"k\":\"versions/task-8/a.pdf\",\"n\":\"brief.pdf\",\"s\":1234,\"e\":9999999999}"
            .getBytes(StandardCharsets.UTF_8));
    String forged = forgedPayload + token.substring(token.indexOf('.'));

    assertThatIllegalArgumentException().isThrownBy(() -> codec.verify(forged));
  }

  @Test
  void rejectsATokenSignedWithAnotherKey() {
    VersionUploadSessionCodec other = new VersionUploadSessionCodec(
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    String token = other.issue(7L, 3L, "versions/task-7/a.pdf", "brief.pdf", 1234L);

    assertThatIllegalArgumentException().isThrownBy(() -> codec.verify(token));
  }

  @Test
  void rejectsExpiredSessions() {
    String token = codec.issue(7L, 3L, "versions/task-7/a.pdf", "brief.pdf", 1234L);
    VersionUploadSessionCodec later = codecAt(NOW.plus(VersionUploadSessionCodec.SESSION_TTL));

    assertThatIllegalArgumentException().isThrownBy(() -> later.verify(token)).withMessageContaining("expired");
  }

  @Test
  void rejectsGarbage() {
    for (String token : new String[] {null, "", "abc", "a.b.c", "....", "x.", ".y"}) {
      assertThatIllegalArgumentException().isThrownBy(() -> codec.verify(token));
    }
  }

  private static VersionUploadSessionCodec codecAt(Instant instant) {
    return new VersionUploadSessionCodec(SECRET, new ObjectMapper(), Clock.fixed(instant, ZoneOffset.UTC));
  }
}
