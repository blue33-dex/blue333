package com.blue333.ordermgmt.controller;

import com.blue333.ordermgmt.domain.enums.FeedbackSource;
import com.blue333.ordermgmt.domain.enums.FeedbackType;
import com.blue333.ordermgmt.dto.ApiResponse;
import com.blue333.ordermgmt.dto.clientreview.ClientReviewResponses.ClientReviewResponse;
import com.blue333.ordermgmt.dto.clientreview.ClientReviewResponses.ConfirmVersionResponse;
import com.blue333.ordermgmt.dto.clientreview.ClientReviewResponses.FeedbackResponse;
import com.blue333.ordermgmt.service.ClientReviewService;
import com.blue333.ordermgmt.service.CurrentUser;
import com.blue333.ordermgmt.service.CurrentUserService;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Phase 3 client-review endpoints: A-13, A-14, A-15, A-16, A-27. */
@RestController
@RequestMapping("/api/v1")
public class ClientReviewController {
  private final ClientReviewService clientReviewService;
  private final CurrentUserService currentUserService;

  public ClientReviewController(
      ClientReviewService clientReviewService, CurrentUserService currentUserService) {
    this.clientReviewService = clientReviewService;
    this.currentUserService = currentUserService;
  }

  /** A-13: opens client review for a released version (RELEASED → IN_CLIENT_REVIEW). */
  @PostMapping("/versions/{versionId:\\d+}/client-review")
  @PreAuthorize("hasRole('AM')")
  public ResponseEntity<ApiResponse<ClientReviewResponse>> initiate(
      @PathVariable Long versionId) {
    CurrentUser current = currentUserService.requireCurrentUser();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(clientReviewService.initiateClientReview(versionId, current)));
  }

  /** A-14: records version-bound feedback; revision feedback returns the task to PENDING_REVISE. */
  @PostMapping("/versions/{versionId:\\d+}/feedbacks")
  @PreAuthorize("hasRole('AM')")
  public ResponseEntity<ApiResponse<FeedbackResponse>> recordFeedback(
      @PathVariable Long versionId, @Valid @RequestBody RecordFeedbackRequest request) {
    CurrentUser current = currentUserService.requireCurrentUser();
    FeedbackResponse response = clientReviewService.recordFeedback(
        versionId, request.feedbackType(), request.source(), request.content(), current);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
  }

  /** A-27: feedback list of one version (AM own order, BO all, DS assigned task). */
  @GetMapping("/versions/{versionId:\\d+}/feedbacks")
  @PreAuthorize("hasAnyRole('AM','BO','DS')")
  public ApiResponse<List<FeedbackResponse>> listFeedbacks(@PathVariable Long versionId) {
    return ApiResponse.of(clientReviewService.listFeedbacks(
        versionId, currentUserService.requireCurrentUser()));
  }

  /** A-15: the assigned designer resolves a feedback item (EXPIRED items are rejected). */
  @PatchMapping("/feedbacks/{feedbackId:\\d+}/resolve")
  @PreAuthorize("hasRole('DS')")
  public ApiResponse<FeedbackResponse> resolve(@PathVariable Long feedbackId) {
    return ApiResponse.of(clientReviewService.resolveFeedback(
        feedbackId, currentUserService.requireCurrentUser()));
  }

  /** A-16: confirms the client-approved version and approves the material task (CONFLICT-01). */
  @PostMapping("/versions/{versionId:\\d+}/confirm")
  @PreAuthorize("hasRole('AM')")
  public ApiResponse<ConfirmVersionResponse> confirm(
      @PathVariable Long versionId, @Valid @RequestBody ConfirmVersionRequest request) {
    return ApiResponse.of(clientReviewService.confirmVersion(
        versionId, request.confirmerName(), currentUserService.requireCurrentUser()));
  }

  public record RecordFeedbackRequest(
      @NotNull @JsonProperty("feedback_type") FeedbackType feedbackType,
      @JsonProperty("source") FeedbackSource source,
      @NotBlank @JsonProperty("content") String content) {
  }

  public record ConfirmVersionRequest(
      @NotBlank @JsonProperty("confirmer_name") String confirmerName) {
  }
}
