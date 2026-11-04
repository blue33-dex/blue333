package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.ClientReview;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Client-review sessions; a session is created once per released version. */
public interface ClientReviewRepository extends Repository<ClientReview, Long> {
  ClientReview save(ClientReview review);

  Optional<ClientReview> findById(Long id);

  Optional<ClientReview> findByVersionId(Long versionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from ClientReview r where r.version.id = :versionId")
  Optional<ClientReview> findByVersionIdForUpdate(@Param("versionId") Long versionId);

  boolean existsByVersionId(Long versionId);

  List<ClientReview> findAllByOrderIdOrderByIdAsc(Long orderId);

  /** Latest session of an order, used by the AM order list to show the current review state. */
  Optional<ClientReview> findFirstByOrderIdOrderByIdDesc(Long orderId);
}
