package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.ExceptionRecord;
import com.blue333.ordermgmt.domain.enums.ExceptionStatus;
import com.blue333.ordermgmt.domain.enums.ExceptionType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExceptionRecordRepository extends JpaRepository<ExceptionRecord, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from ExceptionRecord e where e.id = :exceptionId")
  Optional<ExceptionRecord> findByIdForUpdate(Long exceptionId);

  @Query("select e from ExceptionRecord e left join fetch e.task join fetch e.order"
      + " left join fetch e.recorder where e.order.id = :orderId order by e.createdAt asc, e.id asc")
  List<ExceptionRecord> findAllByOrderId(@Param("orderId") Long orderId);

  /** P4-T06: archiving is blocked while any exception of the order is not CLOSED. */
  boolean existsByOrderIdAndStatusNot(Long orderId, ExceptionStatus status);

  long countByOrderIdAndStatusNot(Long orderId, ExceptionStatus status);

  /** P4-T05: one DELAY record per overdue task; repeated scans must not duplicate rows. */
  boolean existsByTaskIdAndExceptionType(Long taskId, ExceptionType exceptionType);

  @Query("select e from ExceptionRecord e join fetch e.order left join fetch e.task"
      + " left join fetch e.recorder where (:status is null or e.status = :status)"
      + " and (:cursor is null or e.id < :cursor) order by e.id desc")
  List<ExceptionRecord> findPage(@Param("status") ExceptionStatus status,
      @Param("cursor") Long cursor, Pageable pageable);
}
