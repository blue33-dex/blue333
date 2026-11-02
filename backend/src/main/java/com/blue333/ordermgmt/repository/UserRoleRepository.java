package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.UserRole;
import com.blue333.ordermgmt.domain.UserRoleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
  List<UserRole> findByIdUserId(Long userId);
}
