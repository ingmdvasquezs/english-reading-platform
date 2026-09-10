package com.soap.soap.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.soap.soap.application.model.PedagogicalRecommendationScore;
import com.soap.soap.application.model.RecommendationEvidenceV2;
import com.soap.soap.application.model.RecommendationShadowCandidate;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.service.RecommendationScorerV2;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.ReadingProgressStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class RecommendationShadowObservationTest {

  @Test
  void disabledShadowDoesNotBuildV2Evidence() {
    var meters = new SimpleMeterRegistry();
    var observation =
        new RecommendationShadowObservation(new RecommendationScorerV2(), meters, false);
    var invoked = new AtomicBoolean();

    observation.observe(
        () -> {
          invoked.set(true);
          return List.of();
        });

    assertThat(invoked).isFalse();
    assertThat(meters.counter("recommendation.shadow.requests", "outcome", "disabled").count())
        .isEqualTo(1);
  }

  @Test
  void enabledShadowRecordsComparisonReinforcementEaseChallengeAndBuckets() {
    var meters = new SimpleMeterRegistry();
    var observation =
        new RecommendationShadowObservation(new RecommendationScorerV2(), meters, true);
    var candidates = new ArrayList<RecommendationShadowCandidate>();
    for (var index = 0; index < 11; index++) {
      candidates.add(candidate(index, medium(), new BigDecimal(100 - index)));
    }
    candidates.add(candidate(11, difficult(), new BigDecimal("89")));
    candidates.add(candidate(12, reinforcement(), new BigDecimal("1")));

    observation.observe(() -> candidates);

    assertThat(summary(meters, "recommendation.shadow.top12.overlap")).isEqualTo(11);
    assertThat(summary(meters, "recommendation.shadow.rank.max_movement")).isGreaterThan(0);
    assertThat(summary(meters, "recommendation.shadow.rank.spearman")).isLessThan(1);
    assertThat(summary(meters, "recommendation.shadow.top12.learning.mean", "scorer", "v2"))
        .isGreaterThan(
            summary(meters, "recommendation.shadow.top12.learning.mean", "scorer", "v1"));
    assertThat(meters.find("recommendation.shadow.score").tag("confidence", "50+").summaries())
        .isNotEmpty();
    assertThat(
            meters
                .find("recommendation.shadow.rank.movement")
                .tag("progress", "NOT_STARTED")
                .summaries())
        .isNotEmpty();
    assertThat(meters.get("recommendation.shadow.duration").timer().count()).isEqualTo(1);
  }

  @Test
  void aShadowFailureIsMeasuredAndNeverEscapes() {
    var meters = new SimpleMeterRegistry();
    var observation =
        new RecommendationShadowObservation(new RecommendationScorerV2(), meters, true);

    assertThatCode(
            () ->
                observation.observe(
                    () -> {
                      throw new IllegalStateException("shadow failure");
                    }))
        .doesNotThrowAnyException();
    assertThat(meters.counter("recommendation.shadow.requests", "outcome", "failure").count())
        .isEqualTo(1);
  }

  @Test
  void recordsEveryConfidenceAndProgressBucketWithBoundedTags() {
    var meters = new SimpleMeterRegistry();
    var observation =
        new RecommendationShadowObservation(new RecommendationScorerV2(), meters, true);
    var candidates =
        List.of(
            candidate(1, confidence(3), BigDecimal.ONE, null),
            candidate(2, confidence(7), BigDecimal.ONE, ReadingProgressStatus.IN_PROGRESS),
            candidate(3, confidence(20), BigDecimal.ONE, ReadingProgressStatus.COMPLETED),
            candidate(4, confidence(40), BigDecimal.ONE, null),
            candidate(5, confidence(60), BigDecimal.ONE, null));

    observation.observe(() -> candidates);

    assertThat(meters.find("recommendation.shadow.score").tag("confidence", "0-5").summaries())
        .isNotEmpty();
    assertThat(meters.find("recommendation.shadow.score").tag("confidence", "5-10").summaries())
        .isNotEmpty();
    assertThat(meters.find("recommendation.shadow.score").tag("confidence", "10-25").summaries())
        .isNotEmpty();
    assertThat(meters.find("recommendation.shadow.score").tag("confidence", "25-50").summaries())
        .isNotEmpty();
    assertThat(meters.find("recommendation.shadow.score").tag("confidence", "50+").summaries())
        .isNotEmpty();
    assertThat(
            meters.find("recommendation.shadow.score").tag("progress", "IN_PROGRESS").summaries())
        .isNotEmpty();
    assertThat(meters.find("recommendation.shadow.score").tag("progress", "COMPLETED").summaries())
        .isNotEmpty();
  }

  private RecommendationShadowCandidate candidate(
      int index, RecommendationEvidenceV2 evidence, BigDecimal v1Score) {
    return candidate(index, evidence, v1Score, null);
  }

  private RecommendationShadowCandidate candidate(
      int index,
      RecommendationEvidenceV2 evidence,
      BigDecimal v1Score,
      ReadingProgressStatus progressStatus) {
    var reading =
        new RecommendedPlatformReading(
            new UUID(0, index + 1),
            "Reading " + index,
            "en",
            EditorialLevel.B1,
            "Test",
            LocalDateTime.parse("2026-09-01T00:00:00").plusMinutes(index),
            evidence.uniqueWords(),
            evidence.knownUniqueWords(),
            evidence.learningUniqueWords(),
            evidence.explicitNewUniqueWords(),
            evidence.ignoredUniqueWords(),
            evidence.unclassifiedUniqueWords(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            progressStatus);
    var zero = BigDecimal.ZERO;
    return new RecommendationShadowCandidate(
        reading,
        new PedagogicalRecommendationScore(v1Score, zero, zero, zero, zero, zero, zero),
        evidence);
  }

  private RecommendationEvidenceV2 medium() {
    return new RecommendationEvidenceV2(100, 50, 10, 0, 100, 50, 10, 0, 0, 40);
  }

  private RecommendationEvidenceV2 difficult() {
    return new RecommendationEvidenceV2(100, 5, 0, 0, 100, 5, 0, 0, 0, 95);
  }

  private RecommendationEvidenceV2 reinforcement() {
    return new RecommendationEvidenceV2(100, 75, 15, 0, 100, 60, 15, 5, 0, 20);
  }

  private RecommendationEvidenceV2 confidence(int percentage) {
    return new RecommendationEvidenceV2(
        100, percentage, 0, 0, 100, percentage, 0, 0, 0, 100 - percentage);
  }

  private double summary(SimpleMeterRegistry meters, String name, String... tags) {
    return meters.get(name).tags(tags).summary().totalAmount();
  }
}
