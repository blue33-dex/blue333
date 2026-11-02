package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.enums.MaterialTaskStatus;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Enforces the material-task lifecycle, including terminal cancellation and approval states. */
@Component
public class MaterialTaskStateMachine {
  private static final Map<MaterialTaskStatus, Set<MaterialTaskStatus>> TRANSITIONS = transitions();

  public void transition(MaterialTask task, MaterialTaskStatus targetStatus) {
    MaterialTaskStatus currentStatus = task.getStatus();
    if (!TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(targetStatus)) {
      throw new InvalidStateTransitionException(
          "Material task cannot transition from " + currentStatus + " to " + targetStatus);
    }
    task.changeStatus(targetStatus);
  }

  private static Map<MaterialTaskStatus, Set<MaterialTaskStatus>> transitions() {
    Map<MaterialTaskStatus, Set<MaterialTaskStatus>> transitions =
        new EnumMap<>(MaterialTaskStatus.class);
    transitions.put(MaterialTaskStatus.PENDING_DESIGN,
        Set.of(MaterialTaskStatus.PENDING_APPROVAL, MaterialTaskStatus.CANCELLED));
    transitions.put(MaterialTaskStatus.PENDING_APPROVAL,
        Set.of(MaterialTaskStatus.PENDING_REVISE, MaterialTaskStatus.APPROVED,
            MaterialTaskStatus.CANCELLED));
    transitions.put(MaterialTaskStatus.PENDING_REVISE,
        Set.of(MaterialTaskStatus.PENDING_APPROVAL, MaterialTaskStatus.CANCELLED));
    transitions.put(MaterialTaskStatus.APPROVED, Set.of());
    transitions.put(MaterialTaskStatus.CANCELLED, Set.of());
    return Map.copyOf(transitions);
  }
}
