package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.RoleType;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByUsername(String username);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select user from User user where user.username = :username")
  Optional<User> findByUsernameForUpdate(String username);

  /** Enabled users holding {@code roleType}, e.g. BO notification recipients or the DS designer list. */
  @Query("select u from User u where u.enabled = true and exists ("
      + "select 1 from UserRole ur where ur.user = u and ur.role.roleType = :roleType)"
      + " order by u.id asc")
  List<User> findEnabledByRoleType(@Param("roleType") RoleType roleType);

  @Query("select u.id from User u where u.enabled = true and exists ("
      + "select 1 from UserRole ur where ur.user = u and ur.role.roleType = :roleType)"
      + " order by u.id asc")
  List<Long> findEnabledIdsByRoleType(@Param("roleType") RoleType roleType);
}
