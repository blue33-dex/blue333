package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.RequirementBaseline;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementBaselineRepository extends JpaRepository<RequirementBaseline, Long> {
  boolean existsByOrderId(Long orderId);
}
