package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.Role;
import com.blue333.ordermgmt.domain.enums.RoleType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
  Optional<Role> findByRoleType(RoleType roleType);
}
