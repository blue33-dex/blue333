package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.MaterialTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialTaskRepository extends JpaRepository<MaterialTask, Long> {
}
