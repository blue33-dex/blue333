package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.ReviewRecord;
import java.util.List;
import org.springframework.data.repository.Repository;

/** Append-only review history; no update or delete methods are exposed. */
public interface ReviewRecordRepository extends Repository<ReviewRecord, Long> {
  ReviewRecord save(ReviewRecord reviewRecord);

  List<ReviewRecord> findAllByVersionIdOrderByIdAsc(Long versionId);
}
