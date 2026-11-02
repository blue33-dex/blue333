package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.RequirementBaseline;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementBaselineRepository extends JpaRepository<RequirementBaseline, Long> {
  boolean existsByOrderId(Long orderId);

  /** The current baseline is the one with the highest baseline_version. */
  Optional<RequirementBaseline> findFirstByOrderIdOrderByBaselineVersionDesc(Long orderId);
}
