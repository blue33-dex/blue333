package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.dto.storage.OrphanCleanupResponse;
import com.blue333.ordermgmt.repository.AttachmentRepository;
import com.blue333.ordermgmt.repository.DeliveryArchiveRepository;
import com.blue333.ordermgmt.repository.VersionFileRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * P4-T11 orphan object sweep. Presigned uploads that were never confirmed by the client leave objects
 * without a database row. Instead of deleting them outright, unreferenced objects older than a grace
 * period are moved to the {@code orphan/} quarantine prefix and only purged after the retention
 * window. The check never touches referenced keys, so a live attachment/version/archive is invisible
 * to this sweep even if it is old.
 */
@Service
public class OrphanObjectCleaner {
  private static final Logger LOGGER = LoggerFactory.getLogger(OrphanObjectCleaner.class);
  private static final String QUARANTINE_PREFIX = "orphan/";
  private static final List<String> MANAGED_PREFIXES = List.of("attachments/", "versions/", "archives/");

  private final FileStorageService fileStorageService;
  private final AttachmentRepository attachmentRepository;
  private final VersionFileRepository versionFileRepository;
  private final DeliveryArchiveRepository deliveryArchiveRepository;
  private final Clock clock;
  private final boolean enabled;
  private final long minAgeHours;
  private final long quarantineRetentionDays;

  public OrphanObjectCleaner(
      FileStorageService fileStorageService,
      AttachmentRepository attachmentRepository,
      VersionFileRepository versionFileRepository,
      DeliveryArchiveRepository deliveryArchiveRepository,
      Clock clock,
      @Value("${app.orphan-cleanup.enabled:true}") boolean enabled,
      @Value("${app.orphan-cleanup.min-age-hours:24}") long minAgeHours,
      @Value("${app.orphan-cleanup.quarantine-retention-days:7}") long quarantineRetentionDays) {
    this.fileStorageService = fileStorageService;
    this.attachmentRepository = attachmentRepository;
    this.versionFileRepository = versionFileRepository;
    this.deliveryArchiveRepository = deliveryArchiveRepository;
    this.clock = clock;
    this.enabled = enabled;
    this.minAgeHours = minAgeHours;
    this.quarantineRetentionDays = quarantineRetentionDays;
  }

  @Scheduled(cron = "${app.orphan-cleanup.cron:0 0 3 * * ?}")
  public void scheduledCleanup() {
    if (!enabled) {
      return;
    }
    try {
      cleanup();
    } catch (RuntimeException exception) {
      LOGGER.error("Orphan object cleanup failed", exception);
    }
  }

  /** Runs the sweep once and reports what happened; also exposed as a BO operational trigger. */
  public OrphanCleanupResponse cleanup() {
    Set<String> referenced = referencedObjectKeys();
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime orphanCutoff = now.minusHours(minAgeHours);
    LocalDateTime quarantineCutoff = now.minusDays(quarantineRetentionDays);

    int quarantined = 0;
    int failed = 0;
    for (String prefix : MANAGED_PREFIXES) {
      for (FileStorageService.StoredObject object : fileStorageService.listObjects(prefix)) {
        if (referenced.contains(object.key()) || object.lastModified() == null
            || object.lastModified().isAfter(orphanCutoff)) {
          continue;
        }
        try {
          fileStorageService.copyObject(object.key(), QUARANTINE_PREFIX + object.key());
          fileStorageService.removeObject(object.key());
          quarantined++;
        } catch (RuntimeException exception) {
          failed++;
          LOGGER.warn("Could not quarantine orphan object {}", object.key(), exception);
        }
      }
    }

    int purged = 0;
    for (FileStorageService.StoredObject object : fileStorageService.listObjects(QUARANTINE_PREFIX)) {
      if (object.lastModified() == null || object.lastModified().isAfter(quarantineCutoff)) {
        continue;
      }
      try {
        fileStorageService.removeObject(object.key());
        purged++;
      } catch (RuntimeException exception) {
        failed++;
        LOGGER.warn("Could not purge quarantined object {}", object.key(), exception);
      }
    }
    if (quarantined > 0 || purged > 0 || failed > 0) {
      LOGGER.info("Orphan cleanup: quarantined={}, purged={}, failed={}", quarantined, purged, failed);
    }
    return new OrphanCleanupResponse(now, quarantined, purged, failed);
  }

  private Set<String> referencedObjectKeys() {
    Set<String> keys = new HashSet<>();
    keys.addAll(attachmentRepository.findAllStoragePaths());
    keys.addAll(versionFileRepository.findAllStoragePaths());
    keys.addAll(deliveryArchiveRepository.findAllReportPaths());
    return keys;
  }
}
