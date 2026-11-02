package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.MaterialTask;
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

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select task from MaterialTask task where task.id = :taskId")
  Optional<MaterialTask> findByIdForUpdate(Long taskId);

  /** DS scope: tasks assigned to {@code assigneeId}, descending id; a {@code null} cursor is the first page. */
  @Query("select t from MaterialTask t where t.assignee.id = :assigneeId"
      + " and (:cursor is null or t.id < :cursor) order by t.id desc")
  List<MaterialTask> findPageByAssigneeId(
      @Param("assigneeId") Long assigneeId, @Param("cursor") Long cursor, Pageable pageable);

  List<MaterialTask> findAllByOrderIdOrderByIdAsc(Long orderId);
}
