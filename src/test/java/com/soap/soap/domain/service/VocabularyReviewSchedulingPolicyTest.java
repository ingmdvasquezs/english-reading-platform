package com.soap.soap.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.soap.soap.application.service.RatingOptionsBuilder;
import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.model.SrsCalculationResult;
import com.soap.soap.domain.model.SrsItemParameters;
import com.soap.soap.domain.model.SrsState;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VocabularyReviewSchedulingPolicyTest {

  private FsrsScheduler scheduler;
  private VocabularyReviewSchedulingPolicy policy;
  private RatingOptionsBuilder ratingOptionsBuilder;
  private LocalDateTime nowUtc;

  @BeforeEach
  void setUp() {
    scheduler = new FsrsScheduler();
    policy = new VocabularyReviewSchedulingPolicy(scheduler);
    ratingOptionsBuilder = new RatingOptionsBuilder(policy);
    nowUtc = LocalDateTime.of(2026, 9, 20, 12, 0, 0);
  }

  @Test
  @DisplayName("Vector A: NEW card preview has only AGAIN (10m) and GOOD (1d), NO HARD, NO EASY")
  void vectorA_newCardPreviewOptions() {
    var user = new User(UUID.randomUUID(), "User", "user@example.com");
    var word = new Word(UUID.randomUUID(), "test", "en");
    var newCard =
        new UserVocabulary(UUID.randomUUID(), user, word, VocabularyStatus.NEW, nowUtc, null);

    var options = ratingOptionsBuilder.buildRatingOptions(newCard, nowUtc);
    assertThat(options).hasSize(2);

    var againOpt = options.get(0);
    assertThat(againOpt.rating()).isEqualTo(ReviewRating.AGAIN);
    assertThat(againOpt.intervalSeconds()).isEqualTo(600L);
    assertThat(againOpt.nextReviewAt()).isEqualTo(nowUtc.plusMinutes(10));

    var goodOpt = options.get(1);
    assertThat(goodOpt.rating()).isEqualTo(ReviewRating.GOOD);
    assertThat(goodOpt.intervalSeconds()).isEqualTo(86400L);
    assertThat(goodOpt.nextReviewAt()).isEqualTo(nowUtc.plusDays(1));
  }

  @Test
  @DisplayName(
      "Vector B: NEW card + GOOD graduates to REVIEW with 1d interval, preserving canonical FSRS memory")
  void vectorB_newCardGood() {
    var params = SrsItemParameters.defaultForNew();

    SrsCalculationResult result = policy.calculateNextState(params, ReviewRating.GOOD, nowUtc);

    assertThat(result.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(result.vocabularyStatus()).isEqualTo(VocabularyStatus.NEW);
    assertThat(result.intervalSeconds()).isEqualTo(86400L);
    assertThat(result.nextReviewAt()).isEqualTo(nowUtc.plusDays(1));
    assertThat(result.repetitions()).isEqualTo(1);
    assertThat(result.lapses()).isEqualTo(0);
    // Canonical FSRS initial stability and difficulty for GOOD
    assertThat(result.stability()).isCloseTo(3.7145, within(1e-4));
    assertThat(result.difficulty()).isCloseTo(5.1618, within(1e-4));
  }

  @Test
  @DisplayName("Vector C: NEW card + AGAIN transitions to LEARNING with 10m interval")
  void vectorC_newCardAgain() {
    var params = SrsItemParameters.defaultForNew();

    SrsCalculationResult result = policy.calculateNextState(params, ReviewRating.AGAIN, nowUtc);

    assertThat(result.srsState()).isEqualTo(SrsState.LEARNING);
    assertThat(result.vocabularyStatus()).isEqualTo(VocabularyStatus.NEW);
    assertThat(result.intervalSeconds()).isEqualTo(600L);
    assertThat(result.nextReviewAt()).isEqualTo(nowUtc.plusMinutes(10));
    assertThat(result.repetitions()).isEqualTo(1);
    assertThat(result.lapses()).isEqualTo(0);
    // Canonical initial stability and difficulty for AGAIN
    assertThat(result.stability()).isCloseTo(0.4872, within(1e-4));
    assertThat(result.difficulty()).isCloseTo(7.6214, within(1e-4));
  }

  @Test
  @DisplayName("Vector D: LEARNING card preview has only AGAIN (10m) and GOOD (1d)")
  void vectorD_learningCardPreviewOptions() {
    var user = new User(UUID.randomUUID(), "User", "user@example.com");
    var word = new Word(UUID.randomUUID(), "test", "en");
    var learningCard =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusMinutes(10),
            null,
            600L,
            1,
            nowUtc.minusMinutes(10),
            nowUtc,
            SrsState.LEARNING,
            0.4872,
            7.6214,
            1,
            0);

    var options = ratingOptionsBuilder.buildRatingOptions(learningCard, nowUtc);
    assertThat(options).hasSize(2);

    assertThat(options.get(0).rating()).isEqualTo(ReviewRating.AGAIN);
    assertThat(options.get(0).intervalSeconds()).isEqualTo(600L);
    assertThat(options.get(0).nextReviewAt()).isEqualTo(nowUtc.plusMinutes(10));

    assertThat(options.get(1).rating()).isEqualTo(ReviewRating.GOOD);
    assertThat(options.get(1).intervalSeconds()).isEqualTo(86400L);
    assertThat(options.get(1).nextReviewAt()).isEqualTo(nowUtc.plusDays(1));
  }

  @Test
  @DisplayName("Vector E: LEARNING card + GOOD graduates to REVIEW with 1d interval")
  void vectorE_learningCardGood() {
    var params =
        new SrsItemParameters(
            SrsState.LEARNING,
            VocabularyStatus.LEARNING,
            0.4872,
            7.6214,
            1,
            0,
            nowUtc.minusMinutes(10),
            nowUtc);

    SrsCalculationResult result = policy.calculateNextState(params, ReviewRating.GOOD, nowUtc);

    assertThat(result.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(result.vocabularyStatus()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(result.intervalSeconds()).isEqualTo(86400L);
    assertThat(result.nextReviewAt()).isEqualTo(nowUtc.plusDays(1));
    assertThat(result.repetitions()).isEqualTo(2);
    assertThat(result.lapses()).isEqualTo(0);
    // Stability and difficulty updated canonical FSRS
    assertThat(result.stability()).isGreaterThan(0.4872);
  }

  @Test
  @DisplayName("Vector F: Mature REVIEW card + GOOD uses dynamic FSRS interval (> 1d)")
  void vectorF_matureReviewCardGood() {
    // Mature card reviewed 12 days ago with stability 12.0
    var params =
        new SrsItemParameters(
            SrsState.REVIEW, VocabularyStatus.KNOWN, 12.0, 4.5, 5, 0, nowUtc.minusDays(12), nowUtc);

    SrsCalculationResult result = policy.calculateNextState(params, ReviewRating.GOOD, nowUtc);

    assertThat(result.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(result.vocabularyStatus()).isEqualTo(VocabularyStatus.KNOWN);
    // Must NOT be forced to 86400L (1d). Must be dynamic canonical FSRS interval > 1d.
    assertThat(result.intervalSeconds()).isGreaterThan(86400L);
    assertThat(result.nextReviewAt()).isAfter(nowUtc.plusDays(1));
    assertThat(result.repetitions()).isEqualTo(6);
    assertThat(result.stability()).isGreaterThan(12.0);
  }

  @Test
  @DisplayName(
      "Vector G: Mature REVIEW card + AGAIN transitions to RELEARNING with 10m step, incrementing lapses and resetting stability")
  void vectorG_matureReviewCardAgain() {
    var params =
        new SrsItemParameters(
            SrsState.REVIEW, VocabularyStatus.KNOWN, 12.0, 4.5, 5, 0, nowUtc.minusDays(12), nowUtc);

    SrsCalculationResult result = policy.calculateNextState(params, ReviewRating.AGAIN, nowUtc);

    assertThat(result.srsState()).isEqualTo(SrsState.RELEARNING);
    assertThat(result.vocabularyStatus()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(result.intervalSeconds()).isEqualTo(600L);
    assertThat(result.nextReviewAt()).isEqualTo(nowUtc.plusMinutes(10));
    assertThat(result.repetitions()).isEqualTo(6);
    assertThat(result.lapses()).isEqualTo(1);
    // Stability reduced by lapse formula
    assertThat(result.stability()).isLessThan(12.0);
  }

  @Test
  @DisplayName("Vector H: RELEARNING card preview has only AGAIN (10m) and GOOD (1d)")
  void vectorH_relearningCardPreviewOptions() {
    var user = new User(UUID.randomUUID(), "User", "user@example.com");
    var word = new Word(UUID.randomUUID(), "test", "en");
    var relearningCard =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusMinutes(10),
            null,
            600L,
            3,
            nowUtc.minusMinutes(10),
            nowUtc,
            SrsState.RELEARNING,
            1.5,
            5.5,
            6,
            1);

    var options = ratingOptionsBuilder.buildRatingOptions(relearningCard, nowUtc);
    assertThat(options).hasSize(2);

    assertThat(options.get(0).rating()).isEqualTo(ReviewRating.AGAIN);
    assertThat(options.get(0).intervalSeconds()).isEqualTo(600L);
    assertThat(options.get(0).nextReviewAt()).isEqualTo(nowUtc.plusMinutes(10));

    assertThat(options.get(1).rating()).isEqualTo(ReviewRating.GOOD);
    assertThat(options.get(1).intervalSeconds()).isEqualTo(86400L);
    assertThat(options.get(1).nextReviewAt()).isEqualTo(nowUtc.plusDays(1));
  }

  @Test
  @DisplayName("Vector I: RELEARNING card + GOOD recovers to REVIEW with 1d interval")
  void vectorI_relearningCardGood() {
    var params =
        new SrsItemParameters(
            SrsState.RELEARNING,
            VocabularyStatus.LEARNING,
            1.5,
            5.5,
            6,
            1,
            nowUtc.minusMinutes(10),
            nowUtc);

    SrsCalculationResult result = policy.calculateNextState(params, ReviewRating.GOOD, nowUtc);

    assertThat(result.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(result.vocabularyStatus()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(result.intervalSeconds()).isEqualTo(86400L);
    assertThat(result.nextReviewAt()).isEqualTo(nowUtc.plusDays(1));
    assertThat(result.repetitions()).isEqualTo(7);
    assertThat(result.lapses()).isEqualTo(1);
  }

  @Test
  @DisplayName("Vector J: Next-day REVIEW after recovery is dynamic FSRS, NOT forced to 1d")
  void vectorJ_nextDayReviewAfterRecoveryIsDynamic() {
    // Card recovered yesterday via Vector I (graduated to REVIEW with 1d interval)
    // 1 day has elapsed, card is now due in REVIEW state:
    var params =
        new SrsItemParameters(
            SrsState.REVIEW, VocabularyStatus.KNOWN, 2.5, 5.5, 7, 1, nowUtc.minusDays(1), nowUtc);

    SrsCalculationResult result = policy.calculateNextState(params, ReviewRating.GOOD, nowUtc);

    assertThat(result.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(result.vocabularyStatus()).isEqualTo(VocabularyStatus.KNOWN);
    // Must be dynamic FSRS (> 1d, e.g. 2+ days), NOT forced to 1d
    assertThat(result.intervalSeconds()).isGreaterThan(86400L);
    assertThat(result.nextReviewAt()).isAfter(nowUtc.plusDays(1));
    assertThat(result.repetitions()).isEqualTo(8);
  }

  @Test
  @DisplayName(
      "Vector K: Two mature cards with different stabilities produce distinct dynamic intervals")
  void vectorK_differentStabilitiesProduceDistinctDynamicIntervals() {
    var lowStabilityParams =
        new SrsItemParameters(
            SrsState.REVIEW, VocabularyStatus.KNOWN, 5.0, 5.0, 4, 0, nowUtc.minusDays(5), nowUtc);

    var highStabilityParams =
        new SrsItemParameters(
            SrsState.REVIEW, VocabularyStatus.KNOWN, 25.0, 5.0, 4, 0, nowUtc.minusDays(25), nowUtc);

    SrsCalculationResult lowResult =
        policy.calculateNextState(lowStabilityParams, ReviewRating.GOOD, nowUtc);
    SrsCalculationResult highResult =
        policy.calculateNextState(highStabilityParams, ReviewRating.GOOD, nowUtc);

    assertThat(lowResult.intervalSeconds()).isGreaterThan(86400L);
    assertThat(highResult.intervalSeconds()).isGreaterThan(lowResult.intervalSeconds());
    assertThat(highResult.nextReviewAt()).isAfter(lowResult.nextReviewAt());
  }

  @Test
  @DisplayName("Vector L: Consecutive successful reviews expand interval into weeks and months")
  void vectorL_consecutiveSuccessfulReviewsExpandIntervals() {
    // Start with a new card rated GOOD (graduates to REVIEW at 1d)
    var p = SrsItemParameters.defaultForNew();

    SrsCalculationResult r1 = policy.calculateNextState(p, ReviewRating.GOOD, nowUtc);
    assertThat(r1.intervalSeconds()).isEqualTo(86400L); // 1 day

    // Review 2 after 1 day
    var t2 = r1.nextReviewAt();
    var p2 =
        new SrsItemParameters(
            r1.srsState(),
            r1.vocabularyStatus(),
            r1.stability(),
            r1.difficulty(),
            r1.repetitions(),
            r1.lapses(),
            nowUtc,
            t2);
    SrsCalculationResult r2 = policy.calculateNextState(p2, ReviewRating.GOOD, t2);
    assertThat(r2.intervalSeconds()).isGreaterThan(86400L);

    // Review 3 after r2 interval
    var t3 = r2.nextReviewAt();
    var p3 =
        new SrsItemParameters(
            r2.srsState(),
            r2.vocabularyStatus(),
            r2.stability(),
            r2.difficulty(),
            r2.repetitions(),
            r2.lapses(),
            t2,
            t3);
    SrsCalculationResult r3 = policy.calculateNextState(p3, ReviewRating.GOOD, t3);
    assertThat(r3.intervalSeconds()).isGreaterThan(r2.intervalSeconds());

    // Review 4 after r3 interval
    var t4 = r3.nextReviewAt();
    var p4 =
        new SrsItemParameters(
            r3.srsState(),
            r3.vocabularyStatus(),
            r3.stability(),
            r3.difficulty(),
            r3.repetitions(),
            r3.lapses(),
            t3,
            t4);
    SrsCalculationResult r4 = policy.calculateNextState(p4, ReviewRating.GOOD, t4);
    assertThat(r4.intervalSeconds()).isGreaterThan(r3.intervalSeconds());

    // By review 4/5, interval reaches weeks/months (e.g. > 14 days)
    assertThat(r4.intervalSeconds()).isGreaterThan(14 * 86400L);
  }

  @Test
  @DisplayName("Vector M: AGAIN preserves memory context, increments lapses, updates stability")
  void vectorM_againPreservesMemoryContext() {
    var matureParams =
        new SrsItemParameters(
            SrsState.REVIEW, VocabularyStatus.KNOWN, 15.0, 4.0, 8, 2, nowUtc.minusDays(15), nowUtc);

    SrsCalculationResult againResult =
        policy.calculateNextState(matureParams, ReviewRating.AGAIN, nowUtc);

    assertThat(againResult.srsState()).isEqualTo(SrsState.RELEARNING);
    assertThat(againResult.vocabularyStatus()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(againResult.intervalSeconds()).isEqualTo(600L);
    assertThat(againResult.repetitions()).isEqualTo(9);
    assertThat(againResult.lapses()).isEqualTo(3);
    assertThat(againResult.stability()).isLessThan(15.0);
    assertThat(againResult.difficulty()).isGreaterThan(4.0);
  }

  @Test
  @DisplayName(
      "Consistency: RatingOptionsBuilder preview intervals match policy calculation exactly")
  void previewOptionsMatchPolicyCalculation() {
    var user = new User(UUID.randomUUID(), "User", "user@example.com");
    var word = new Word(UUID.randomUUID(), "test", "en");
    var card =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.KNOWN,
            nowUtc.minusDays(10),
            nowUtc.minusDays(10),
            10L,
            5,
            nowUtc.minusDays(10),
            nowUtc,
            SrsState.REVIEW,
            10.0,
            5.0,
            5,
            0);

    var options = ratingOptionsBuilder.buildRatingOptions(card, nowUtc);
    var againCalc = policy.calculateNextState(card.toSrsParameters(), ReviewRating.AGAIN, nowUtc);
    var goodCalc = policy.calculateNextState(card.toSrsParameters(), ReviewRating.GOOD, nowUtc);

    var againOpt = options.get(0);
    assertThat(againOpt.rating()).isEqualTo(ReviewRating.AGAIN);
    assertThat(againOpt.intervalSeconds()).isEqualTo(againCalc.intervalSeconds());
    assertThat(againOpt.nextReviewAt()).isEqualTo(againCalc.nextReviewAt());

    var goodOpt = options.get(1);
    assertThat(goodOpt.rating()).isEqualTo(ReviewRating.GOOD);
    assertThat(goodOpt.intervalSeconds()).isEqualTo(goodCalc.intervalSeconds());
    assertThat(goodOpt.nextReviewAt()).isEqualTo(goodCalc.nextReviewAt());
  }
}
