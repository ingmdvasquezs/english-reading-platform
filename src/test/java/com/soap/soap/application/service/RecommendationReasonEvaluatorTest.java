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
    var breakdown = new VocabularyBreakdown(100, 90, 5, 0, 0, 5);
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

    // confidence = 50.00 + known >= 90% y challenge <= 15% -> HIGH_VOCABULARY_MATCH
    var highMatch = new VocabularyBreakdown(100, 90, 5, 0, 0, 5);
    assertThat(evaluator.evaluate(highMatch, new BigDecimal("50.00"), null))
        .isEqualTo(RecommendationReasonCode.HIGH_VOCABULARY_MATCH);
  }

  @Test
  @DisplayName(
      "Regla 4: HIGH_VOCABULARY_MATCH con confidence >= 40%, known >= 90% y challenge <= 15%")
  void highVocabularyMatchRequiresConfidenceAndKnownThresholds() {
    var highKnown = new VocabularyBreakdown(100, 90, 5, 0, 0, 5);
    assertThat(evaluator.evaluate(highKnown, new BigDecimal("50.00"), null))
        .isEqualTo(RecommendationReasonCode.HIGH_VOCABULARY_MATCH);

    var belowKnownThreshold = new VocabularyBreakdown(100, 89, 5, 0, 0, 6);
    // known = 89% (< 90%), learning = 5% (< 10%), challenge = 6% (< 15%), conf = 50% ->
    // falls to DISCOVERY
    assertThat(evaluator.evaluate(belowKnownThreshold, new BigDecimal("50.00"), null))
        .isEqualTo(RecommendationReasonCode.DISCOVERY);
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
    // known = 90%, learning = 10%, challenge = 0%, confidence = 80%
    var breakdown = new VocabularyBreakdown(100, 90, 10, 0, 0, 0);
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
    // 90 known sobre 100 relevant = 90%
    var breakdown = new VocabularyBreakdown(120, 90, 5, 5, 20, 0);
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

  @Test
  @DisplayName("V2: Precedencia estricta de 8 niveles en RecommendationReasonEvaluator")
  void v2EightLevelStrictPrecedence() {
    var b100 = new BigDecimal("100.00");
    var b95 = new BigDecimal("95.00");
    var b90 = new BigDecimal("90.00");
    var b50 = new BigDecimal("50.00");
    var b40 = new BigDecimal("40.00");
    var b80 = new BigDecimal("80.00");
    var b70 = new BigDecimal("70.00");
    var b35 = new BigDecimal("35.00");
    var b31 = new BigDecimal("31.00");
    var b25 = new BigDecimal("25.00");
    var b24 = new BigDecimal("24.99");
    var b20 = new BigDecimal("20.00");
    var b15 = new BigDecimal("15.00");
    var b10 = new BigDecimal("10.00");
    var b5 = new BigDecimal("5.00");

    // 1. CONTINUE_READING has absolute highest precedence (even in cold start or 0 confidence)
    assertThat(
            evaluator.evaluate(
                ReadingProgressStatus.IN_PROGRESS,
                true,
                true,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO))
        .isEqualTo(RecommendationReasonCode.CONTINUE_READING);

    // 2. DISCOVERY when global cold start is true
    assertThat(evaluator.evaluate(null, true, false, b100, b95, b15, b5))
        .isEqualTo(RecommendationReasonCode.DISCOVERY);

    // 3. DISCOVERY when insufficient local evidence or local confidence < 25%
    assertThat(evaluator.evaluate(null, false, true, b100, b95, b15, b5))
        .isEqualTo(RecommendationReasonCode.DISCOVERY);
    assertThat(evaluator.evaluate(null, false, false, b24, b95, b15, b5))
        .isEqualTo(RecommendationReasonCode.DISCOVERY);

    // 4. HIGH_VOCABULARY_MATCH: localConfidence >= 40, knownTokenCoverage >= 90, uniqueChallenge <=
    // 15
    // Takes precedence over PRACTICE_VOCABULARY (learning >= 10)
    assertThat(
            evaluator.evaluate(
                null, false, false, b50, b90,
                b15, // learning >= 10, but high match takes precedence!
                b10))
        .isEqualTo(RecommendationReasonCode.HIGH_VOCABULARY_MATCH);

    // 5. PRACTICE_VOCABULARY: localConfidence >= 40, learningUniqueRatio >= 10
    // Takes precedence over BALANCED_CHALLENGE (challenge between 15 and 30)
    assertThat(
            evaluator.evaluate(
                null, false, false, b50, b70, // known < 90
                b10, // learning >= 10
                b20)) // challenge in 15..30
        .isEqualTo(RecommendationReasonCode.PRACTICE_VOCABULARY);

    // 6. BALANCED_CHALLENGE: localConfidence >= 40, 15 <= uniqueChallenge <= 30
    assertThat(
            evaluator.evaluate(
                null, false, false, b40, b70, b5, // learning < 10
                b20)) // challenge in 15..30
        .isEqualTo(RecommendationReasonCode.BALANCED_CHALLENGE);

    // 7. MORE_CHALLENGING: localConfidence >= 40, uniqueChallenge > 30
    assertThat(
            evaluator.evaluate(
                null, false, false, b40, b50, b5, // learning < 10
                b31)) // challenge > 30
        .isEqualTo(RecommendationReasonCode.MORE_CHALLENGING);

    // 8. DISCOVERY fallback: 25 <= localConfidence < 40
    assertThat(
            evaluator.evaluate(
                null, false, false, b35, // 25 <= conf < 40
                b95, b20, b5))
        .isEqualTo(RecommendationReasonCode.DISCOVERY);

    // Fallback when localConfidence >= 40 but no condition matched (e.g. known < 90, learning < 10,
    // challenge < 15)
    assertThat(
            evaluator.evaluate(
                null, false, false, b50, b80, // known < 90
                b5, // learning < 10
                b10)) // challenge < 15
        .isEqualTo(RecommendationReasonCode.DISCOVERY);
  }
}
