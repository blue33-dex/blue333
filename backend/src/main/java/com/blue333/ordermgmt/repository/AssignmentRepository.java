package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
}
