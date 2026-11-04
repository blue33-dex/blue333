package com.blue333.ordermgmt.controller;

import com.blue333.ordermgmt.domain.DesignVersion;
import com.blue333.ordermgmt.dto.ApiResponse;
import com.blue333.ordermgmt.dto.version.VersionResponses.UploadStartResponse;
import com.blue333.ordermgmt.dto.version.VersionResponses.VersionComparisonResponse;
import com.blue333.ordermgmt.dto.version.VersionResponses.VersionResponse;
import com.blue333.ordermgmt.service.CurrentUser;
import com.blue333.ordermgmt.service.CurrentUserService;
import com.blue333.ordermgmt.service.DesignVersionService;
import com.blue333.ordermgmt.service.DesignVersionService.UploadStart;
import com.blue333.ordermgmt.service.FileStorageService;
import com.blue333.ordermgmt.service.InternalReviewService;
import com.blue333.ordermgmt.service.VersionQueryService;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** M3 design-version endpoints: A-08, S-09, A-09, A-10, A-11, A-12, A-17, A-26, S-07. */
@RestController
@RequestMapping("/api/v1")
public class DesignVersionController {
  private final CurrentUserService currentUserService;
  private final DesignVersionService versionService;
  private final VersionQueryService versionQueryService;
  private final InternalReviewService reviewService;
  private final FileStorageService fileStorageService;

  public DesignVersionController(CurrentUserService currentUserService, DesignVersionService versionService,
      VersionQueryService versionQueryService, InternalReviewService reviewService,
      FileStorageService fileStorageService) {
    this.currentUserService = currentUserService;
    this.versionService = versionService;
    this.versionQueryService = versionQueryService;
    this.reviewService = reviewService;
    this.fileStorageService = fileStorageService;
  }

  /** A-08 Step 1: presigned PUT URL for a new version of one of the caller's tasks. */
  @PostMapping("/material-tasks/{taskId:\\d+}/versions")
  @PreAuthorize("hasRole('DS')")
  public ApiResponse<UploadStartResponse> startUpload(@PathVariable Long taskId,
      @Valid @RequestBody StartUploadRequest request) {
    UploadStart start = versionService.startUpload(taskId, request.fileName(), request.fileSize(),
        currentUserService.requireCurrentUser());
    return ApiResponse.of(new UploadStartResponse(start.uploadSessionId(), start.presigned().presignedUrl(),
        start.presigned().objectKey(), start.presigned().expiresInMinutes(), start.nextVersionNo()));
  }

  /** S-09 Step 3: records the uploaded file as a DRAFT version with an automatic version number. */
  @PostMapping("/versions/confirm-upload")
  @PreAuthorize("hasRole('DS')")
  public ResponseEntity<ApiResponse<VersionResponse>> confirmUpload(
      @Valid @RequestBody ConfirmUploadRequest request) {
    CurrentUser current = currentUserService.requireCurrentUser();
    DesignVersion version = versionService.confirmUpload(request.uploadSessionId(), request.designNote(),
        request.changeSummary(), current);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.of(versionQueryService.detail(version.getId(), current)));
  }

  /** A-09: submits a DRAFT version for BO internal review. */
  @PostMapping("/versions/{versionId:\\d+}/submit-review")
  @PreAuthorize("hasRole('DS')")
  public ApiResponse<VersionResponse> submitForReview(@PathVariable Long versionId) {
    CurrentUser current = currentUserService.requireCurrentUser();
    DesignVersion version = versionService.submitForReview(versionId, current);
    return ApiResponse.of(versionQueryService.detail(version.getId(), current));
  }

  /** A-26: version history of an order, filtered by the caller's visibility. */
  @GetMapping("/orders/{orderId:\\d+}/versions")
  @PreAuthorize("hasAnyRole('AM','BO','DS')")
  public ApiResponse<List<VersionResponse>> listVersions(@PathVariable Long orderId) {
    return ApiResponse.of(versionQueryService.listForOrder(orderId, currentUserService.requireCurrentUser()));
  }

  /** A-10: BO compares two versions of one order side by side. */
  @GetMapping("/orders/{orderId:\\d+}/versions/compare")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<VersionComparisonResponse> compareVersions(@PathVariable Long orderId,
      @RequestParam("v1") Long leftVersionId, @RequestParam("v2") Long rightVersionId) {
    return ApiResponse.of(versionQueryService.compare(orderId, leftVersionId, rightVersionId,
        currentUserService.requireCurrentUser()));
  }

  /** A-11: BO releases a version that passed internal review to client review. */
  @PostMapping("/versions/{versionId:\\d+}/release")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<VersionResponse> releaseVersion(@PathVariable Long versionId,
      @RequestBody(required = false) ReleaseVersionRequest request) {
    CurrentUser current = currentUserService.requireCurrentUser();
    DesignVersion version = reviewService.release(versionId,
        request == null ? null : request.releaseRemark(), current);
    return ApiResponse.of(versionQueryService.detail(version.getId(), current));
  }

  /** A-12: BO rejects a version and sends it back to the designer for revision. */
  @PostMapping("/versions/{versionId:\\d+}/reject")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<VersionResponse> rejectVersion(@PathVariable Long versionId,
      @Valid @RequestBody RejectVersionRequest request) {
    CurrentUser current = currentUserService.requireCurrentUser();
    DesignVersion version = reviewService.reject(versionId, request.reviewComment(), current);
    return ApiResponse.of(versionQueryService.detail(version.getId(), current));
  }

  /** A-17: DS marks a client-confirmed version as the delivered final version. */
  @PostMapping("/versions/{versionId:\\d+}/mark-final")
  @PreAuthorize("hasRole('DS')")
  public ApiResponse<VersionResponse> markFinal(@PathVariable Long versionId) {
    CurrentUser current = currentUserService.requireCurrentUser();
    DesignVersion version = reviewService.markFinal(versionId, current);
    return ApiResponse.of(versionQueryService.detail(version.getId(), current));
  }

  /** S-07: authorizes the download and redirects to a 5-minute presigned GET URL. */
  @GetMapping("/versions/{versionId:\\d+}/download")
  @PreAuthorize("hasAnyRole('AM','BO','DS')")
  public ResponseEntity<Void> download(@PathVariable Long versionId) {
    String path = versionQueryService.downloadPath(versionId, currentUserService.requireCurrentUser());
    return ResponseEntity.status(HttpStatus.FOUND)
        .header(HttpHeaders.LOCATION, fileStorageService.generatePresignedGetUrl(path).presignedUrl())
        .build();
  }

  public record StartUploadRequest(
      @NotBlank @JsonProperty("file_name") String fileName,
      @Positive @JsonProperty("file_size") long fileSize) {
  }

  public record ConfirmUploadRequest(
      @NotBlank @JsonProperty("upload_session_id") String uploadSessionId,
      @NotBlank @JsonProperty("design_note") String designNote,
      @JsonProperty("change_summary") String changeSummary) {
  }

  public record ReleaseVersionRequest(@JsonProperty("release_remark") String releaseRemark) {
  }

  public record RejectVersionRequest(@NotBlank @JsonProperty("review_comment") String reviewComment) {
  }
}
