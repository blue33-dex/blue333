package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.enums.MaterialTaskStatus;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialTaskRepository extends JpaRepository<MaterialTask, Long> {
  boolean existsByMaterialSpecId(Long materialSpecId);

  long countByOrderId(Long orderId);

  /** CONFLICT-02: delivery requires count(APPROVED) == total task count, so a CANCELLED task blocks it. */
  long countByOrderIdAndStatus(Long orderId, MaterialTaskStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select task from MaterialTask task where task.id = :taskId")
  Optional<MaterialTask> findByIdForUpdate(Long taskId);

  /** DS scope: tasks assigned to {@code assigneeId}, descending id; a {@code null} cursor is the first page. */
  @Query("select t from MaterialTask t where t.assignee.id = :assigneeId"
      + " and (:cursor is null or t.id < :cursor) order by t.id desc")
  List<MaterialTask> findPageByAssigneeId(
      @Param("assigneeId") Long assigneeId, @Param("cursor") Long cursor, Pageable pageable);

  List<MaterialTask> findAllByOrderIdOrderByIdAsc(Long orderId);

  /**
   * P4-T05: overdue tasks of in-production orders that still have work ahead. Terminal tasks
   * (APPROVED / CANCELLED) never raise a DELAY exception.
   */
  @Query("select t from MaterialTask t join fetch t.order join fetch t.materialSpec"
      + " where t.taskDeadline < :now and t.status in :statuses and t.order.status = :orderStatus"
      + " order by t.id asc")
  List<MaterialTask> findOverdueTasks(@Param("now") java.time.LocalDateTime now,
      @Param("statuses") java.util.Collection<MaterialTaskStatus> statuses,
      @Param("orderStatus") com.blue333.ordermgmt.domain.enums.OrderStatus orderStatus);

  /** A-25: task totals for the visible kanban orders in one query (no per-card round trips). */
  @Query("select t.order.id, count(t) from MaterialTask t where t.order.id in :orderIds"
      + " group by t.order.id")
  List<Object[]> countGroupedByOrderIds(@Param("orderIds") java.util.Collection<Long> orderIds);
}
