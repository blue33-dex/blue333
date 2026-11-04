package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.blue333.ordermgmt.domain.DesignVersion;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.VersionStatus;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VersionStateMachineTest {
  private static final Map<VersionStatus, VersionStatus> FORWARD = Map.of(
      VersionStatus.DRAFT, VersionStatus.PENDING_INTERNAL_REVIEW,
      VersionStatus.PENDING_INTERNAL_REVIEW, VersionStatus.RELEASED,
      VersionStatus.RELEASED, VersionStatus.IN_CLIENT_REVIEW,
      VersionStatus.IN_CLIENT_REVIEW, VersionStatus.CLIENT_CONFIRMED,
      VersionStatus.CLIENT_CONFIRMED, VersionStatus.FINAL);

  private final VersionStateMachine stateMachine = new VersionStateMachine();

  @Test
  void walksTheFullSixStateLifecycle() {
    DesignVersion version = newDraft();
    for (VersionStatus target : List.of(VersionStatus.PENDING_INTERNAL_REVIEW, VersionStatus.RELEASED,
        VersionStatus.IN_CLIENT_REVIEW, VersionStatus.CLIENT_CONFIRMED, VersionStatus.FINAL)) {
      stateMachine.transition(version, target);
      assertThat(version.getStatus()).isEqualTo(target);
    }
  }

  @Test
  void allowsExactlyTheForwardStepAndNothingElse() {
    for (VersionStatus from : VersionStatus.values()) {
      for (VersionStatus to : VersionStatus.values()) {
        assertThat(stateMachine.canTransition(from, to))
            .as("%s -> %s", from, to)
            .isEqualTo(to == FORWARD.get(from));
      }
    }
  }

  @Test
  void rejectsSkippingStepsAndLeavesStatusUnchanged() {
    DesignVersion version = newDraft();
    for (VersionStatus target : EnumSet.complementOf(EnumSet.of(VersionStatus.PENDING_INTERNAL_REVIEW))) {
      assertThatThrownBy(() -> stateMachine.transition(version, target))
          .isInstanceOf(InvalidStateTransitionException.class);
      assertThat(version.getStatus()).isEqualTo(VersionStatus.DRAFT);
    }
  }

  @Test
  void finalIsTerminal() {
    for (VersionStatus to : VersionStatus.values()) {
      assertThat(stateMachine.canTransition(VersionStatus.FINAL, to)).isFalse();
    }
  }

  private static DesignVersion newDraft() {
    return DesignVersion.draft(mock(MaterialTask.class), "V1", "note", null, mock(User.class), false);
  }
}
