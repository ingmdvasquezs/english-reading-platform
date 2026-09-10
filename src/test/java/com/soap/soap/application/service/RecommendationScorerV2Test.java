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

    assertThat(after.finalScore()).isGreaterThanOrEqualTo(before.finalScore());
    assertThat(after.tokenKnownCoverage()).isGreaterThan(before.tokenKnownCoverage());
  }

  @Test
  void replacingUnclassifiedWithUsefulLearningNeverWorsensTheScore() {
    var before = score(evidence(100, 20, 10, 20, 10, 0, 0, 70), EditorialLevel.B1);
    var after = score(evidence(100, 20, 11, 20, 11, 0, 0, 69), EditorialLevel.B1);

    assertThat(after.finalScore()).isGreaterThanOrEqualTo(before.finalScore());
    assertThat(after.learningReinforcement()).isGreaterThan(before.learningReinforcement());
  }

  @Test
  void excessiveExplicitNewEvidenceReducesTheScore() {
    var uncertain = score(evidence(100, 20, 10, 20, 10, 0, 0, 70), EditorialLevel.B1);
    var explicitNew = score(evidence(100, 20, 10, 20, 10, 40, 0, 30), EditorialLevel.B1);

    assertThat(explicitNew.finalScore()).isLessThan(uncertain.finalScore());
    assertThat(explicitNew.explicitNewRisk()).isGreaterThan(uncertain.explicitNewRisk());
  }

  @Test
  void lowEvidenceDependsMoreOnEditorialPrior() {
    var sparse = evidence(100, 3, 2, 3, 2, 0, 0, 95);
    var a1 = score(sparse, EditorialLevel.A1);
    var c2 = score(sparse, EditorialLevel.C2);

    assertThat(a1.finalScore()).isGreaterThan(c2.finalScore());
    assertThat(a1.finalScore()).isCloseTo(a1.editorialPriorScore(), within("1.00"));
    assertThat(c2.finalScore()).isCloseTo(c2.editorialPriorScore(), within("1.00"));
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
