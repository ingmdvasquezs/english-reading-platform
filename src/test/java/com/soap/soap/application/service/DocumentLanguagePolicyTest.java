package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.policy.LanguageAvailabilityPolicy;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DocumentLanguagePolicyTest {

  private static DocumentLanguagePolicy policy(String... enabledContent) {
    var availability =
        new LanguageAvailabilityPolicy(Set.of(enabledContent), Set.of(enabledContent));
    return new DocumentLanguagePolicy(availability);
  }

  @Test
  void acceptsConfiguredPrimaryLanguageAndRegionalVariants() {
    assertThatCode(() -> policy("en").requireSupported("en-US")).doesNotThrowAnyException();
  }

  @Test
  void rejectsUnsupportedLanguageWithSafeReason() {
    assertThatThrownBy(() -> policy("en").requireSupported("es"))
        .isInstanceOf(DocumentImportException.class)
        .extracting(value -> ((DocumentImportException) value).reason())
        .isEqualTo(DocumentImportException.Reason.UNSUPPORTED_LANGUAGE);
  }

  /**
   * OCP test: enabling French content is a configuration change, not a code change. When
   * enabled-content includes "fr", French documents must pass the import language gate.
   */
  @Test
  void enabledFrenchDocumentsPassGateWhenConfigured() {
    assertThatCode(() -> policy("en", "fr").requireSupported("fr")).doesNotThrowAnyException();
  }

  /**
   * OCP test: with only English enabled, French documents are rejected with UNSUPPORTED_LANGUAGE.
   */
  @Test
  void frenchDocumentsRejectedWhenNotConfigured() {
    assertThatThrownBy(() -> policy("en").requireSupported("fr"))
        .isInstanceOf(DocumentImportException.class)
        .extracting(value -> ((DocumentImportException) value).reason())
        .isEqualTo(DocumentImportException.Reason.UNSUPPORTED_LANGUAGE);
  }
}
