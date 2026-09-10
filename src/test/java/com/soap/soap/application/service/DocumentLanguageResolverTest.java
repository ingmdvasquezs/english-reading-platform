package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.exception.DocumentImportException.Reason;
import org.junit.jupiter.api.Test;

class DocumentLanguageResolverTest {
  private final DocumentLanguageResolver resolver = new DocumentLanguageResolver();

  @Test
  void overrideWinsAndTagsAreNormalizedWithoutEnglishFallback() {
    assertThat(resolver.resolve("pt_BR", "en")).isEqualTo("pt-BR");
    assertThat(resolver.resolve(null, "fr-ca")).isEqualTo("fr-CA");
  }

  @Test
  void missingOrInvalidLanguageHasAStableReason() {
    assertThatThrownBy(() -> resolver.resolve(null, null))
        .isInstanceOfSatisfying(
            DocumentImportException.class,
            exception -> assertThat(exception.reason()).isEqualTo(Reason.LANGUAGE_REQUIRED));
    assertThatThrownBy(() -> resolver.resolve("not_a_real_language_tag!", null))
        .isInstanceOf(DocumentImportException.class);
  }
}
