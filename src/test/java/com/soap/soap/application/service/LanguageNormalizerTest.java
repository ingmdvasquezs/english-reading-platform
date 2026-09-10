package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LanguageNormalizerTest {
  private final LanguageNormalizer normalizer = new LanguageNormalizer();

  @Test
  void canonicalizesSupportedEnglishRegionalTags() {
    assertThat(normalizer.normalize("en")).isEqualTo("en");
    assertThat(normalizer.normalize("EN-us")).isEqualTo("en");
    assertThat(normalizer.normalize("en-GB")).isEqualTo("en");
    assertThat(normalizer.equivalentLanguages("en-US"))
        .containsExactlyInAnyOrder("en", "en-us", "en-gb");
  }

  @Test
  void leavesOtherValidLanguagesNormalizedButDistinct() {
    assertThat(normalizer.normalize(" ES ")).isEqualTo("es");
    assertThat(normalizer.equivalentLanguages("es")).containsExactly("es");
  }
}
