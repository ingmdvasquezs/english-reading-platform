package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.LexicalTranslationCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LexicalTranslationSelectorTest {

  private final LexicalTranslationSelector selector = new LexicalTranslationSelector();

  @Test
  void identifiesSuspiciousTranslations() {
    assertThat(selector.isSuspiciousTranslation("merry", "")).isTrue();
    assertThat(selector.isSuspiciousTranslation("merry", "   ")).isTrue();
    assertThat(selector.isSuspiciousTranslation("merry", null)).isTrue();
    assertThat(selector.isSuspiciousTranslation("merry", "merry")).isTrue();
    assertThat(selector.isSuspiciousTranslation("merry", "Merry")).isTrue();
    assertThat(selector.isSuspiciousTranslation("merry", "  MERRY!  ")).isTrue();
    assertThat(selector.isSuspiciousTranslation("merry", "\"merry\"")).isTrue();

    assertThat(selector.isSuspiciousTranslation("journey", "Viaje")).isFalse();
    assertThat(selector.isSuspiciousTranslation("happy", "feliz")).isFalse();
  }

  @Test
  void selectsCandidateWithContextualStemAndDefinitionMatch() {
    var candidateFeliz =
        new LexicalTranslationCandidate("feliz", "NOUN", 0.61, List.of("happy", "merry", "glad"));
    var candidateAlegre =
        new LexicalTranslationCandidate(
            "alegre", "NOUN", 0.39, List.of("cheerful", "glad", "merry"));

    var resolved =
        selector.selectBestCandidate(
            "merry",
            "adjective",
            "very happy and cheerful feeling or showing joy and happiness",
            "¡Vamos a comer, beber y alegrarnos!",
            List.of(candidateFeliz, candidateAlegre));

    // "alegre" stem matches "alegr" in "alegrarnos" in the example translation
    assertThat(resolved).isEqualTo("alegre");
  }

  @Test
  void selectsHighestConfidenceWhenNoExampleContextProvided() {
    var candidateFeliz =
        new LexicalTranslationCandidate("feliz", "ADJ", 0.61, List.of("happy", "merry", "glad"));
    var candidateAlegre =
        new LexicalTranslationCandidate(
            "alegre", "NOUN", 0.39, List.of("cheerful", "glad", "merry"));

    var resolved =
        selector.selectBestCandidate(
            "merry", "adjective", "feeling happy", null, List.of(candidateFeliz, candidateAlegre));

    assertThat(resolved).isEqualTo("feliz");
  }

  @Test
  void preservesLegitimateIdenticalTermWhenConfirmed() {
    var candidate = new LexicalTranslationCandidate("hotel", "NOUN", 1.0, List.of("hotel"));

    var resolved =
        selector.selectBestCandidate(
            "hotel", "noun", "a building where travelers stay", null, List.of(candidate));

    assertThat(resolved).isEqualTo("hotel");
  }

  @Test
  void returnsEmptyStringWhenCandidatesEmptyOrUnverified() {
    assertThat(selector.selectBestCandidate("foobar", "noun", "nonsense", null, List.of()))
        .isEmpty();
  }

  @Test
  void characterizationOfLeadingTrailingPunctuation() {
    // Initial punctuation
    assertThat(selector.isSuspiciousTranslation("hello", "...hello")).isTrue();
    // Trailing punctuation
    assertThat(selector.isSuspiciousTranslation("hello", "hello???")).isTrue();
    // Both ends punctuation
    assertThat(selector.isSuspiciousTranslation("hello", "«¿hello?!»")).isTrue();
    // Internal punctuation is preserved (not stripped from ends)
    assertThat(selector.isSuspiciousTranslation("hello", "hel-lo")).isFalse();
    // Word without punctuation
    assertThat(selector.isSuspiciousTranslation("hello", "hello")).isTrue();
    // Unicode letters with punctuation
    assertThat(selector.isSuspiciousTranslation("canción", "¡canción!")).isTrue();
    assertThat(selector.isSuspiciousTranslation("über", "«über»")).isTrue();
  }

  @Test
  void characterizationOfCleanWordUnicodeSemantics() {
    // A) Leading and trailing punctuation
    assertThat(selector.cleanWord("!!!hello???")).isEqualTo("hello");

    // B) Inverted punctuation and accents
    assertThat(selector.cleanWord("¿¡canción!?")).isEqualTo("canción");

    // C) Only punctuation
    assertThat(selector.cleanWord("---??!***---")).isEmpty();
    assertThat(selector.cleanWord("   ")).isEmpty();
    assertThat(selector.cleanWord(null)).isEmpty();

    // D) Unicode letters
    assertThat(selector.cleanWord("«привет»")).isEqualTo("привет");
    assertThat(selector.cleanWord("“über”")).isEqualTo("über");
    assertThat(selector.cleanWord("«español»")).isEqualTo("español");

    // E) Unicode numbers across all 3 relevant categories
    // 1. DECIMAL_DIGIT_NUMBER (Nd) - standard [0-9] and Arabic-Indic digit (٤٢)
    assertThat(selector.cleanWord("...123...")).isEqualTo("123");
    assertThat(selector.cleanWord("«٤٢»")).isEqualTo("٤٢");
    // 2. LETTER_NUMBER (Nl) - Roman numeral V (\u2164)
    assertThat(selector.cleanWord("«\u2164»")).isEqualTo("\u2174");
    // 3. OTHER_NUMBER (No) - Superscript 2 (\u00B2) and vulgar fraction 1/2 (\u00BD)
    assertThat(selector.cleanWord("...\u00B2...")).isEqualTo("\u00B2");
    assertThat(selector.cleanWord("«\u00BD»")).isEqualTo("\u00BD");

    // F) Long text surrounded by repeated punctuation
    var longPunctuation = "!@#$%^&*()_+-=[]{}|;':,./<>?".repeat(20);
    assertThat(selector.cleanWord(longPunctuation + "substantialWord" + longPunctuation))
        .isEqualTo("substantialword");

    // G) Internal contractions and apostrophes are preserved
    assertThat(selector.cleanWord("don't")).isEqualTo("don't");
    assertThat(selector.cleanWord("it's")).isEqualTo("it's");
    assertThat(selector.cleanWord("¡don't!")).isEqualTo("don't");
  }
}
