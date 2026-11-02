package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blue333.ordermgmt.domain.MaterialSpec;
import com.blue333.ordermgmt.domain.MaterialTask;
import com.blue333.ordermgmt.domain.Order;
import com.blue333.ordermgmt.domain.User;
import com.blue333.ordermgmt.domain.enums.MaterialTaskStatus;
import com.blue333.ordermgmt.domain.enums.MaterialType;
import com.blue333.ordermgmt.domain.enums.Priority;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MaterialTaskStateMachineTest {
  private final MaterialTaskStateMachine stateMachine = new MaterialTaskStateMachine();

  @Test
  void permitsConfiguredWorkflowAndCancellationFromNonterminalStates() {
    MaterialTask task = pendingDesignTask();

    stateMachine.transition(task, MaterialTaskStatus.PENDING_APPROVAL);
    stateMachine.transition(task, MaterialTaskStatus.PENDING_REVISE);
    stateMachine.transition(task, MaterialTaskStatus.PENDING_APPROVAL);
    stateMachine.transition(task, MaterialTaskStatus.APPROVED);

    assertThat(task.getStatus()).isEqualTo(MaterialTaskStatus.APPROVED);

    MaterialTask cancellableTask = pendingDesignTask();
    stateMachine.transition(cancellableTask, MaterialTaskStatus.CANCELLED);
    assertThat(cancellableTask.getStatus()).isEqualTo(MaterialTaskStatus.CANCELLED);
  }

  @Test
  void rejectsIllegalAndTerminalTransitions() {
    MaterialTask task = pendingDesignTask();
    assertThatThrownBy(() -> stateMachine.transition(task, MaterialTaskStatus.APPROVED))
        .isInstanceOf(InvalidStateTransitionException.class);

    stateMachine.transition(task, MaterialTaskStatus.CANCELLED);
    assertThatThrownBy(() -> stateMachine.transition(task, MaterialTaskStatus.PENDING_DESIGN))
        .isInstanceOf(InvalidStateTransitionException.class);
  }

  private MaterialTask pendingDesignTask() {
    User user = Mockito.mock(User.class);
    Order order = Order.create("ORD-20250101-001", "Customer", "Store display", LocalDate.now(),
        null, user);
    MaterialSpec materialSpec = MaterialSpec.create(order, MaterialType.POSTER, "A3");
    return MaterialTask.create(order, materialSpec, user, LocalDateTime.now(), Priority.MEDIUM);
  }
}
