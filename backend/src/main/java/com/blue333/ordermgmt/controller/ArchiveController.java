package com.blue333.ordermgmt.controller;

import com.blue333.ordermgmt.dto.ApiResponse;
import com.blue333.ordermgmt.dto.archive.ArchiveResponses.DeliveryArchiveResponse;
import com.blue333.ordermgmt.service.ArchiveService;
import com.blue333.ordermgmt.service.CurrentUserService;
import com.blue333.ordermgmt.service.FileStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Phase 3 delivery archive endpoints: A-19 (archive) and A-28 (PDF export). */
@RestController
@RequestMapping("/api/v1/orders")
public class ArchiveController {
  private final ArchiveService archiveService;
  private final CurrentUserService currentUserService;
  private final FileStorageService fileStorageService;

  public ArchiveController(
      ArchiveService archiveService,
      CurrentUserService currentUserService,
      FileStorageService fileStorageService) {
    this.archiveService = archiveService;
    this.currentUserService = currentUserService;
    this.fileStorageService = fileStorageService;
  }

  /** A-19: archives a COMPLETED order and generates the PDF timeline report. */
  @PostMapping("/{orderId:\\d+}/archive")
  @PreAuthorize("hasRole('BO')")
  public ResponseEntity<ApiResponse<DeliveryArchiveResponse>> archive(@PathVariable Long orderId) {
    DeliveryArchiveResponse archive = archiveService.archiveOrder(
        orderId, currentUserService.requireCurrentUser());
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(archive));
  }

  /** A-28: redirects to a short-lived presigned URL for the stored archive PDF. */
  @GetMapping("/{orderId:\\d+}/archive-report")
  @PreAuthorize("hasRole('BO')")
  public ResponseEntity<Void> archiveReport(@PathVariable Long orderId) {
    String path = archiveService.archiveReportPath(
        orderId, currentUserService.requireCurrentUser());
    return ResponseEntity.status(HttpStatus.FOUND)
        .header(HttpHeaders.LOCATION, fileStorageService.generatePresignedGetUrl(path).presignedUrl())
        .build();
  }
}
