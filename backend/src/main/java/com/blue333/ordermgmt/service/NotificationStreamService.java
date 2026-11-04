package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.dto.notification.NotificationResponses.NotificationResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events hub for the notification center (P4-T09).
 *
 * <p>One emitter per open browser tab, keyed by user id. Emitters are removed on completion, timeout
 * or error so a disconnected client cannot leak. Because the application authenticates with a JWT
 * header, the frontend consumes this stream with {@code fetch} + {@code ReadableStream} rather than
 * {@code EventSource}; the endpoint is plain SSE either way.</p>
 */
@Service
public class NotificationStreamService {
  private static final Logger LOGGER = LoggerFactory.getLogger(NotificationStreamService.class);
  private static final long EMITTER_TIMEOUT_MILLIS = 30L * 60L * 1000L;

  private final Map<Long, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

  public SseEmitter subscribe(Long userId) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("A persisted user is required to subscribe");
    }
    SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
    emittersByUser.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);
    emitter.onCompletion(() -> remove(userId, emitter));
    emitter.onTimeout(() -> {
      remove(userId, emitter);
      emitter.complete();
    });
    emitter.onError(error -> remove(userId, emitter));
    try {
      emitter.send(SseEmitter.event().name("connected").data(Map.of("user_id", userId)));
    } catch (IOException | IllegalStateException exception) {
      remove(userId, emitter);
      LOGGER.debug("Unable to send the SSE connect event for user {}", userId, exception);
    }
    return emitter;
  }

  /** Pushes one stored notification to every open tab of its recipient. */
  public void publish(Long userId, NotificationResponse notification) {
    if (userId == null || notification == null) {
      return;
    }
    List<SseEmitter> emitters = emittersByUser.get(userId);
    if (emitters == null || emitters.isEmpty()) {
      return;
    }
    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event().name("notification").data(notification));
      } catch (IOException | IllegalStateException exception) {
        LOGGER.debug("Removing a stale SSE emitter for user {}", userId, exception);
        remove(userId, emitter);
      }
    }
  }

  /** Keeps proxies and load balancers from closing idle connections. */
  @Scheduled(fixedRateString = "${app.sse.heartbeat-ms:25000}")
  public void heartbeat() {
    emittersByUser.forEach((userId, emitters) -> {
      for (SseEmitter emitter : emitters) {
        try {
          emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException | IllegalStateException exception) {
          remove(userId, emitter);
        }
      }
    });
  }

  /** Visible for operations/tests: number of open streams of one user. */
  public int openStreamCount(Long userId) {
    List<SseEmitter> emitters = emittersByUser.get(userId);
    return emitters == null ? 0 : emitters.size();
  }

  private void remove(Long userId, SseEmitter emitter) {
    List<SseEmitter> emitters = emittersByUser.get(userId);
    if (emitters == null) {
      return;
    }
    emitters.remove(emitter);
    if (emitters.isEmpty()) {
      emittersByUser.remove(userId, emitters);
    }
  }
}
