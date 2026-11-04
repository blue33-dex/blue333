package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.domain.enums.MaterialTaskStatus;
import com.blue333.ordermgmt.domain.enums.VersionStatus;
import com.blue333.ordermgmt.repository.DesignVersionRepository;
import com.blue333.ordermgmt.repository.MaterialTaskRepository;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * A-18 / CONFLICT-02 delivery gate shared by "mark delivered" and "archive".
 *
 * <p>The count check is intentionally strict: count(APPROVED) must equal the total task count, so a
 * single CANCELLED (or still reworking) task keeps the whole order out of COMPLETED.</p>
 */
@Component
public class DeliveryRules {
  private static final Set<VersionStatus> CONFIRMED_VERSION_STATUSES =
      Set.of(VersionStatus.CLIENT_CONFIRMED, VersionStatus.FINAL);

  private final MaterialTaskRepository taskRepository;
  private final DesignVersionRepository versionRepository;

  public DeliveryRules(
      MaterialTaskRepository taskRepository, DesignVersionRepository versionRepository) {
    this.taskRepository = taskRepository;
    this.versionRepository = versionRepository;
  }

  public void requireDeliverable(Long orderId) {
    long total = taskRepository.countByOrderId(orderId);
    if (total <= 0) {
      throw new InvalidStateTransitionException(
          "An order must have at least one material task before it can be delivered");
    }
    long approved = taskRepository.countByOrderIdAndStatus(orderId, MaterialTaskStatus.APPROVED);
    if (approved != total) {
      throw new InvalidStateTransitionException(
          "All material tasks must be APPROVED before delivery (" + approved + "/" + total + " approved)");
    }
    if (versionRepository.countByOrderIdAndStatusIn(orderId, CONFIRMED_VERSION_STATUSES) == 0) {
      throw new InvalidStateTransitionException(
          "A client-confirmed version is required before the order can be delivered");
    }
  }
}
