package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.exception.DocumentImportException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DocumentLanguagePolicyTest {
  private final DocumentLanguagePolicy policy = new DocumentLanguagePolicy(Set.of("en"));

  @Test
  void acceptsConfiguredPrimaryLanguageAndRegionalVariants() {
    assertThatCode(() -> policy.requireSupported("en-US")).doesNotThrowAnyException();
  }

  @Test
  void rejectsUnsupportedLanguageWithSafeReason() {
    assertThatThrownBy(() -> policy.requireSupported("es"))
        .isInstanceOf(DocumentImportException.class)
        .extracting(value -> ((DocumentImportException) value).reason())
        .isEqualTo(DocumentImportException.Reason.UNSUPPORTED_LANGUAGE);
  }
}
