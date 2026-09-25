package com.soap.soap.domain.service;

import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.model.SrsCalculationResult;
import com.soap.soap.domain.model.SrsItemParameters;
import com.soap.soap.domain.model.SrsState;
import com.soap.soap.domain.model.VocabularyStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class FsrsScheduler {

  public static final double[] CANONICAL_FSRS45_WEIGHTS = {
    0.4872, // w[0]: S0(Again)
    1.4003, // w[1]: S0(Hard)
    3.7145, // w[2]: S0(Good)
    13.8206, // w[3]: S0(Easy)
    5.1618, // w[4]: D0(Good) baseline
    1.2298, // w[5]: D0 step scale
    0.8975, // w[6]: D step scale on review
    0.0310, // w[7]: D mean reversion weight
    1.6474, // w[8]: S recall factor
    0.1367, // w[9]: S recall stability exponent
    1.0461, // w[10]: S recall retrievability exponent
    2.1072, // w[11]: S lapse base factor
    0.0793, // w[12]: S lapse difficulty exponent
    0.3246, // w[13]: S lapse stability exponent
    1.5870, // w[14]: S lapse retrievability exponent
    0.2272, // w[15]: Hard penalty factor
    2.8755 // w[16]: Easy bonus factor
  };

  private static final double DECAY = -0.5;
  private static final double FACTOR = 19.0 / 81.0;

  public double initStability(ReviewRating rating) {
    Objects.requireNonNull(rating, "Review rating must not be null");
    return CANONICAL_FSRS45_WEIGHTS[rating.getGrade() - 1];
  }

  public double initDifficulty(ReviewRating rating) {
    Objects.requireNonNull(rating, "Review rating must not be null");
    double raw =
        CANONICAL_FSRS45_WEIGHTS[4] - (rating.getGrade() - 3) * CANONICAL_FSRS45_WEIGHTS[5];
    return round4(clamp(raw, 1.0, 10.0));
  }

  public double nextDifficulty(double currentDifficulty, ReviewRating rating) {
    Objects.requireNonNull(rating, "Review rating must not be null");
    double raw = currentDifficulty - CANONICAL_FSRS45_WEIGHTS[6] * (rating.getGrade() - 3);
    double reverted =
        CANONICAL_FSRS45_WEIGHTS[7] * CANONICAL_FSRS45_WEIGHTS[4]
            + (1.0 - CANONICAL_FSRS45_WEIGHTS[7]) * raw;
    return round4(clamp(reverted, 1.0, 10.0));
  }

  public double retrievability(double elapsedDays, double stability) {
    if (stability <= 0.0) {
      return 0.0;
    }
    if (elapsedDays <= 0.0) {
      return 1.0;
    }
    return Math.pow(1.0 + FACTOR * (elapsedDays / stability), DECAY);
  }

  public double nextRecallStability(
      double difficulty, double stability, double retrievability, ReviewRating rating) {
    Objects.requireNonNull(rating, "Review rating must not be null");
    double h =
        switch (rating) {
          case HARD -> CANONICAL_FSRS45_WEIGHTS[15];
          case GOOD -> 1.0;
          case EASY -> CANONICAL_FSRS45_WEIGHTS[16];
          default -> 1.0;
        };
    double factor =
        Math.exp(CANONICAL_FSRS45_WEIGHTS[8])
            * (11.0 - difficulty)
            * Math.pow(stability, -CANONICAL_FSRS45_WEIGHTS[9])
            * (Math.exp((1.0 - retrievability) * CANONICAL_FSRS45_WEIGHTS[10]) - 1.0)
            * h;
    return stability * (1.0 + factor);
  }

  public double nextLapseStability(double difficulty, double stability, double retrievability) {
    return CANONICAL_FSRS45_WEIGHTS[11]
        * Math.pow(difficulty, -CANONICAL_FSRS45_WEIGHTS[12])
        * (Math.pow(stability + 1.0, CANONICAL_FSRS45_WEIGHTS[13]) - 1.0)
        * Math.exp((1.0 - retrievability) * CANONICAL_FSRS45_WEIGHTS[14]);
  }

  public SrsCalculationResult calculateNextState(
      SrsItemParameters current, ReviewRating rating, LocalDateTime nowUtc) {
    Objects.requireNonNull(current, "Current SRS parameters must not be null");
    Objects.requireNonNull(rating, "Review rating must not be null");
    Objects.requireNonNull(nowUtc, "Now timestamp must not be null");

    double elapsedDays = 0.0;
    if (current.lastReviewedAt() != null) {
      long elapsedSeconds =
          Duration.between(
                  current.lastReviewedAt().atOffset(ZoneOffset.UTC),
                  nowUtc.atOffset(ZoneOffset.UTC))
              .toSeconds();
      elapsedDays = Math.max(0.0, elapsedSeconds / 86400.0);
    }
    double r = (current.stability() > 0.0) ? retrievability(elapsedDays, current.stability()) : 1.0;

    SrsState currentState = current.state() != null ? current.state() : SrsState.NEW;

    return switch (currentState) {
      case NEW -> calculateForNew(rating, nowUtc);
      case LEARNING -> calculateForLearning(current, rating, nowUtc);
      case REVIEW -> calculateForReview(current, rating, r, nowUtc);
      case RELEARNING -> calculateForRelearning(current, rating, nowUtc);
    };
  }

  private SrsCalculationResult calculateForNew(ReviewRating rating, LocalDateTime nowUtc) {
    int reps = 1;
    int lapses = 0;
    double initS = initStability(rating);
    double initD = initDifficulty(rating);

    return switch (rating) {
      case AGAIN ->
          new SrsCalculationResult(
              SrsState.LEARNING,
              VocabularyStatus.LEARNING,
              round4(initS),
              round4(initD),
              reps,
              lapses,
              nowUtc.plusSeconds(600L),
              600L);
      case HARD ->
          new SrsCalculationResult(
              SrsState.LEARNING,
              VocabularyStatus.LEARNING,
              round4(initS),
              round4(initD),
              reps,
              lapses,
              nowUtc.plusSeconds(900L),
              900L);
      case GOOD -> {
        int days = Math.max(1, (int) Math.round(initS));
        yield new SrsCalculationResult(
            SrsState.REVIEW,
            VocabularyStatus.KNOWN,
            round4(initS),
            round4(initD),
            reps,
            lapses,
            nowUtc.plusDays(days),
            days * 86400L);
      }
      case EASY -> {
        int days = Math.max(1, (int) Math.round(initS));
        yield new SrsCalculationResult(
            SrsState.REVIEW,
            VocabularyStatus.KNOWN,
            round4(initS),
            round4(initD),
            reps,
            lapses,
            nowUtc.plusDays(days),
            days * 86400L);
      }
    };
  }

  private SrsCalculationResult calculateForLearning(
      SrsItemParameters current, ReviewRating rating, LocalDateTime nowUtc) {
    int reps = current.repetitions() + 1;
    int lapses = current.lapses();
    double nextD = nextDifficulty(current.difficulty(), rating);

    return switch (rating) {
      case AGAIN -> {
        double s = initStability(ReviewRating.AGAIN);
        yield new SrsCalculationResult(
            SrsState.LEARNING,
            VocabularyStatus.LEARNING,
            round4(s),
            round4(nextD),
            reps,
            lapses,
            nowUtc.plusSeconds(600L),
            600L);
      }
      case HARD -> {
        double s = Math.max(current.stability(), initStability(ReviewRating.HARD));
        yield new SrsCalculationResult(
            SrsState.LEARNING,
            VocabularyStatus.LEARNING,
            round4(s),
            round4(nextD),
            reps,
            lapses,
            nowUtc.plusSeconds(900L),
            900L);
      }
      case GOOD -> {
        double s = initStability(ReviewRating.GOOD);
        int days = Math.max(1, (int) Math.round(s));
        yield new SrsCalculationResult(
            SrsState.REVIEW,
            VocabularyStatus.KNOWN,
            round4(s),
            round4(nextD),
            reps,
            lapses,
            nowUtc.plusDays(days),
            days * 86400L);
      }
      case EASY -> {
        double s = initStability(ReviewRating.EASY);
        int days = Math.max(1, (int) Math.round(s));
        yield new SrsCalculationResult(
            SrsState.REVIEW,
            VocabularyStatus.KNOWN,
            round4(s),
            round4(nextD),
            reps,
            lapses,
            nowUtc.plusDays(days),
            days * 86400L);
      }
    };
  }

  private SrsCalculationResult calculateForReview(
      SrsItemParameters current, ReviewRating rating, double r, LocalDateTime nowUtc) {
    int reps = current.repetitions() + 1;
    double nextD = nextDifficulty(current.difficulty(), rating);

    if (rating == ReviewRating.AGAIN) {
      int lapses = current.lapses() + 1;
      double lapseS = nextLapseStability(current.difficulty(), current.stability(), r);
      return new SrsCalculationResult(
          SrsState.RELEARNING,
          VocabularyStatus.LEARNING,
          round4(lapseS),
          round4(nextD),
          reps,
          lapses,
          nowUtc.plusSeconds(600L),
          600L);
    } else {
      int lapses = current.lapses();
      double recallS = nextRecallStability(current.difficulty(), current.stability(), r, rating);
      int days = Math.max(1, (int) Math.round(recallS));
      return new SrsCalculationResult(
          SrsState.REVIEW,
          VocabularyStatus.KNOWN,
          round4(recallS),
          round4(nextD),
          reps,
          lapses,
          nowUtc.plusDays(days),
          days * 86400L);
    }
  }

  // ---------------------------------------------------------------------------
  // RELEARNING — Product Step Policy
  //
  // When a REVIEW card lapses (AGAIN), canonical FSRS-4.5 already computed a
  // penalised post-lapse stability (S_lapse).  During the relearning cycle we
  // PRESERVE that stability unchanged — we must not apply ad-hoc multipliers on
  // top of it.  The product scheduling policy matches Anki single-step behavior:
  //   AGAIN → +10 min  (1x learning step)
  //   HARD  → +15 min  (1.5x learning step)
  //   GOOD  → +1 day   (graduated, first short interval)
  //   EASY  → +2 days  (graduated, slightly longer interval)
  // At the next real REVIEW the full FSRS recall-stability formula will be
  // applied with S_lapse, giving a naturally rebuilt interval without ever
  // restoring the pre-lapse mature interval.
  // ---------------------------------------------------------------------------
  private SrsCalculationResult calculateForRelearning(
      SrsItemParameters current, ReviewRating rating, LocalDateTime nowUtc) {
    int reps = current.repetitions() + 1;
    int lapses = current.lapses(); // lapses do NOT increment again during relearning
    double postLapseStability = current.stability(); // preserved — no custom multipliers
    double nextD = nextDifficulty(current.difficulty(), rating);

    return switch (rating) {
      // Still in relearning — short step, preserve post-lapse stability
      case AGAIN ->
          new SrsCalculationResult(
              SrsState.RELEARNING,
              VocabularyStatus.LEARNING,
              round4(postLapseStability),
              round4(nextD),
              reps,
              lapses,
              nowUtc.plusSeconds(600L), // +10 min
              600L);
      // Still in relearning — 15m step (1.5x of 10m step), preserve post-lapse stability
      case HARD ->
          new SrsCalculationResult(
              SrsState.RELEARNING,
              VocabularyStatus.LEARNING,
              round4(postLapseStability),
              round4(nextD),
              reps,
              lapses,
              nowUtc.plusSeconds(900L), // +15 min
              900L);
      // Graduate — product policy: +1 day, post-lapse stability preserved
      case GOOD ->
          new SrsCalculationResult(
              SrsState.REVIEW,
              VocabularyStatus.KNOWN,
              round4(postLapseStability), // NO *0.35
              round4(nextD),
              reps,
              lapses,
              nowUtc.plusDays(1), // product step policy
              86400L);
      // Graduate — product policy: +2 days, post-lapse stability preserved
      case EASY ->
          new SrsCalculationResult(
              SrsState.REVIEW,
              VocabularyStatus.KNOWN,
              round4(postLapseStability), // NO *0.6
              round4(nextD),
              reps,
              lapses,
              nowUtc.plusDays(2), // product step policy
              2 * 86400L);
    };
  }

  public static double round4(double value) {
    return Math.round(value * 10000.0) / 10000.0;
  }

  private static double clamp(double val, double min, double max) {
    return Math.min(max, Math.max(min, val));
  }
}
