package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.domain.DesignVersion;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.VersionFile;
import com.blue333.ordermgmt.dto.version.VersionResponses.VersionComparisonResponse;
import com.blue333.ordermgmt.dto.version.VersionResponses.VersionFileResponse;
import com.blue333.ordermgmt.dto.version.VersionResponses.VersionResponse;
import com.blue333.ordermgmt.repository.DesignVersionRepository;
import com.blue333.ordermgmt.repository.VersionFileRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read models for design versions, assembled inside one read-only transaction. */
@Service
public class VersionQueryService {
  private final AccessControlService accessControl;
  private final DesignVersionRepository versionRepository;
  private final VersionFileRepository fileRepository;

  public VersionQueryService(AccessControlService accessControl, DesignVersionRepository versionRepository,
      VersionFileRepository fileRepository) {
    this.accessControl = accessControl;
    this.versionRepository = versionRepository;
    this.fileRepository = fileRepository;
  }

  /** A-26: version history of an order, grouped by task (ascending) and newest version first. */
  @Transactional(readOnly = true)
  public List<VersionResponse> listForOrder(Long orderId, CurrentUser currentUser) {
    accessControl.findVisibleOrder(orderId, currentUser);
    List<DesignVersion> versions = versionRepository.findAllForOrder(orderId).stream()
        .filter(version -> accessControl.isVersionVisible(version, currentUser))
        .toList();
    if (versions.isEmpty()) {
      return List.of();
    }
    Map<Long, VersionFile> files = fileRepository.findAllByVersionIdIn(
            versions.stream().map(DesignVersion::getId).toList()).stream()
        .collect(Collectors.toMap(file -> file.getVersion().getId(), Function.identity()));
    return versions.stream().map(version -> toResponse(version, files.get(version.getId()))).toList();
  }

  @Transactional(readOnly = true)
  public VersionResponse detail(Long versionId, CurrentUser currentUser) {
    DesignVersion version = accessControl.findVisibleVersion(versionId, currentUser);
    return toResponse(version, fileRepository.findByVersionId(version.getId()).orElse(null));
  }

  /**
   * A-10 (BO-05): side-by-side comparison of two versions of one order. Read-only; requires at
   * least two versions of the order and two different version IDs that both belong to it.
   */
  @Transactional(readOnly = true)
  public VersionComparisonResponse compare(Long orderId, Long leftVersionId, Long rightVersionId,
      CurrentUser currentUser) {
    accessControl.findVisibleOrder(orderId, currentUser);
    Long leftId = requirePositiveVersionId(leftVersionId, "v1");
    Long rightId = requirePositiveVersionId(rightVersionId, "v2");
    if (leftId.equals(rightId)) {
      throw new IllegalArgumentException("Two different versions are required for comparison");
    }
    List<DesignVersion> versions = versionRepository.findAllForOrder(orderId);
    if (versions.size() < 2) {
      throw new InvalidStateTransitionException("At least two versions are required for comparison");
    }
    DesignVersion left = versions.stream().filter(version -> version.getId().equals(leftId))
        .findFirst().orElseThrow(() -> versionNotFound(leftId));
    DesignVersion right = versions.stream().filter(version -> version.getId().equals(rightId))
        .findFirst().orElseThrow(() -> versionNotFound(rightId));
    Map<Long, VersionFile> files = fileRepository.findAllByVersionIdIn(List.of(leftId, rightId)).stream()
        .collect(Collectors.toMap(file -> file.getVersion().getId(), Function.identity()));
    return new VersionComparisonResponse(orderId, toResponse(left, files.get(leftId)),
        toResponse(right, files.get(rightId)),
        !Objects.equals(left.getDesignNote(), right.getDesignNote()),
        !Objects.equals(left.getChangeSummary(), right.getChangeSummary()));
  }

  /** Storage path for S-07; the download rules are applied before the path is read. */
  @Transactional(readOnly = true)
  public String downloadPath(Long versionId, CurrentUser currentUser) {
    DesignVersion version = accessControl.findDownloadableVersion(versionId, currentUser);
    return fileRepository.findByVersionId(version.getId())
        .orElseThrow(() -> new IllegalStateException("The design version has no stored file"))
        .getStoragePath();
  }

  private VersionResponse toResponse(DesignVersion version, VersionFile file) {
    MaterialTask task = version.getTask();
    return new VersionResponse(version.getId(), task.getId(), task.getOrder().getId(),
        task.getMaterialSpec().getMaterialType().name(), task.getMaterialSpec().getSpecSize(),
        version.getVersionNo(), version.getStatus().name(), version.getDesignNote(), version.getChangeSummary(),
        version.getUploader().getId(), version.getUploader().getName(), version.isFinalVersion(),
        version.isOverdue(), file == null ? null : new VersionFileResponse(file.getFileName(), file.getFileSize()),
        version.getSubmittedAt(), version.getCreatedAt());
  }

  private static Long requirePositiveVersionId(Long versionId, String parameterName) {
    if (versionId == null || versionId <= 0) {
      throw new IllegalArgumentException(parameterName + " must be a positive value");
    }
    return versionId;
  }

  private static ResourceNotFoundException versionNotFound(Long versionId) {
    return new ResourceNotFoundException("Design version does not exist: " + versionId);
  }
}
