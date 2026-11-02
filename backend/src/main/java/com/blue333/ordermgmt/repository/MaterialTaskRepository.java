package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.MaterialTask;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MaterialTaskRepository extends JpaRepository<MaterialTask, Long> {
  boolean existsByMaterialSpecId(Long materialSpecId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select task from MaterialTask task where task.id = :taskId")
  Optional<MaterialTask> findByIdForUpdate(Long taskId);
}
