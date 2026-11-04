package com.blue333.ordermgmt.controller;

import com.blue333.ordermgmt.dto.ApiResponse;
import com.blue333.ordermgmt.dto.storage.OrphanCleanupResponse;
import com.blue333.ordermgmt.service.CurrentUserService;
import com.blue333.ordermgmt.service.OrphanObjectCleaner;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operational endpoint that runs the orphan sweep on demand (P4-T11); the job also runs nightly. */
@RestController
@RequestMapping("/api/v1/storage")
public class StorageController {
  private final OrphanObjectCleaner orphanObjectCleaner;
  private final CurrentUserService currentUserService;

  public StorageController(OrphanObjectCleaner orphanObjectCleaner, CurrentUserService currentUserService) {
    this.orphanObjectCleaner = orphanObjectCleaner;
    this.currentUserService = currentUserService;
  }

  @PostMapping("/cleanup-orphans")
  @PreAuthorize("hasRole('BO')")
  public ApiResponse<OrphanCleanupResponse> cleanupOrphans() {
    currentUserService.requireCurrentUser();
    return ApiResponse.of(orphanObjectCleaner.cleanup());
  }
}
