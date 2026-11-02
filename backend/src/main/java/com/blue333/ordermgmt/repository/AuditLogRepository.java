package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.AuditLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Append-only repository: no delete methods are exposed. */
public interface AuditLogRepository extends Repository<AuditLog, Long> {
  <S extends AuditLog> S save(S auditLog);

  Optional<AuditLog> findById(Long id);

  List<AuditLog> findAll();
}
