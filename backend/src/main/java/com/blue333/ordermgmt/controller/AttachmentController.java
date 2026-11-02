package com.blue333.ordermgmt.controller;

import com.blue333.ordermgmt.domain.Attachment;
import com.blue333.ordermgmt.service.AccessControlService;
import com.blue333.ordermgmt.service.CurrentUserService;
import com.blue333.ordermgmt.service.FileStorageService;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {
  private final CurrentUserService currentUserService;
  private final AccessControlService accessControl;
  private final FileStorageService fileStorageService;
  public AttachmentController(CurrentUserService currentUserService, AccessControlService accessControl, FileStorageService fileStorageService) {
    this.currentUserService = currentUserService; this.accessControl = accessControl; this.fileStorageService = fileStorageService;
  }
  @GetMapping("/{attachmentId:\\d+}/download")
  @PreAuthorize("hasAnyRole('AM','BO','DS')")
  public ResponseEntity<Void> download(@PathVariable Long attachmentId) {
    Attachment attachment = accessControl.findVisibleAttachment(attachmentId, currentUserService.requireCurrentUser());
    URI location = URI.create(fileStorageService.generatePresignedGetUrl(attachment.getStoragePath()).presignedUrl());
    return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location.toString()).build();
  }
}
