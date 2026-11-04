package com.blue333.ordermgmt.controller;

import com.blue333.ordermgmt.dto.ApiResponse;
import com.blue333.ordermgmt.dto.notification.NotificationResponses.MarkAllReadResponse;
import com.blue333.ordermgmt.dto.notification.NotificationResponses.NotificationResponse;
import com.blue333.ordermgmt.dto.notification.NotificationResponses.UnreadCountResponse;
import com.blue333.ordermgmt.service.CurrentUser;
import com.blue333.ordermgmt.service.CurrentUserService;
import com.blue333.ordermgmt.service.NotificationService;
import com.blue333.ordermgmt.service.NotificationStreamService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Notification center (P4-T09): REST reads plus the SSE stream consumed by the frontend. */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
  private final NotificationService notificationService;
  private final NotificationStreamService streamService;
  private final CurrentUserService currentUserService;

  public NotificationController(NotificationService notificationService,
      NotificationStreamService streamService, CurrentUserService currentUserService) {
    this.notificationService = notificationService;
    this.streamService = streamService;
    this.currentUserService = currentUserService;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<List<NotificationResponse>> list(
      @RequestParam(name = "unread_only", required = false, defaultValue = "false") boolean unreadOnly,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    CurrentUser current = currentUserService.requireCurrentUser();
    return ApiResponse.page(notificationService.listMine(current, cursor, limit, unreadOnly));
  }

  @GetMapping("/unread-count")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<UnreadCountResponse> unreadCount() {
    return ApiResponse.of(notificationService.unreadCount(currentUserService.requireCurrentUser()));
  }

  @PatchMapping("/{notificationId:\\d+}/read")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<NotificationResponse> markRead(@PathVariable Long notificationId) {
    return ApiResponse.of(notificationService.markRead(notificationId,
        currentUserService.requireCurrentUser()));
  }

  @PostMapping("/read-all")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<MarkAllReadResponse> markAllRead() {
    return ApiResponse.of(notificationService.markAllRead(currentUserService.requireCurrentUser()));
  }

  /**
   * SSE stream. Browsers cannot attach an Authorization header to {@code EventSource}, so the
   * frontend reads this stream through {@code fetch} + {@code ReadableStream} and sends the usual JWT
   * header; the response format is standard SSE.
   */
  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @PreAuthorize("isAuthenticated()")
  public SseEmitter stream() {
    return streamService.subscribe(currentUserService.requireCurrentUser().id());
  }
}
