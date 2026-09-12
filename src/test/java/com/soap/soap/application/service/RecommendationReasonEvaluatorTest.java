package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.VocabularyBreakdown;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.RecommendationReasonCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecommendationReasonEvaluatorTest {
  private final RecommendationReasonEvaluator evaluator = new RecommendationReasonEvaluator();

  @Test
  @DisplayName("Regla 1: IN_PROGRESS devuelve CONTINUE_READING con prioridad sobre razones léxicas")
  void inProgressReturnsContinueReadingRegardlessOfLexicalMetrics() {
    var breakdown = new VocabularyBreakdown(100, 90, 5, 0, 0, 5);
    var code =
        evaluator.evaluate(breakdown, new BigDecimal("95.00"), ReadingProgressStatus.IN_PROGRESS);

    assertThat(code).isEqualTo(RecommendationReasonCode.CONTINUE_READING);

    var zeroBreakdown = new VocabularyBreakdown(0, 0, 0, 0, 0, 0);
    assertThat(
            evaluator.evaluate(zeroBreakdown, BigDecimal.ZERO, ReadingProgressStatus.IN_PROGRESS))
        .isEqualTo(RecommendationReasonCode.CONTINUE_READING);
  }

  @Test
  @DisplayName("COMPLETED nunca activa CONTINUE_READING solo por progreso")
  void completedNeverProducesContinueReading() {
    var breakdown = new VocabularyBreakdown(100, 70, 10, 5, 0, 15);
    var code =
        evaluator.evaluate(breakdown, new BigDecimal("85.00"), ReadingProgressStatus.COMPLETED);

    assertThat(code).isNotEqualTo(RecommendationReasonCode.CONTINUE_READING);
    assertThat(code).isEqualTo(RecommendationReasonCode.HIGH_VOCABULARY_MATCH);

    var lowConfidence = new VocabularyBreakdown(100, 70, 0, 0, 0, 30);
    assertThat(
            evaluator.evaluate(
                lowConfidence, new BigDecimal("10.00"), ReadingProgressStatus.COMPLETED))
        .isEqualTo(RecommendationReasonCode.DISCOVERY);
  }

  @Test
  @DisplayName("Lecturas sin vocabulario o sin palabras relevantes devuelven DISCOVERY")
  void zeroOrNoRelevantWordsReturnsDiscovery() {
    var zeroWords = new VocabularyBreakdown(0, 0, 0, 0, 0, 0);
    assertThat(evaluator.evaluate(zeroWords, new BigDecimal("100.00"), null))
        .isEqualTo(RecommendationReasonCode.DISCOVERY);

    var allIgnored = new VocabularyBreakdown(10, 0, 0, 0, 10, 0);
    assertThat(evaluator.evaluate(allIgnored, new BigDecimal("100.00"), null))
        .isEqualTo(RecommendationReasonCode.DISCOVERY);

    assertThat(evaluator.evaluate(null, new BigDecimal("50.00"), null))
        .isEqualTo(RecommendationReasonCode.DISCOVERY);
  }

  @Test
  @DisplayName("Regla 2 y boundaries observables de confianza")
  void observableConfidenceBoundaries() {
    // 100 palabras: 70 known, 10 learning, 20 unclassified
    var breakdown = new VocabularyBreakdown(100, 70, 10, 0, 0, 20);

    // confidence = 0.00 -> DISCOVERY
    assertThat(evaluator.evaluate(breakdown, new BigDecimal("0.00"), null))
        .isEqualTo(RecommendationReasonCode.DISCOVERY);

    // confidence = 19.99 -> DISCOVERY
    assertThat(evaluator.evaluate(breakdown, new BigDecimal("19.99"), null))
        .isEqualTo(RecommendationReasonCode.DISCOVERY);

    // confidence = 20.00 con métricas no clasificables para reglas 3..6 -> DISCOVERY (fallback
    // prudente)
    var lowMetrics = new VocabularyBreakdown(100, 20, 5, 5, 0, 70);
    assertThat(evaluator.evaluate(lowMetrics, new BigDecimal("20.00"), null))
        .isEqualTo(RecommendationReasonCode.DISCOVERY);

    // confidence = 39.99 -> DISCOVERY por falta de confianza para rules 3..6 aunque tenga high
    // known
    assertThat(evaluator.evaluate(breakdown, new BigDecimal("39.99"), null))
        .isEqualTo(RecommendationReasonCode.DISCOVERY);

    // confidence = 40.00 + learning >= 10% -> PRACTICE_VOCABULARY
    var practiceBreakdown = new VocabularyBreakdown(100, 40, 10, 10, 0, 40);
    assertThat(evaluator.evaluate(practiceBreakdown, new BigDecimal("40.00"), null))
        .isEqualTo(RecommendationReasonCode.PRACTICE_VOCABULARY);

    // confidence = 40.00 + challenge 15-30% -> BALANCED_CHALLENGE
    var balancedBreakdown =
        new VocabularyBreakdown(100, 60, 5, 10, 0, 25); // challenge = 35% > 30% -> more challenging
    var balancedBreakdown2 =
        new VocabularyBreakdown(100, 75, 5, 10, 0, 10); // learning=5, challenge=20
    assertThat(evaluator.evaluate(balancedBreakdown2, new BigDecimal("40.00"), null))
        .isEqualTo(RecommendationReasonCode.BALANCED_CHALLENGE);

    // confidence = 50.00 + known >= 65% -> HIGH_VOCABULARY_MATCH
    assertThat(evaluator.evaluate(breakdown, new BigDecimal("50.00"), null))
        .isEqualTo(RecommendationReasonCode.HIGH_VOCABULARY_MATCH);
  }

  @Test
  @DisplayName("Regla 3: HIGH_VOCABULARY_MATCH con confidence >= 50% y known >= 65%")
  void highVocabularyMatchRequiresConfidenceAndKnownThresholds() {
    var highKnown = new VocabularyBreakdown(100, 65, 5, 10, 0, 20);
    assertThat(evaluator.evaluate(highKnown, new BigDecimal("50.00"), null))
        .isEqualTo(RecommendationReasonCode.HIGH_VOCABULARY_MATCH);

    var belowKnownThreshold = new VocabularyBreakdown(100, 64, 5, 15, 0, 16);
    // known = 64% (< 65%), learning = 5% (< 10%), challenge = 31% (> 30%), conf = 50% ->
    // MORE_CHALLENGING
    assertThat(evaluator.evaluate(belowKnownThreshold, new BigDecimal("50.00"), null))
        .isEqualTo(RecommendationReasonCode.MORE_CHALLENGING);
  }

  @Test
  @DisplayName("Regla 4: PRACTICE_VOCABULARY con confidence >= 40% y learning >= 10%")
  void practiceVocabularyRequiresConfidenceAndLearningThresholds() {
    var learningBreakdown = new VocabularyBreakdown(100, 50, 10, 15, 0, 25);
    assertThat(evaluator.evaluate(learningBreakdown, new BigDecimal("40.00"), null))
        .isEqualTo(RecommendationReasonCode.PRACTICE_VOCABULARY);

    // learning = 9% (< 10%), challenge = 40% (> 30%) -> MORE_CHALLENGING
    var belowLearningThreshold = new VocabularyBreakdown(100, 50, 9, 15, 0, 26);
    assertThat(evaluator.evaluate(belowLearningThreshold, new BigDecimal("40.00"), null))
        .isEqualTo(RecommendationReasonCode.MORE_CHALLENGING);
  }

  @Test
  @DisplayName("Regla 5 y 6: BALANCED_CHALLENGE (15-30%) y MORE_CHALLENGING (>30%)")
  void challengeBoundaries() {
    // challenge = 15.00% (learning < 10, known < 65)
    var challenge15 = new VocabularyBreakdown(100, 55, 5, 5, 0, 10); // learning=5, challenge=15
    assertThat(evaluator.evaluate(challenge15, new BigDecimal("40.00"), null))
        .isEqualTo(RecommendationReasonCode.BALANCED_CHALLENGE);

    // challenge = 30.00%
    var challenge30 = new VocabularyBreakdown(100, 55, 5, 10, 0, 20); // learning=5, challenge=30
    assertThat(evaluator.evaluate(challenge30, new BigDecimal("40.00"), null))
        .isEqualTo(RecommendationReasonCode.BALANCED_CHALLENGE);

    // challenge = 31.00% > 30%
    var challenge31 = new VocabularyBreakdown(100, 55, 5, 11, 0, 20); // learning=5, challenge=31
    assertThat(evaluator.evaluate(challenge31, new BigDecimal("40.00"), null))
        .isEqualTo(RecommendationReasonCode.MORE_CHALLENGING);
  }

  @Test
  @DisplayName("Precedencia: HIGH_VOCABULARY_MATCH sobre PRACTICE_VOCABULARY y BALANCED_CHALLENGE")
  void precedenceHighVocabularyMatchOverLearningAndChallenge() {
    // known = 70%, learning = 12%, challenge = 18%, confidence = 80%
    var breakdown = new VocabularyBreakdown(100, 70, 12, 8, 0, 10);
    var code = evaluator.evaluate(breakdown, new BigDecimal("80.00"), null);

    assertThat(code).isEqualTo(RecommendationReasonCode.HIGH_VOCABULARY_MATCH);
  }

  @Test
  @DisplayName("Precedencia: PRACTICE_VOCABULARY sobre BALANCED_CHALLENGE y MORE_CHALLENGING")
  void precedencePracticeVocabularyOverChallenge() {
    // known = 50%, learning = 18%, challenge = 22%, confidence = 80%
    var balanced = new VocabularyBreakdown(100, 50, 18, 10, 0, 12);
    assertThat(evaluator.evaluate(balanced, new BigDecimal("80.00"), null))
        .isEqualTo(RecommendationReasonCode.PRACTICE_VOCABULARY);

    // known = 30%, learning = 15%, challenge = 45%, confidence = 70%
    var moreChallenging = new VocabularyBreakdown(100, 30, 15, 20, 0, 25);
    assertThat(evaluator.evaluate(moreChallenging, new BigDecimal("70.00"), null))
        .isEqualTo(RecommendationReasonCode.PRACTICE_VOCABULARY);
  }

  @Test
  @DisplayName("Ignored words se excluyen del denominador de palabras relevantes")
  void ignoredWordsExcludedFromRelevantDenominator() {
    // total 120 palabras, 20 ignored -> 100 relevant words
    // 65 known sobre 100 relevant = 65%
    var breakdown = new VocabularyBreakdown(120, 65, 5, 10, 20, 20);
    var code = evaluator.evaluate(breakdown, new BigDecimal("50.00"), null);

    assertThat(code).isEqualTo(RecommendationReasonCode.HIGH_VOCABULARY_MATCH);
  }

  @Test
  @DisplayName(
      "Caso real auditado: Fit moderado (~34%) pero baja confianza (<20%) produce DISCOVERY")
  void lowConfidenceWithApparentFitProducesDiscovery() {
    // 100 palabras: 30 known, 4 learning, 66 unclassified -> fit ~34%, confidence ~9%
    var breakdown = new VocabularyBreakdown(100, 30, 4, 0, 0, 66);
    var code = evaluator.evaluate(breakdown, new BigDecimal("9.20"), null);

    assertThat(code).isEqualTo(RecommendationReasonCode.DISCOVERY);
  }
}
