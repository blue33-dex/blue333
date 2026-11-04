package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.FeedbackItem;
import com.blue333.ordermgmt.domain.enums.FeedbackStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Feedback rows are edited only through the resolve/expire state transitions, never deleted. */
public interface FeedbackItemRepository extends Repository<FeedbackItem, Long> {
  FeedbackItem save(FeedbackItem item);

  Optional<FeedbackItem> findById(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select f from FeedbackItem f where f.id = :feedbackId")
  Optional<FeedbackItem> findByIdForUpdate(@Param("feedbackId") Long feedbackId);

  List<FeedbackItem> findAllByVersionIdOrderByCreatedAtAscIdAsc(Long versionId);

  long countByVersionIdAndStatus(Long versionId, FeedbackStatus status);

  /** Counts feedback of one status across every version of an order (AM list "open feedback" column). */
  @Query("select count(f) from FeedbackItem f"
      + " where f.version.task.order.id = :orderId and f.status = :status")
  long countByOrderIdAndStatus(@Param("orderId") Long orderId, @Param("status") FeedbackStatus status);

  /** A-25: unresolved feedback totals for the visible kanban orders in one query. */
  @Query("select f.version.task.order.id, count(f) from FeedbackItem f"
      + " where f.version.task.order.id in :orderIds and f.status = :status group by f.version.task.order.id")
  List<Object[]> countGroupedByOrderIds(@Param("orderIds") java.util.Collection<Long> orderIds,
      @Param("status") FeedbackStatus status);

  /** Highest numeric part of {@code feedback_no} ("FB-012" → 12); 0 when none exist. */
  @Query(value = "select coalesce(max(cast(substring(feedback_no, 4) as unsigned)), 0)"
      + " from feedback_items", nativeQuery = true)
  Number findMaxFeedbackNumber();

  /**
   * EN-04 automatic expiry: when one version of an order becomes FINAL, every still-unresolved
   * feedback on an earlier version of the same order is no longer actionable.
   */
  @Modifying
  @Query("update FeedbackItem f set f.status = :expiredStatus, f.updatedAt = :expiredAt"
      + " where f.status = :unresolvedStatus and f.version.id in ("
      + "select v.id from DesignVersion v where v.task.order.id = :orderId and v.id <> :finalVersionId)")
  int expireUnresolvedForOrder(
      @Param("orderId") Long orderId,
      @Param("finalVersionId") Long finalVersionId,
      @Param("unresolvedStatus") FeedbackStatus unresolvedStatus,
      @Param("expiredStatus") FeedbackStatus expiredStatus,
      @Param("expiredAt") LocalDateTime expiredAt);
}
