package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.blue333.ordermgmt.repository.DesignVersionRepository;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class VersionNoGeneratorTest {
  private final DesignVersionRepository repository = mock(DesignVersionRepository.class);
  private final VersionNoGenerator generator = new VersionNoGenerator(repository);

  @Test
  void firstVersionOfATaskIsV1() {
    when(repository.findMaxVersionNumber(5L)).thenReturn(0L);
    assertThat(generator.next(5L)).isEqualTo("V1");
  }

  @Test
  void nextVersionIsNumericMaximumPlusOneNotLexicographic() {
    // MySQL returns BIGINT UNSIGNED as BigInteger; "V10" must follow "V9", not "V2".
    when(repository.findMaxVersionNumber(5L)).thenReturn(BigInteger.valueOf(9));
    assertThat(generator.next(5L)).isEqualTo("V10");
    assertThat(generator.peekNext(5L)).isEqualTo("V10");
  }

  @Test
  void formatAndParseAreInverse() {
    assertThat(VersionNoGenerator.parse(VersionNoGenerator.format(123))).isEqualTo(123);
    assertThatIllegalArgumentException().isThrownBy(() -> VersionNoGenerator.parse("v1"));
    assertThatIllegalArgumentException().isThrownBy(() -> VersionNoGenerator.parse("V0"));
    assertThatIllegalArgumentException().isThrownBy(() -> VersionNoGenerator.format(0));
  }

  @Test
  void rejectsInvalidTaskId() {
    assertThatIllegalArgumentException().isThrownBy(() -> generator.next(0L));
  }
}
