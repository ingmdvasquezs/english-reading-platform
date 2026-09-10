package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.VocabularyBreakdown;
import com.soap.soap.domain.model.EditorialLevel;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PedagogicalRecommendationScorerTest {
  private final PedagogicalRecommendationScorer scorer = new PedagogicalRecommendationScorer();

  @Test
  void idealDistributionBeatsVeryEasyAndVeryDifficultTexts() {
    var ideal = score(breakdown(65, 15, 0, 0, 20), 80);
    var easy = score(breakdown(95, 1, 0, 0, 4), 80);
    var difficult = score(breakdown(45, 8, 20, 0, 27), 80);

    assertThat(ideal.score()).isGreaterThan(easy.score()).isGreaterThan(difficult.score());
    assertThat(ideal.accessiblePercentage()).isEqualByComparingTo("80.00");
    assertThat(ideal.challengePercentage()).isEqualByComparingTo("20.00");
  }

  @Test
  void learningReinforcementBeatsSimplyMaximizingKnownWords() {
    var contextualReinforcement = score(breakdown(70, 12, 0, 0, 18), 90);
    var mostlyKnown = score(breakdown(80, 1, 0, 0, 19), 90);

    assertThat(contextualReinforcement.score()).isGreaterThan(mostlyKnown.score());
  }

  @Test
  void extremeDifficultyCannotBeOvercomeByLearningBonus() {
    var frustrating = score(breakdown(45, 8, 20, 0, 27), 90);
    var safer = score(breakdown(78, 4, 0, 0, 18), 90);

    assertThat(safer.score()).isGreaterThan(frustrating.score());
  }

  @Test
  void explicitNewIsRiskierThanTheSameAmountOfUnclassifiedChallenge() {
    var explicitNew = score(breakdown(65, 15, 20, 0, 0), 80);
    var uncertain = score(breakdown(65, 15, 0, 0, 20), 80);

    assertThat(uncertain.score()).isGreaterThan(explicitNew.score());
  }

  @Test
  void lowConfidenceModeratesUncertaintyAndConservativelyFavoursLowerEditorialLevel() {
    var sparse = breakdown(5, 2, 3, 0, 90);
    var a2 = scorer.score(sparse, new BigDecimal("5"), EditorialLevel.A2);
    var c1 = scorer.score(sparse, new BigDecimal("5"), EditorialLevel.C1);

    assertThat(a2.score()).isGreaterThan(c1.score());
    assertThat(a2.unclassifiedPercentage()).isEqualByComparingTo("90.00");
  }

  @Test
  void ignoredWordsDoNotDistortTheRelevantVocabularyDistribution() {
    var result =
        scorer.score(
            new VocabularyBreakdown(120, 65, 15, 0, 20, 20),
            new BigDecimal("80"),
            EditorialLevel.B1);

    assertThat(result.knownPercentage()).isEqualByComparingTo("65.00");
    assertThat(result.learningPercentage()).isEqualByComparingTo("15.00");
    assertThat(result.challengePercentage()).isEqualByComparingTo("20.00");
  }

  @Test
  void handlesZeroRelevantWordsAndRoundingDeterministically() {
    var empty = scorer.score(breakdown(0, 0, 0, 0, 0), BigDecimal.ZERO, EditorialLevel.A1);
    var first =
        scorer.score(
            new VocabularyBreakdown(3, 2, 0, 0, 0, 1),
            new BigDecimal("66.6667"),
            EditorialLevel.B1);
    var second =
        scorer.score(
            new VocabularyBreakdown(3, 2, 0, 0, 0, 1),
            new BigDecimal("66.6667"),
            EditorialLevel.B1);

    assertThat(empty.score()).isZero();
    assertThat(first).isEqualTo(second);
    assertThat(first.knownPercentage()).isEqualByComparingTo("66.67");
    assertThat(first.challengePercentage()).isEqualByComparingTo("33.33");
  }

  @Test
  void acceptsTheAlternativeTargetBoundarySeventyTenTwenty() {
    var boundary = score(breakdown(70, 10, 5, 0, 15), 85);
    var tooEasy = score(breakdown(90, 2, 0, 0, 8), 85);

    assertThat(boundary.score()).isGreaterThan(tooEasy.score());
  }

  private com.soap.soap.application.model.PedagogicalRecommendationScore score(
      VocabularyBreakdown breakdown, int confidence) {
    return scorer.score(breakdown, BigDecimal.valueOf(confidence), EditorialLevel.B1);
  }

  private VocabularyBreakdown breakdown(
      int known, int learning, int explicitNew, int ignored, int unclassified) {
    return new VocabularyBreakdown(
        known + learning + explicitNew + ignored + unclassified,
        known,
        learning,
        explicitNew,
        ignored,
        unclassified);
  }
}
