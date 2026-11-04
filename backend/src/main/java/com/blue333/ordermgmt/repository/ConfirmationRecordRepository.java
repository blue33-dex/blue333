package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.ConfirmationRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Append-only confirmation trail (BR-10): only insert and lookup methods are exposed, and the
 * database triggers reject UPDATE/DELETE on the underlying table.
 */
public interface ConfirmationRecordRepository extends Repository<ConfirmationRecord, Long> {
  ConfirmationRecord save(ConfirmationRecord record);

  Optional<ConfirmationRecord> findById(Long id);

  Optional<ConfirmationRecord> findByVersionId(Long versionId);

  boolean existsByVersionId(Long versionId);

  List<ConfirmationRecord> findAllByOrderIdOrderByIdAsc(Long orderId);
}
