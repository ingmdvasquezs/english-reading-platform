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
}
