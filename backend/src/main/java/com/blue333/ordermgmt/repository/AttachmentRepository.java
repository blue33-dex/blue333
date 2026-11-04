package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.Attachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
  boolean existsByOrderId(Long orderId);

  long countByOrderId(Long orderId);

  List<Attachment> findAllByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);

  /** P4-T11: referenced object keys, used by the orphan sweep. */
  @Query("select a.storagePath from Attachment a")
  List<String> findAllStoragePaths();
}
