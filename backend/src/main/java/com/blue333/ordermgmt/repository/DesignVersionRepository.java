package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.DesignVersion;
import com.blue333.ordermgmt.domain.enums.VersionStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Restricted repository: versions are never deleted (BR-07), so no delete methods are exposed. */
public interface DesignVersionRepository extends Repository<DesignVersion, Long> {
  DesignVersion save(DesignVersion version);

  Optional<DesignVersion> findById(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from DesignVersion v where v.id = :id")
  Optional<DesignVersion> findByIdForUpdate(@Param("id") Long id);

  @Query("select v.task.id from DesignVersion v where v.id = :id")
  Optional<Long> findTaskIdById(@Param("id") Long id);

  /** Highest numeric part of {@code version_no} ("V12" → 12) within one task; 0 when none exist. */
  @Query(value = "select coalesce(max(cast(substring(version_no, 2) as unsigned)), 0)"
      + " from design_versions where task_id = :taskId", nativeQuery = true)
  Number findMaxVersionNumber(@Param("taskId") Long taskId);

  @Query("select v from DesignVersion v join fetch v.task t join fetch t.materialSpec join fetch v.uploader"
      + " where t.order.id = :orderId order by t.id asc, v.id desc")
  List<DesignVersion> findAllForOrder(@Param("orderId") Long orderId);

  /** Number of versions of the task submitted after {@code versionId}; non-zero means it is stale. */
  @Query("select count(v) from DesignVersion v where v.task.id = :taskId"
      + " and v.submittedAt is not null and v.id > :versionId")
  long countNewerSubmittedVersions(@Param("taskId") Long taskId, @Param("versionId") Long versionId);

  /** A-17: at most one version of an order may be marked as the delivered final version. */
  @Query("select count(v) from DesignVersion v where v.task.order.id = :orderId and v.finalVersion = true")
  long countFinalVersionsInOrder(@Param("orderId") Long orderId);

  /** A-18: the order must hold at least one client-confirmed (or final) version before delivery. */
  @Query("select count(v) from DesignVersion v where v.task.order.id = :orderId and v.status in :statuses")
  long countByOrderIdAndStatusIn(
      @Param("orderId") Long orderId, @Param("statuses") Collection<VersionStatus> statuses);
}
