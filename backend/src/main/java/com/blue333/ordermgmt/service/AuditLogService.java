package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.domain.AuditLog;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.AuditActionType;
import com.blue333.ordermgmt.domain.enums.AuditTargetType;
import com.blue333.ordermgmt.repository.AuditLogRepository;
import com.blue333.ordermgmt.repository.UserRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists audit entries independently of the business transaction. */
@Service
public class AuditLogService {
  private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogService.class);

  private final AuditLogRepository auditLogRepository;
  private final UserRepository userRepository;

  public AuditLogService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
    this.auditLogRepository = auditLogRepository;
    this.userRepository = userRepository;
  }

  /**
   * Records an audit event on the dedicated executor. Audit persistence failures are contained so
   * they can never affect the calling business operation.
   */
  @Async("auditLogExecutor")
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      AuditActionType action,
      AuditTargetType targetType,
      Long targetId,
      String beforeState,
      String afterState,
      String result,
      String remark) {
    try {
      if (targetId == null || targetId <= 0) {
        LOGGER.warn("Skipping audit action {} because the target ID is invalid", action);
        return;
      }

      Optional<User> operator = currentUsername().flatMap(userRepository::findByUsername);
      if (operator.isEmpty()) {
        LOGGER.warn("Skipping audit action {} because no authenticated operator is available", action);
        return;
      }

      auditLogRepository.save(AuditLog.create(
          operator.get(), action, targetType, targetId, beforeState, afterState, result, remark));
    } catch (RuntimeException exception) {
      LOGGER.error("Unable to persist audit action {} for target {}", action, targetId, exception);
    }
  }

  private Optional<String> currentUsername() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }
    String username = authentication.getName();
    return username == null || username.isBlank() ? Optional.empty() : Optional.of(username);
  }
}
