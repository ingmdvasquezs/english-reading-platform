package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.RecommendationEvidenceV2;
import com.soap.soap.application.model.RecommendationScoreV2;
import com.soap.soap.domain.model.EditorialLevel;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationScorerV2Test {
  private final RecommendationScorerV2 scorer = new RecommendationScorerV2();

  @Test
  void replacingUnclassifiedWithKnownNeverWorsensTheScore() {
    var before = score(evidence(100, 20, 10, 20, 10, 0, 0, 70), EditorialLevel.B1);
    var after = score(evidence(100, 21, 10, 21, 10, 0, 0, 69), EditorialLevel.B1);

    assertThat(after.personalizedScore()).isGreaterThanOrEqualTo(before.personalizedScore());
    assertThat(after.knownComfort()).isGreaterThan(before.knownComfort());
    assertThat(after.tokenKnownCoverage()).isGreaterThan(before.tokenKnownCoverage());
  }

  @Test
  void replacingUnclassifiedWithUsefulLearningNeverWorsensTheScore() {
    var before = score(evidence(100, 20, 10, 20, 10, 0, 0, 70), EditorialLevel.B1);
    var after = score(evidence(100, 20, 11, 20, 11, 0, 0, 69), EditorialLevel.B1);

    assertThat(after.personalizedScore()).isGreaterThanOrEqualTo(before.personalizedScore());
    assertThat(after.learningReinforcement()).isGreaterThan(before.learningReinforcement());
  }

  @Test
  void excessiveExplicitNewEvidenceReducesTheScore() {
    var uncertain = score(evidence(100, 20, 10, 20, 10, 0, 0, 70), EditorialLevel.B1);
    var explicitNew = score(evidence(100, 20, 10, 20, 10, 40, 0, 30), EditorialLevel.B1);

    assertThat(explicitNew.finalScore()).isLessThan(uncertain.finalScore());
    assertThat(explicitNew.uniqueChallenge()).isGreaterThan(uncertain.uniqueChallenge());
  }

  @Test
  void lowEvidenceDependsMoreOnEditorialPrior() {
    var sparse = evidence(100, 3, 2, 3, 2, 0, 0, 95);
    var a1 = score(sparse, EditorialLevel.A1);
    var c2 = score(sparse, EditorialLevel.C2);

    assertThat(a1.finalScore()).isGreaterThan(c2.finalScore());
    assertThat(a1.finalScore()).isCloseTo(a1.editorialPriorScore(), within("4.00"));
    assertThat(c2.finalScore()).isCloseTo(c2.editorialPriorScore(), within("4.00"));
  }

  @Test
  void highEvidenceDependsMoreOnTheUserAndTokenCoverage() {
    var easyTokens = score(evidence(100, 75, 10, 60, 15, 5, 0, 20), EditorialLevel.B1);
    var hardTokens = score(evidence(100, 25, 10, 60, 15, 5, 0, 20), EditorialLevel.B1);

    assertThat(easyTokens.finalScore()).isGreaterThan(hardTokens.finalScore());
    assertThat(easyTokens.tokenKnownCoverage()).isGreaterThan(hardTokens.tokenKnownCoverage());
  }

  @Test
  void movingLearningToKnownProducesABoundedExplainableChange() {
    var learning = score(evidence(100, 60, 15, 60, 15, 5, 0, 20), EditorialLevel.B1);
    var known = score(evidence(100, 61, 14, 61, 14, 5, 0, 20), EditorialLevel.B1);

    assertThat(known.finalScore().subtract(learning.finalScore()).abs())
        .isLessThan(new java.math.BigDecimal("5"));
    assertThat(known.tokenKnownCoverage()).isGreaterThan(learning.tokenKnownCoverage());
  }

  @Test
  void goodReinforcementRanksNearTheTopForEverySyntheticProfile() {
    for (var profile : SyntheticProfile.values()) {
      var ranked =
          List.of(
                  scenario("too easy", evidence(100, 95, 1, 90, 2, 0, 0, 8), profile.level),
                  scenario("balanced", evidence(100, 80, 5, 65, 10, 5, 0, 20), profile.level),
                  scenario(
                      "good reinforcement", evidence(100, 75, 15, 60, 15, 5, 0, 20), profile.level),
                  scenario("too difficult", evidence(100, 30, 4, 25, 5, 25, 0, 45), profile.level))
              .stream()
              .sorted(Comparator.comparing(Scenario::score).reversed())
              .toList();

      assertThat(ranked.subList(0, 2))
          .as("profile %s", profile)
          .extracting(Scenario::name)
          .contains("good reinforcement");
      assertThat(ranked.getLast().name()).isEqualTo("too difficult");
    }
  }

  @Test
  void knownComfortCalculatesCorrectlyAndSaturatesAtNinetyFivePercent() {
    var c0 = scorer.score(evidence(100, 0, 0, 0, 0, 0, 0, 100), EditorialLevel.B1);
    var c50 = scorer.score(evidence(100, 50, 0, 50, 0, 0, 0, 50), EditorialLevel.B1);
    var c85 = scorer.score(evidence(100, 85, 0, 85, 0, 0, 0, 15), EditorialLevel.B1);
    var c90 = scorer.score(evidence(100, 90, 0, 90, 0, 0, 0, 10), EditorialLevel.B1);
    var c95 = scorer.score(evidence(100, 95, 0, 95, 0, 0, 0, 5), EditorialLevel.B1);
    var c100 = scorer.score(evidence(100, 100, 0, 100, 0, 0, 0, 0), EditorialLevel.B1);

    assertThat(c0.knownComfort()).isEqualByComparingTo("0.00");
    assertThat(c50.knownComfort()).isEqualByComparingTo("52.63");
    assertThat(c85.knownComfort()).isEqualByComparingTo("89.47");
    assertThat(c90.knownComfort()).isEqualByComparingTo("94.74");
    assertThat(c95.knownComfort()).isEqualByComparingTo("100.00");
    assertThat(c100.knownComfort()).isEqualByComparingTo("100.00");
    // Mandatory regression E: 95% KNOWN and 100% KNOWN yield identical KnownComfort = 100
    assertThat(c95.knownComfort()).isEqualTo(c100.knownComfort());
  }

  @Test
  void learningReinforcementCalculatesCorrectlyAndSaturatesAtFifteenPercent() {
    var l0 = scorer.score(evidence(100, 0, 0, 0, 0, 0, 0, 100), EditorialLevel.B1);
    var l5 = scorer.score(evidence(100, 0, 5, 0, 5, 0, 0, 95), EditorialLevel.B1);
    var l10 = scorer.score(evidence(100, 0, 10, 0, 10, 0, 0, 90), EditorialLevel.B1);
    var l15 = scorer.score(evidence(100, 0, 15, 0, 15, 0, 0, 85), EditorialLevel.B1);
    var l20 = scorer.score(evidence(100, 0, 20, 0, 20, 0, 0, 80), EditorialLevel.B1);

    assertThat(l0.learningReinforcement()).isEqualByComparingTo("0.00");
    assertThat(l5.learningReinforcement()).isEqualByComparingTo("33.33");
    assertThat(l10.learningReinforcement()).isEqualByComparingTo("66.67");
    assertThat(l15.learningReinforcement()).isEqualByComparingTo("100.00");
    assertThat(l20.learningReinforcement()).isEqualByComparingTo("100.00");
    assertThat(l15.learningReinforcement()).isEqualTo(l20.learningReinforcement());
  }

  @Test
  void uniqueChallengeAndExcessPenaltyThresholds() {
    // 0% challenge: no penalty
    var p0 = scorer.score(evidence(100, 100, 0, 100, 0, 0, 0, 0), EditorialLevel.B1);
    assertThat(p0.uniqueChallenge()).isEqualByComparingTo("0.00");
    assertThat(p0.excessChallengePenalty()).isEqualByComparingTo("0.00");

    // 15% challenge: no penalty
    var p15 = scorer.score(evidence(100, 85, 0, 85, 0, 15, 0, 0), EditorialLevel.B1);
    assertThat(p15.uniqueChallenge()).isEqualByComparingTo("15.00");
    assertThat(p15.excessChallengePenalty()).isEqualByComparingTo("0.00");

    // Mandatory regression F: 30% challenge has NO excess penalty
    var p30 = scorer.score(evidence(100, 70, 0, 70, 0, 30, 0, 0), EditorialLevel.B1);
    assertThat(p30.uniqueChallenge()).isEqualByComparingTo("30.00");
    assertThat(p30.excessChallengePenalty()).isEqualByComparingTo("0.00");

    // Mandatory regression F: 30.01% challenge has strictly positive excess penalty
    // 3001 explicitNew out of 10000 relevant unique = 30.01%
    var p3001 =
        scorer.score(
            new RecommendationEvidenceV2(10000, 6999, 0, 0, 10000, 6999, 0, 3001, 0, 0),
            EditorialLevel.B1);
    assertThat(p3001.uniqueChallenge()).isEqualByComparingTo("30.01");
    assertThat(p3001.excessChallengePenalty()).isGreaterThan(java.math.BigDecimal.ZERO);
    assertThat(p3001.excessChallengePenalty()).isEqualByComparingTo("0.02");

    // 50% challenge: penalty = (50 - 30) * 1.50 = 30.00
    var p50 = scorer.score(evidence(100, 50, 0, 50, 0, 50, 0, 0), EditorialLevel.B1);
    assertThat(p50.uniqueChallenge()).isEqualByComparingTo("50.00");
    assertThat(p50.excessChallengePenalty()).isEqualByComparingTo("30.00");

    // 100% challenge: penalty = (100 - 30) * 1.50 = 105.00
    var p100 = scorer.score(evidence(100, 0, 0, 0, 0, 100, 0, 0), EditorialLevel.B1);
    assertThat(p100.uniqueChallenge()).isEqualByComparingTo("100.00");
    assertThat(p100.excessChallengePenalty()).isEqualByComparingTo("105.00");
  }

  @Test
  void localConfidenceCalculationAcrossBoundaries() {
    var c0 = scorer.score(evidence(100, 0, 0, 0, 0, 0, 0, 100), EditorialLevel.B1);
    var c20 = scorer.score(evidence(100, 20, 0, 20, 0, 0, 0, 80), EditorialLevel.B1);
    var c25 = scorer.score(evidence(100, 25, 0, 25, 0, 0, 0, 75), EditorialLevel.B1);
    var c40 = scorer.score(evidence(100, 40, 0, 40, 0, 0, 0, 60), EditorialLevel.B1);
    var c50 = scorer.score(evidence(100, 50, 0, 50, 0, 0, 0, 50), EditorialLevel.B1);
    var c100 = scorer.score(evidence(100, 100, 0, 100, 0, 0, 0, 0), EditorialLevel.B1);

    assertThat(c0.classificationConfidence()).isEqualByComparingTo("0.00");
    assertThat(c20.classificationConfidence()).isEqualByComparingTo("20.00");
    assertThat(c25.classificationConfidence()).isEqualByComparingTo("25.00");
    assertThat(c40.classificationConfidence()).isEqualByComparingTo("40.00");
    assertThat(c50.classificationConfidence()).isEqualByComparingTo("50.00");
    assertThat(c100.classificationConfidence()).isEqualByComparingTo("100.00");
  }

  @Test
  void zeroDenominatorsProduceSafeFallbackWithoutNaNOrException() {
    // totalUnique == 0
    var zeroUnique =
        scorer.score(new RecommendationEvidenceV2(0, 0, 0, 0, 0, 0, 0, 0, 0, 0), EditorialLevel.A1);
    assertThat(zeroUnique.insufficientEvidence()).isTrue();
    assertThat(zeroUnique.finalScore()).isEqualByComparingTo("70.0000");
    assertThat(zeroUnique.knownComfort()).isEqualByComparingTo("0.00");
    assertThat(zeroUnique.classificationConfidence()).isEqualByComparingTo("0.00");

    // relevantUnique == 0 (all words ignored)
    var allWordsIgnored =
        scorer.score(
            new RecommendationEvidenceV2(10, 0, 0, 10, 5, 0, 0, 0, 5, 0), EditorialLevel.B2);
    assertThat(allWordsIgnored.insufficientEvidence()).isTrue();
    assertThat(allWordsIgnored.finalScore()).isEqualByComparingTo("58.0000");
    assertThat(allWordsIgnored.knownComfort()).isEqualByComparingTo("0.00");

    // relevantTokens == 0 (all tokens ignored)
    var allTokensIgnored =
        scorer.score(
            new RecommendationEvidenceV2(10, 0, 0, 10, 5, 2, 0, 0, 0, 3), EditorialLevel.C1);
    assertThat(allTokensIgnored.insufficientEvidence()).isTrue();
    assertThat(allTokensIgnored.finalScore()).isEqualByComparingTo("54.0000");
    assertThat(allTokensIgnored.knownComfort()).isEqualByComparingTo("0.00");

    // Null evidence
    var nullEvidence = scorer.score((RecommendationEvidenceV2) null, EditorialLevel.A2);
    assertThat(nullEvidence.insufficientEvidence()).isTrue();
    assertThat(nullEvidence.finalScore()).isEqualByComparingTo("66.0000");
  }

  @Test
  void globalColdStartAlwaysReturnsEditorialPriorRegardlessOfPersonalizedScore() {
    var highFitEvidence = evidence(100, 95, 5, 90, 10, 0, 0, 0);

    for (var level : EditorialLevel.values()) {
      var prior = scorer.editorialPrior(level);
      var coldStartScore = scorer.score(highFitEvidence, level, true);
      assertThat(coldStartScore.finalScore()).isEqualTo(prior.setScale(4));
    }

    var matureScore = scorer.score(highFitEvidence, EditorialLevel.B1, false);
    assertThat(matureScore.finalScore()).isNotEqualTo(scorer.editorialPrior(EditorialLevel.B1));
  }

  @Test
  void readingLexicalEvidenceScoresEquivalently() {
    var readingId = java.util.UUID.randomUUID();
    var lexicalEvidence =
        new com.soap.soap.application.model.ReadingLexicalEvidence(
            readingId, 100, 75, 10, 0, 30, 18, 5, 2, 0, 5);

    var score = scorer.score(lexicalEvidence, EditorialLevel.B1, false);
    assertThat(score.finalScore()).isNotNull();
    assertThat(score.knownComfort()).isEqualByComparingTo("78.95");
    assertThat(score.classificationConfidence()).isEqualByComparingTo("83.33");
  }

  @Test
  void repeatedTokenFrequencyDoesNotInflateClassificationConfidence() {
    // 100 unique words, 1000 tokens
    // 1 KNOWN word appears 500 times, 99 UNCLASSIFIED words appear in the remaining 500 tokens
    var evidence =
        new RecommendationEvidenceV2(
            1000, // totalTokens
            500, // knownTokens
            0, // learningTokens
            0, // ignoredTokens
            100, // uniqueWords
            1, // knownUniqueWords
            0, // learningUniqueWords
            0, // explicitNewUniqueWords
            0, // ignoredUniqueWords
            99 // unclassifiedUniqueWords
            );

    var score = scorer.score(evidence, EditorialLevel.B1, false);

    // Classification confidence is UNIQUE-based: 1 / 100 = 1.00%
    assertThat(score.classificationConfidence()).isEqualByComparingTo("1.00");
    assertThat(score.classificationConfidence()).isNotEqualByComparingTo("50.00");

    // Known token coverage is TOKEN-based: 500 / 1000 = 50.00%
    assertThat(score.tokenKnownCoverage()).isEqualByComparingTo("50.00");
  }

  @Test
  void lowConfidenceInMatureUsesBlendedScoreAndEvaluatesToDiscoveryReason() {
    // Reading with 100 unique words, 5 knownUnique, 95 unclassifiedUnique -> localConfidence =
    // 5.00%
    // 100 tokens, 5 knownTokens -> tokenKnownCoverage = 5.00%
    var evidence =
        new RecommendationEvidenceV2(
            100, // totalTokens
            5, // knownTokens
            0, // learningTokens
            0, // ignoredTokens
            100, // uniqueWords
            5, // knownUniqueWords
            0, // learningUniqueWords
            0, // explicitNewUniqueWords
            0, // ignoredUniqueWords
            95 // unclassifiedUniqueWords
            );

    // Global cold start = false (mature user with >= 30 global classified words)
    var score = scorer.score(evidence, EditorialLevel.B1, false);

    assertThat(score.classificationConfidence()).isEqualByComparingTo("5.00");
    var prior = scorer.editorialPrior(EditorialLevel.B1); // 62.0000

    // FinalScore must NOT be equal to editorialPrior (it's not cold start, and local confidence >
    // 0)
    assertThat(score.finalScore()).isNotEqualTo(prior);

    // Exactly: FinalScore = clamp(0.05 * ScorePersonalized + 0.95 * editorialPrior, 0, 100)
    var expectedBlended =
        new java.math.BigDecimal("0.05")
            .multiply(score.personalizedScore())
            .add(new java.math.BigDecimal("0.95").multiply(prior))
            .setScale(4, java.math.RoundingMode.HALF_UP);
    assertThat(score.finalScore()).isEqualTo(expectedBlended);

    // Reason code evaluator gives DISCOVERY because localConfidence (5.00) < 25.00
    var reasonEvaluator = new RecommendationReasonEvaluator();
    var reason =
        reasonEvaluator.evaluate(
            null,
            false,
            score.insufficientEvidence(),
            score.classificationConfidence(),
            score.tokenKnownCoverage(),
            score.learningUniqueRatio(),
            score.uniqueChallenge());
    assertThat(reason).isEqualTo(com.soap.soap.domain.model.RecommendationReasonCode.DISCOVERY);
  }

  @Test
  void scoreTransitionsContinuouslyAcrossTenPercentConfidenceWithoutDiscontinuity() {
    // 9% localConfidence: 9 known out of 100 unique words, 9 tokens out of 100
    var evidence9 = new RecommendationEvidenceV2(100, 9, 0, 0, 100, 9, 0, 0, 0, 91);
    // 10% localConfidence: 10 known out of 100 unique words, 10 tokens out of 100
    var evidence10 = new RecommendationEvidenceV2(100, 10, 0, 0, 100, 10, 0, 0, 0, 90);

    var score9 = scorer.score(evidence9, EditorialLevel.B1, false);
    var score10 = scorer.score(evidence10, EditorialLevel.B1, false);

    assertThat(score9.classificationConfidence()).isEqualByComparingTo("9.00");
    assertThat(score10.classificationConfidence()).isEqualByComparingTo("10.00");

    // Continuous change: difference between 9% and 10% score should be smooth (< 1.5 score points)
    var diff = score10.finalScore().subtract(score9.finalScore()).abs();
    assertThat(diff).isLessThan(new java.math.BigDecimal("1.5000"));

    // Neither score collapses to static prior
    assertThat(score9.finalScore()).isNotEqualTo(score9.editorialPriorScore());
    assertThat(score10.finalScore()).isNotEqualTo(score10.editorialPriorScore());
  }

  private RecommendationScoreV2 score(RecommendationEvidenceV2 evidence, EditorialLevel level) {
    return scorer.score(evidence, level);
  }

  private RecommendationEvidenceV2 evidence(
      int tokens,
      int knownTokens,
      int learningTokens,
      int knownUnique,
      int learningUnique,
      int explicitNew,
      int ignored,
      int unclassified) {
    return new RecommendationEvidenceV2(
        tokens,
        knownTokens,
        learningTokens,
        0,
        knownUnique + learningUnique + explicitNew + ignored + unclassified,
        knownUnique,
        learningUnique,
        explicitNew,
        ignored,
        unclassified);
  }

  private Scenario scenario(String name, RecommendationEvidenceV2 evidence, EditorialLevel level) {
    return new Scenario(name, scorer.score(evidence, level).finalScore());
  }

  private org.assertj.core.data.Offset<java.math.BigDecimal> within(String value) {
    return org.assertj.core.data.Offset.offset(new java.math.BigDecimal(value));
  }

  private enum SyntheticProfile {
    BEGINNER(EditorialLevel.A1),
    LOW_INTERMEDIATE(EditorialLevel.A2),
    INTERMEDIATE(EditorialLevel.B1),
    ADVANCED(EditorialLevel.C1);

    private final EditorialLevel level;

    SyntheticProfile(EditorialLevel level) {
      this.level = level;
    }
  }

  private record Scenario(String name, java.math.BigDecimal score) {}
}
