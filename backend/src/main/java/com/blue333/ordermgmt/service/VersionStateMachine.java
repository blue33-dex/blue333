package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.domain.DesignVersion;
import com.blue333.ordermgmt.domain.enums.VersionStatus;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Enforces the six-state design-version lifecycle (P2-T05):
 * DRAFT → PENDING_INTERNAL_REVIEW → RELEASED → IN_CLIENT_REVIEW → CLIENT_CONFIRMED → FINAL.
 *
 * <p>Version transitions never change the material task by themselves. In particular RELEASED does
 * not approve the task (CONFLICT-01); task approval happens on client confirmation in Phase 3.</p>
 */
@Component
public class VersionStateMachine {
  private static final Map<VersionStatus, Set<VersionStatus>> TRANSITIONS = transitions();

  public void transition(DesignVersion version, VersionStatus targetStatus) {
    VersionStatus currentStatus = version.getStatus();
    if (!canTransition(currentStatus, targetStatus)) {
      throw new InvalidStateTransitionException(
          "Design version cannot transition from " + currentStatus + " to " + targetStatus);
    }
    version.changeStatus(targetStatus);
  }

  public boolean canTransition(VersionStatus from, VersionStatus to) {
    return from != null && to != null && TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
  }

  private static Map<VersionStatus, Set<VersionStatus>> transitions() {
    Map<VersionStatus, Set<VersionStatus>> transitions = new EnumMap<>(VersionStatus.class);
    transitions.put(VersionStatus.DRAFT, Set.of(VersionStatus.PENDING_INTERNAL_REVIEW));
    transitions.put(VersionStatus.PENDING_INTERNAL_REVIEW, Set.of(VersionStatus.RELEASED));
    transitions.put(VersionStatus.RELEASED, Set.of(VersionStatus.IN_CLIENT_REVIEW));
    transitions.put(VersionStatus.IN_CLIENT_REVIEW, Set.of(VersionStatus.CLIENT_CONFIRMED));
    transitions.put(VersionStatus.CLIENT_CONFIRMED, Set.of(VersionStatus.FINAL));
    transitions.put(VersionStatus.FINAL, Set.of());
    return Map.copyOf(transitions);
  }
}
