package com.soap.soap.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.model.SrsCalculationResult;
import com.soap.soap.domain.model.SrsItemParameters;
import com.soap.soap.domain.model.SrsState;
import com.soap.soap.domain.model.VocabularyStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FsrsSchedulerTest {

  private FsrsScheduler scheduler;
  private LocalDateTime nowUtc;

  @BeforeEach
  void setUp() {
    scheduler = new FsrsScheduler();
    nowUtc = LocalDateTime.of(2026, 9, 18, 12, 0, 0);
  }

  @Test
  @DisplayName("Reference Vector 1: Initial Difficulty D0(G) = clamp(w4 - (G - 3) * w5, 1.0, 10.0)")
  void canonicalInitialDifficulty() {
    assertThat(scheduler.initDifficulty(ReviewRating.AGAIN)).isEqualTo(7.6214);
    assertThat(scheduler.initDifficulty(ReviewRating.HARD)).isEqualTo(6.3916);
    assertThat(scheduler.initDifficulty(ReviewRating.GOOD)).isEqualTo(5.1618);
    assertThat(scheduler.initDifficulty(ReviewRating.EASY)).isEqualTo(3.9320);
  }

  @Test
  @DisplayName("Reference Vector 2: Initial Stability S0(G) = w[G-1]")
  void canonicalInitialStability() {
    assertThat(scheduler.initStability(ReviewRating.AGAIN)).isEqualTo(0.4872);
    assertThat(scheduler.initStability(ReviewRating.HARD)).isEqualTo(1.4003);
    assertThat(scheduler.initStability(ReviewRating.GOOD)).isEqualTo(3.7145);
    assertThat(scheduler.initStability(ReviewRating.EASY)).isEqualTo(13.8206);
  }

  @Test
  @DisplayName("Reference Vector 3: Difficulty Updates with Mean Reversion from D = 5.0")
  void canonicalDifficultyUpdates() {
    assertThat(scheduler.nextDifficulty(5.0, ReviewRating.AGAIN)).isCloseTo(6.7444, within(1e-4));
    assertThat(scheduler.nextDifficulty(5.0, ReviewRating.HARD)).isCloseTo(5.8747, within(1e-4));
    assertThat(scheduler.nextDifficulty(5.0, ReviewRating.GOOD)).isCloseTo(5.0050, within(1e-4));
    assertThat(scheduler.nextDifficulty(5.0, ReviewRating.EASY)).isCloseTo(4.1353, within(1e-4));
  }

  @Test
  @DisplayName("Reference Vector 4: Retrievability Forgetting Curve R(t, S)")
  void canonicalRetrievability() {
    assertThat(scheduler.retrievability(0.0, 30.0)).isEqualTo(1.0000);
    assertThat(scheduler.retrievability(30.0, 30.0)).isCloseTo(0.9000, within(1e-4));
  }

  @Test
  @DisplayName(
      "Reference Vector 5: Mature Recall Stability (S=30, D=4, t=30, G=GOOD) exact double 105.5511")
  void matureRecallStability() {
    assertThat(scheduler.nextRecallStability(4.0, 30.0, 0.90, ReviewRating.GOOD))
        .isCloseTo(105.5511, within(1e-4));
  }

  @Test
  @DisplayName(
      "Reference Vector 6: Mature Lapse Stability (S=60, D=4, t=60, G=AGAIN) exact double 6.1899")
  void matureLapseStability() {
    assertThat(scheduler.nextLapseStability(4.0, 60.0, 0.90)).isCloseTo(6.1899, within(1e-4));
  }

  @Test
  @DisplayName("Product Policy: New Item evaluations")
  void newItemEvaluations() {
    var initial = SrsItemParameters.defaultForNew();

    // AGAIN -> 10m, LEARNING
    SrsCalculationResult again = scheduler.calculateNextState(initial, ReviewRating.AGAIN, nowUtc);
    assertThat(again.srsState()).isEqualTo(SrsState.LEARNING);
    assertThat(again.vocabularyStatus()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(again.intervalSeconds()).isEqualTo(600L);
    assertThat(again.repetitions()).isEqualTo(1);
    assertThat(again.lapses()).isEqualTo(0);
    assertThat(again.stability()).isEqualTo(0.4872);
    assertThat(again.difficulty()).isEqualTo(7.6214);

    // HARD -> 12h, LEARNING
    SrsCalculationResult hard = scheduler.calculateNextState(initial, ReviewRating.HARD, nowUtc);
    assertThat(hard.srsState()).isEqualTo(SrsState.LEARNING);
    assertThat(hard.vocabularyStatus()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(hard.intervalSeconds()).isEqualTo(43200L);
    assertThat(hard.stability()).isEqualTo(1.4003);
    assertThat(hard.difficulty()).isEqualTo(6.3916);

    // GOOD -> 4 days, REVIEW / KNOWN
    SrsCalculationResult good = scheduler.calculateNextState(initial, ReviewRating.GOOD, nowUtc);
    assertThat(good.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(good.vocabularyStatus()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(good.intervalSeconds()).isEqualTo(4 * 86400L);
    assertThat(good.stability()).isEqualTo(3.7145);
    assertThat(good.difficulty()).isEqualTo(5.1618);

    // EASY -> 14 days, REVIEW / KNOWN
    SrsCalculationResult easy = scheduler.calculateNextState(initial, ReviewRating.EASY, nowUtc);
    assertThat(easy.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(easy.vocabularyStatus()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(easy.intervalSeconds()).isEqualTo(14 * 86400L);
    assertThat(easy.stability()).isEqualTo(13.8206);
    assertThat(easy.difficulty()).isEqualTo(3.9320);
  }

  @Test
  @DisplayName("Product Policy: Learning progression to graduation with GOOD")
  void learningGraduation() {
    var learning =
        new SrsItemParameters(
            SrsState.LEARNING,
            VocabularyStatus.LEARNING,
            0.4872,
            7.6214,
            1,
            0,
            nowUtc.minusMinutes(10),
            nowUtc);

    SrsCalculationResult graduated =
        scheduler.calculateNextState(learning, ReviewRating.GOOD, nowUtc);
    assertThat(graduated.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(graduated.vocabularyStatus()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(graduated.intervalSeconds()).isEqualTo(4 * 86400L);
    assertThat(graduated.repetitions()).isEqualTo(2);
    assertThat(graduated.lapses()).isEqualTo(0);
    assertThat(graduated.difficulty()).isEqualTo(7.5452);
  }

  @Test
  @DisplayName("Product Policy: Mature Review Lapse and Full Relearning Cycle")
  void matureReviewLapseAndRelearningCycle() {
    var mature =
        new SrsItemParameters(
            SrsState.REVIEW, VocabularyStatus.KNOWN, 60.0, 4.0, 6, 0, nowUtc.minusDays(60), nowUtc);

    // -----------------------------------------------------------------------
    // A. REVIEW + AGAIN → lapse to RELEARNING
    // -----------------------------------------------------------------------
    SrsCalculationResult lapse = scheduler.calculateNextState(mature, ReviewRating.AGAIN, nowUtc);
    assertThat(lapse.srsState()).isEqualTo(SrsState.RELEARNING);
    assertThat(lapse.vocabularyStatus()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(lapse.repetitions()).isEqualTo(7);
    assertThat(lapse.lapses()).isEqualTo(1); // lapses +1 at lapse time
    assertThat(lapse.stability()).isCloseTo(6.1899, within(1e-4)); // canonical lapse stability
    assertThat(lapse.intervalSeconds()).isEqualTo(600L); // +10 min product step

    double postLapseStability = lapse.stability();

    // Build RELEARNING parameters to simulate the next step(s)
    var inRelearning =
        new SrsItemParameters(
            lapse.srsState(),
            lapse.vocabularyStatus(),
            lapse.stability(),
            lapse.difficulty(),
            lapse.repetitions(),
            lapse.lapses(),
            nowUtc,
            nowUtc.plusSeconds(600L));

    // -----------------------------------------------------------------------
    // B. RELEARNING + AGAIN → stays RELEARNING, lapses NOT re-incremented, stability preserved
    // -----------------------------------------------------------------------
    var relearnAgain =
        scheduler.calculateNextState(inRelearning, ReviewRating.AGAIN, nowUtc.plusMinutes(10));
    assertThat(relearnAgain.srsState()).isEqualTo(SrsState.RELEARNING);
    assertThat(relearnAgain.vocabularyStatus()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(relearnAgain.repetitions()).isEqualTo(8);
    assertThat(relearnAgain.lapses())
        .isEqualTo(1); // STILL 1 — lapses do NOT increment in relearning
    assertThat(relearnAgain.intervalSeconds()).isEqualTo(600L); // +10 min
    assertThat(relearnAgain.stability()).isCloseTo(postLapseStability, within(1e-4)); // preserved

    // -----------------------------------------------------------------------
    // C. RELEARNING + HARD → stays RELEARNING at +12h, stability preserved
    // -----------------------------------------------------------------------
    var relearnHard =
        scheduler.calculateNextState(inRelearning, ReviewRating.HARD, nowUtc.plusMinutes(10));
    assertThat(relearnHard.srsState()).isEqualTo(SrsState.RELEARNING);
    assertThat(relearnHard.vocabularyStatus()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(relearnHard.intervalSeconds()).isEqualTo(43200L); // +12 h
    assertThat(relearnHard.stability()).isCloseTo(postLapseStability, within(1e-4)); // preserved
    assertThat(relearnHard.lapses()).isEqualTo(1); // unchanged

    // -----------------------------------------------------------------------
    // D. RELEARNING + GOOD → graduate to REVIEW / KNOWN at +1 day; stability preserved (NO *0.35)
    // -----------------------------------------------------------------------
    var relearnGood =
        scheduler.calculateNextState(inRelearning, ReviewRating.GOOD, nowUtc.plusMinutes(10));
    assertThat(relearnGood.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(relearnGood.vocabularyStatus()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(relearnGood.repetitions()).isEqualTo(8);
    assertThat(relearnGood.lapses()).isEqualTo(1);
    assertThat(relearnGood.intervalSeconds()).isEqualTo(86400L); // exactly +1 day (product policy)
    // Post-lapse stability is PRESERVED: no custom multiplier applied.
    // The old 60-day interval is NOT restored.
    assertThat(relearnGood.stability()).isCloseTo(postLapseStability, within(1e-4)); // NO *0.35
    // Post-lapse stability (≈6.19) is far below the pre-lapse 60d — but above 5.0 for this case.
    assertThat(relearnGood.stability()).isLessThan(15.0); // far below pre-lapse 60d

    // -----------------------------------------------------------------------
    // E. RELEARNING + EASY → graduate to REVIEW / KNOWN at +2 days; stability preserved (NO *0.6)
    // -----------------------------------------------------------------------
    var relearnEasy =
        scheduler.calculateNextState(inRelearning, ReviewRating.EASY, nowUtc.plusMinutes(10));
    assertThat(relearnEasy.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(relearnEasy.vocabularyStatus()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(relearnEasy.intervalSeconds())
        .isEqualTo(2 * 86400L); // exactly +2 days (product policy)
    assertThat(relearnEasy.stability()).isCloseTo(postLapseStability, within(1e-4)); // NO *0.6
    assertThat(relearnEasy.lapses()).isEqualTo(1); // unchanged
  }
}
