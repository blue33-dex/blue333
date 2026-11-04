package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** E-15 feedback numbering: FB-001, FB-002 ... */
class FeedbackNoGeneratorTest {
  @Test
  void formatsAndParsesFeedbackNumbers() {
    assertThat(FeedbackNoGenerator.format(1)).isEqualTo("FB-001");
    assertThat(FeedbackNoGenerator.format(42)).isEqualTo("FB-042");
    assertThat(FeedbackNoGenerator.format(1000)).isEqualTo("FB-1000");
    assertThat(FeedbackNoGenerator.parse("FB-042")).isEqualTo(42);
  }

  @Test
  void rejectsInvalidNumbers() {
    assertThatIllegalArgumentException().isThrownBy(() -> FeedbackNoGenerator.format(0));
    assertThatIllegalArgumentException().isThrownBy(() -> FeedbackNoGenerator.parse("FB-1a"));
    assertThatIllegalArgumentException().isThrownBy(() -> FeedbackNoGenerator.parse(null));
  }
}
