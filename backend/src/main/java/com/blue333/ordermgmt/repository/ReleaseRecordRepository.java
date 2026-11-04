package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.ReleaseRecord;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Append-only release history; no update or delete methods are exposed. */
public interface ReleaseRecordRepository extends Repository<ReleaseRecord, Long> {
  ReleaseRecord save(ReleaseRecord releaseRecord);

  Optional<ReleaseRecord> findByVersionId(Long versionId);
}
