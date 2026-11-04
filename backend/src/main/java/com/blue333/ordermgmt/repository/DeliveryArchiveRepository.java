package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.DeliveryArchive;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Archive records; one per order and never modified after creation. */
public interface DeliveryArchiveRepository extends Repository<DeliveryArchive, Long> {
  DeliveryArchive save(DeliveryArchive archive);

  Optional<DeliveryArchive> findById(Long id);

  Optional<DeliveryArchive> findByOrderId(Long orderId);

  boolean existsByOrderId(Long orderId);

  /** P4-T11: archive report object keys, used by the orphan sweep. */
  @Query("select a.archiveReportPath from DeliveryArchive a")
  List<String> findAllReportPaths();
}
