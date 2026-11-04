package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.AuditLog;
import com.blue333.ordermgmt.domain.enums.AuditTargetType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Append-only repository: no delete methods are exposed. */
public interface AuditLogRepository extends Repository<AuditLog, Long> {
  <S extends AuditLog> S save(S auditLog);

  Optional<AuditLog> findById(Long id);

  List<AuditLog> findAll();

  /** A-19/A-28: audit entries of one target type, e.g. every operation of an order's tasks. */
  @Query("select a from AuditLog a join fetch a.operator where a.targetType = :targetType"
      + " and a.targetId in :targetIds order by a.operatedAt asc, a.id asc")
  List<AuditLog> findAllForTargets(
      @Param("targetType") AuditTargetType targetType, @Param("targetIds") Collection<Long> targetIds);

  /**
   * A-29: the full order timeline — order-level operations plus everything recorded against the
   * order's tasks, versions, feedback, confirmations and exception records. Ascending ids keep the
   * historical reading order; {@code cursor} is the last id of the previous page.
   */
  @Query("select a from AuditLog a join fetch a.operator where ("
      + " (a.targetType = :orderType and a.targetId = :orderId)"
      + " or (a.targetType = :taskType and a.targetId in"
      + "   (select t.id from MaterialTask t where t.order.id = :orderId))"
      + " or (a.targetType = :versionType and a.targetId in"
      + "   (select v.id from DesignVersion v where v.task.order.id = :orderId))"
      + " or (a.targetType = :feedbackType and a.targetId in"
      + "   (select f.id from FeedbackItem f where f.version.task.order.id = :orderId))"
      + " or (a.targetType = :confirmationType and a.targetId in"
      + "   (select c.id from ConfirmationRecord c where c.order.id = :orderId))"
      + " or (a.targetType = :exceptionType and a.targetId in"
      + "   (select e.id from ExceptionRecord e where e.order.id = :orderId))"
      + ") and (:cursor is null or a.id > :cursor) order by a.id asc")
  List<AuditLog> findForOrderTimeline(@Param("orderId") Long orderId,
      @Param("orderType") AuditTargetType orderType, @Param("taskType") AuditTargetType taskType,
      @Param("versionType") AuditTargetType versionType,
      @Param("feedbackType") AuditTargetType feedbackType,
      @Param("confirmationType") AuditTargetType confirmationType,
      @Param("exceptionType") AuditTargetType exceptionType,
      @Param("cursor") Long cursor, org.springframework.data.domain.Pageable pageable);
}
