package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.blue333.ordermgmt.domain.enums.MaterialTaskStatus;
import com.blue333.ordermgmt.repository.DesignVersionRepository;
import com.blue333.ordermgmt.repository.MaterialTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** CONFLICT-02: delivery is blocked unless every task is APPROVED and a version is confirmed. */
class DeliveryRulesTest {
  private static final Long ORDER_ID = 1L;

  private MaterialTaskRepository taskRepository;
  private DesignVersionRepository versionRepository;
  private DeliveryRules rules;

  @BeforeEach
  void setUp() {
    taskRepository = mock(MaterialTaskRepository.class);
    versionRepository = mock(DesignVersionRepository.class);
    rules = new DeliveryRules(taskRepository, versionRepository);
  }

  @Test
  void rejectsOrdersWithoutMaterialTasks() {
    when(taskRepository.countByOrderId(ORDER_ID)).thenReturn(0L);

    assertThatThrownBy(() -> rules.requireDeliverable(ORDER_ID))
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("at least one material task");
  }

  @Test
  void rejectsWhenAnyTaskIsNotApproved() {
    when(taskRepository.countByOrderId(ORDER_ID)).thenReturn(2L);
    when(taskRepository.countByOrderIdAndStatus(ORDER_ID, MaterialTaskStatus.APPROVED)).thenReturn(1L);

    assertThatThrownBy(() -> rules.requireDeliverable(ORDER_ID))
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("2").hasMessageContaining("APPROVED")
        .hasMessageContaining("1/2");
    org.mockito.Mockito.verifyNoInteractions(versionRepository);
  }

  @Test
  void rejectsWithoutAClientConfirmedVersion() {
    when(taskRepository.countByOrderId(ORDER_ID)).thenReturn(1L);
    when(taskRepository.countByOrderIdAndStatus(ORDER_ID, MaterialTaskStatus.APPROVED)).thenReturn(1L);
    when(versionRepository.countByOrderIdAndStatusIn(eq(ORDER_ID), any())).thenReturn(0L);

    assertThatThrownBy(() -> rules.requireDeliverable(ORDER_ID))
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("client-confirmed");
  }

  @Test
  void acceptsFullyApprovedOrdersWithAConfirmedVersion() {
    when(taskRepository.countByOrderId(ORDER_ID)).thenReturn(2L);
    when(taskRepository.countByOrderIdAndStatus(ORDER_ID, MaterialTaskStatus.APPROVED)).thenReturn(2L);
    when(versionRepository.countByOrderIdAndStatusIn(eq(ORDER_ID), any())).thenReturn(1L);

    assertThatCode(() -> rules.requireDeliverable(ORDER_ID)).doesNotThrowAnyException();
  }
}
