package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.MaterialSpec;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialSpecRepository extends JpaRepository<MaterialSpec, Long> {
  boolean existsByOrderId(Long orderId);

  List<MaterialSpec> findAllByOrderId(Long orderId);
}
