package com.soap.soap.application.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.domain.model.LanguageTag;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LanguageAvailabilityPolicyTest {

  @Test
  void acceptsConfiguredLearningLanguage() {
    var policy = new LanguageAvailabilityPolicy(Set.of("en"), Set.of("en"));

    assertThatCode(() -> policy.requireLearningLanguageEnabled(LanguageTag.of("en")))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsUnavailableLearningLanguageWithMessage() {
    var policy = new LanguageAvailabilityPolicy(Set.of("en"), Set.of("en"));

    assertThatThrownBy(() -> policy.requireLearningLanguageEnabled(LanguageTag.of("fr")))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("fr")
        .hasMessageContaining("not currently supported");
  }

  @Test
  void acceptsConfiguredContentLanguage() {
    var policy = new LanguageAvailabilityPolicy(Set.of("en"), Set.of("en"));

    assertThatCode(() -> policy.requireContentLanguageEnabled(LanguageTag.of("en")))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsUnavailableContentLanguageWithMessage() {
    var policy = new LanguageAvailabilityPolicy(Set.of("en"), Set.of("en"));

    assertThatThrownBy(() -> policy.requireContentLanguageEnabled(LanguageTag.of("pt-BR")))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("pt-BR")
        .hasMessageContaining("not currently supported");
  }

  /**
   * OCP test: enabling a new language is a configuration change, not a code change. Adding "fr" to
   * the enabled set must be sufficient — no code modification needed.
   */
  @Test
  void enablesNewLanguageViaConfigurationAlone() {
    var policy = new LanguageAvailabilityPolicy(Set.of("en", "fr"), Set.of("en", "fr"));

    assertThatCode(() -> policy.requireLearningLanguageEnabled(LanguageTag.of("fr")))
        .doesNotThrowAnyException();
    assertThatCode(() -> policy.requireContentLanguageEnabled(LanguageTag.of("fr")))
        .doesNotThrowAnyException();
  }

  @Test
  void isLearningLanguageEnabledReturnsFalseWhenNotConfigured() {
    var policy = new LanguageAvailabilityPolicy(Set.of("en"), Set.of("en"));

    assertThatCode(
            () -> {
              boolean result = policy.isLearningLanguageEnabled(LanguageTag.of("de"));
              assert !result : "Expected false for 'de'";
            })
        .doesNotThrowAnyException();
  }
}
