package com.soap.soap.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LanguageTagTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "en",
        "en-US",
        "en-GB",
        "es",
        "es-CO",
        "pt-BR",
        "fr",
        "de",
        "it",
        "ja",
        "ko",
        "zh-CN",
        "zh-Hans-CN",
        "es-419",
        "sl-rozaj",
        "de-CH-1996",
        "hy-Latn-IT-arevmda",
        "en-US-u-ca-gregory"
      })
  void validTagsAreAcceptedAndSubtagsPreserved(String raw) {
    LanguageTag tag = LanguageTag.of(raw);
    assertThat(tag.value()).isEqualTo(raw);
    assertThat(tag.toString()).isEqualTo(raw);
  }

  @Test
  void explicitlyConfirmEnUsIsNotEqualToEn() {
    LanguageTag enUs = LanguageTag.of("en-US");
    LanguageTag en = LanguageTag.of("en");
    assertThat(enUs).isNotEqualTo(en);
    assertThat(enUs.value()).isEqualTo("en-US");
    assertThat(en.value()).isEqualTo("en");
    assertThat(enUs.value()).isNotEqualTo(en.value());
  }

  @Test
  void caseIsCanonicalizedProperly() {
    LanguageTag tag = LanguageTag.of("EN-us");
    assertThat(tag.value()).isEqualTo("en-US");

    LanguageTag pt = LanguageTag.of("pt-br");
    assertThat(pt.value()).isEqualTo("pt-BR");
  }

  @Test
  void underscoresAreRejectedBecauseBcp47RequiresHyphens() {
    assertThatThrownBy(() -> LanguageTag.of("en_US")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LanguageTag.of("es_CO")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void doesNotCollapseRegionalOrVariantTagsToPrimaryLanguage() {
    LanguageTag tag = LanguageTag.of("en-US");
    assertThat(tag.value()).isEqualTo("en-US");
    assertThat(tag.value()).isNotEqualTo("en");

    LanguageTag brit = LanguageTag.of("en-GB");
    assertThat(brit.value()).isEqualTo("en-GB");
    assertThat(brit.value()).isNotEqualTo("en");
  }

  @Test
  void whitespaceIsTrimmed() {
    LanguageTag tag = LanguageTag.of("  en-US  ");
    assertThat(tag.value()).isEqualTo("en-US");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\t\n"})
  void blankAndEmptyTagsAreRejected(String raw) {
    assertThatThrownBy(() -> LanguageTag.of(raw))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void nullTagIsRejected() {
    assertThatThrownBy(() -> LanguageTag.of(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void tagExceedingMaxLengthIsRejected() {
    String longTag = "a".repeat(51);
    assertThatThrownBy(() -> LanguageTag.of(longTag))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeds maximum length");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "12345",
        "-en",
        "en-",
        "en--US",
        "invalid@lang",
        "verylongsubtagthatexceedseightcharacters"
      })
  void malformedTagsAreRejected(String malformed) {
    assertThatThrownBy(() -> LanguageTag.of(malformed))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void equalsAndHashCodeAreStable() {
    LanguageTag tag1 = LanguageTag.of("en-US");
    LanguageTag tag2 = LanguageTag.of("EN-us");
    LanguageTag tag3 = LanguageTag.of("en");

    assertThat(tag1).isEqualTo(tag2);
    assertThat(tag1.hashCode()).isEqualTo(tag2.hashCode());
    assertThat(tag1).isNotEqualTo(tag3);
  }
}
