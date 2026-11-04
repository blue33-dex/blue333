package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blue333.ordermgmt.dto.storage.OrphanCleanupResponse;
import com.blue333.ordermgmt.repository.AttachmentRepository;
import com.blue333.ordermgmt.repository.DeliveryArchiveRepository;
import com.blue333.ordermgmt.repository.VersionFileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** P4-T11: unreferenced uploads are quarantined, referenced ones never touched, stale ones purged. */
class OrphanObjectCleanerTest {
  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");

  private FileStorageService fileStorageService;
  private AttachmentRepository attachmentRepository;
  private VersionFileRepository versionFileRepository;
  private DeliveryArchiveRepository deliveryArchiveRepository;
  private OrphanObjectCleaner cleaner;

  @BeforeEach
  void setUp() {
    fileStorageService = mock(FileStorageService.class);
    attachmentRepository = mock(AttachmentRepository.class);
    versionFileRepository = mock(VersionFileRepository.class);
    deliveryArchiveRepository = mock(DeliveryArchiveRepository.class);
    cleaner = new OrphanObjectCleaner(fileStorageService, attachmentRepository, versionFileRepository,
        deliveryArchiveRepository, Clock.fixed(NOW, ZoneOffset.UTC), true, 24, 7);
  }

  @Test
  void quarantinesOldUnreferencedObjectsAndPurgesStaleQuarantine() {
    when(attachmentRepository.findAllStoragePaths())
        .thenReturn(List.of("attachments/order-1/confirmed.pdf"));
    when(versionFileRepository.findAllStoragePaths()).thenReturn(List.of());
    when(deliveryArchiveRepository.findAllReportPaths()).thenReturn(List.of());
    when(fileStorageService.listObjects("attachments/")).thenReturn(List.of(
        object("attachments/order-1/confirmed.pdf", LocalDateTime.of(2026, 10, 1, 0, 0)),
        object("attachments/order-1/orphan.pdf", LocalDateTime.of(2026, 11, 1, 0, 0)),
        object("attachments/order-1/fresh.pdf", LocalDateTime.of(2026, 11, 4, 7, 0))));
    when(fileStorageService.listObjects("versions/")).thenReturn(List.of());
    when(fileStorageService.listObjects("archives/")).thenReturn(List.of());
    when(fileStorageService.listObjects("orphan/")).thenReturn(List.of(object(
        "orphan/attachments/order-1/stale.pdf", LocalDateTime.of(2026, 10, 1, 0, 0))));

    OrphanCleanupResponse response = cleaner.cleanup();

    assertThat(response.quarantinedCount()).isEqualTo(1);
    assertThat(response.purgedCount()).isEqualTo(1);
    assertThat(response.failedCount()).isZero();
    verify(fileStorageService).copyObject("attachments/order-1/orphan.pdf",
        "orphan/attachments/order-1/orphan.pdf");
    verify(fileStorageService).removeObject("attachments/order-1/orphan.pdf");
    verify(fileStorageService).removeObject("orphan/attachments/order-1/stale.pdf");
    verify(fileStorageService, never()).removeObject("attachments/order-1/confirmed.pdf");
    verify(fileStorageService, never()).removeObject("attachments/order-1/fresh.pdf");
  }

  @Test
  void aFailedQuarantineKeepsTheOriginalAndIsReported() {
    when(attachmentRepository.findAllStoragePaths()).thenReturn(List.of());
    when(versionFileRepository.findAllStoragePaths()).thenReturn(List.of());
    when(deliveryArchiveRepository.findAllReportPaths()).thenReturn(List.of());
    when(fileStorageService.listObjects("attachments/")).thenReturn(List.of(
        object("attachments/order-1/orphan.pdf", LocalDateTime.of(2026, 11, 1, 0, 0))));
    when(fileStorageService.listObjects("versions/")).thenReturn(List.of());
    when(fileStorageService.listObjects("archives/")).thenReturn(List.of());
    when(fileStorageService.listObjects("orphan/")).thenReturn(List.of());
    org.mockito.Mockito.doThrow(new FileStorageException("copy failed", null))
        .when(fileStorageService).copyObject(anyString(), anyString());

    OrphanCleanupResponse response = cleaner.cleanup();

    assertThat(response.quarantinedCount()).isZero();
    assertThat(response.failedCount()).isEqualTo(1);
    verify(fileStorageService, never()).removeObject("attachments/order-1/orphan.pdf");
  }

  @Test
  void disabledCleanupDoesNothing() {
    OrphanObjectCleaner disabled = new OrphanObjectCleaner(fileStorageService, attachmentRepository,
        versionFileRepository, deliveryArchiveRepository, Clock.fixed(NOW, ZoneOffset.UTC), false, 24, 7);

    disabled.scheduledCleanup();

    verify(fileStorageService, never()).listObjects(anyString());
  }

  @Test
  void cleanupWithoutStoredObjectsIsANoop() {
    when(attachmentRepository.findAllStoragePaths()).thenReturn(List.of());
    when(versionFileRepository.findAllStoragePaths()).thenReturn(List.of());
    when(deliveryArchiveRepository.findAllReportPaths()).thenReturn(List.of());
    when(fileStorageService.listObjects(anyString())).thenReturn(List.of());

    OrphanCleanupResponse response = cleaner.cleanup();

    assertThat(response.quarantinedCount()).isZero();
    assertThat(response.purgedCount()).isZero();
    assertThat(response.failedCount()).isZero();
    assertThat(response.cleanedAt()).isEqualTo(LocalDateTime.of(2026, 11, 4, 8, 0));
    verify(fileStorageService, never()).copyObject(anyString(), anyString());
    verify(fileStorageService, never()).removeObject(anyString());
  }

  private FileStorageService.StoredObject object(String key, LocalDateTime lastModified) {
    return new FileStorageService.StoredObject(key, 10L, lastModified);
  }
}
