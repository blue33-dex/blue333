package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class AttachmentFilePolicyValidatorTest {
  private final AttachmentFilePolicyValidator validator = new AttachmentFilePolicyValidator();

  @Test
  void acceptsWhitelistedExtensionCaseInsensitivelyAtSizeLimit() {
    assertThatCode(() -> validator.validate("design.PsD", 200L * 1024 * 1024))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsDisallowedExtensionsAndOversizedFiles() {
    assertThatIllegalArgumentException().isThrownBy(() -> validator.validate("design.exe", 1));
    assertThatIllegalArgumentException().isThrownBy(
        () -> validator.validate("design.pdf", 200L * 1024 * 1024 + 1));
  }
}
