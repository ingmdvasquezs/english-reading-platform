package com.soap.soap.domain.model;

import com.soap.soap.domain.exception.InvalidVocabularyStateException;
import com.soap.soap.domain.service.FsrsScheduler;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

public record UserVocabulary(
    UUID id,
    User user,
    Word word,
    VocabularyStatus status,
    LocalDateTime firstSeenAt,
    LocalDateTime learnedAt,
    Long version,
    int reviewStage,
    LocalDateTime lastReviewedAt,
    LocalDateTime nextReviewAt,
    SrsState srsState,
    double stability,
    double difficulty,
    int repetitions,
    int lapses) {

  private static final FsrsScheduler DEFAULT_SCHEDULER = new FsrsScheduler();

  public UserVocabulary(
      UUID id,
      User user,
      Word word,
      VocabularyStatus status,
      LocalDateTime firstSeenAt,
      LocalDateTime learnedAt) {
    this(
        id,
        user,
        word,
        status,
        firstSeenAt,
        learnedAt,
        null,
        0,
        null,
        null,
        defaultStateForStatus(status),
        defaultStabilityForStatus(status),
        defaultDifficultyForStatus(status),
        0,
        0);
  }

  public UserVocabulary(
      UUID id,
      User user,
      Word word,
      VocabularyStatus status,
      LocalDateTime firstSeenAt,
      LocalDateTime learnedAt,
      Long version) {
    this(
        id,
        user,
        word,
        status,
        firstSeenAt,
        learnedAt,
        version,
        0,
        null,
        null,
        defaultStateForStatus(status),
        defaultStabilityForStatus(status),
        defaultDifficultyForStatus(status),
        0,
        0);
  }

  public UserVocabulary(
      UUID id,
      User user,
      Word word,
      VocabularyStatus status,
      LocalDateTime firstSeenAt,
      LocalDateTime learnedAt,
      Long version,
      int reviewStage,
      LocalDateTime lastReviewedAt,
      LocalDateTime nextReviewAt) {
    this(
        id,
        user,
        word,
        status,
        firstSeenAt,
        learnedAt,
        version,
        reviewStage,
        lastReviewedAt,
        nextReviewAt,
        defaultStateForStatus(status),
        defaultStabilityForStatus(status),
        defaultDifficultyForStatus(status),
        0,
        0);
  }

  public UserVocabulary {
    Objects.requireNonNull(user, "User must not be null");
    Objects.requireNonNull(word, "Word must not be null");
    Objects.requireNonNull(status, "Vocabulary status must not be null");
    Objects.requireNonNull(firstSeenAt, "First seen date must not be null");
    if (srsState == null) {
      srsState = defaultStateForStatus(status);
    }
    if (difficulty == 0.0) {
      difficulty = 5.0;
    }
    if (status == VocabularyStatus.KNOWN && learnedAt == null) {
      throw new InvalidVocabularyStateException("Known vocabulary must have a learned date");
    }
    if (status != VocabularyStatus.KNOWN && learnedAt != null) {
      throw new InvalidVocabularyStateException("Only known vocabulary can have a learned date");
    }
    if (reviewStage < 0 || reviewStage > 5) {
      throw new InvalidVocabularyStateException("Review stage must be between 0 and 5");
    }
    if (difficulty < 1.0 || difficulty > 10.0) {
      throw new InvalidVocabularyStateException("Difficulty must be between 1.0 and 10.0");
    }
    if (stability < 0.0) {
      throw new InvalidVocabularyStateException("Stability must be non-negative");
    }
    if (repetitions < 0) {
      throw new InvalidVocabularyStateException("Repetitions must be non-negative");
    }
    if (lapses < 0) {
      throw new InvalidVocabularyStateException("Lapses must be non-negative");
    }
  }

  private static SrsState defaultStateForStatus(VocabularyStatus status) {
    if (status == null) {
      return SrsState.NEW;
    }
    return switch (status) {
      case KNOWN -> SrsState.REVIEW;
      case LEARNING -> SrsState.LEARNING;
      case NEW, IGNORED -> SrsState.NEW;
    };
  }

  private static double defaultStabilityForStatus(VocabularyStatus status) {
    if (status == null) {
      return 0.0;
    }
    return switch (status) {
      case KNOWN -> 13.8206;
      case LEARNING -> 0.4872;
      case NEW, IGNORED -> 0.0;
    };
  }

  private static double defaultDifficultyForStatus(VocabularyStatus status) {
    if (status == null) {
      return 5.0;
    }
    return switch (status) {
      case KNOWN -> 3.9320;
      case LEARNING -> 7.6214;
      case NEW, IGNORED -> 5.0;
    };
  }

  /**
   * Creates a new vocabulary entry with its initial SRS V2 state.
   *
   * <p>Scheduling for KNOWN words is derived from the canonical FSRS-4.5 initial stability for an
   * EASY first-time rating (w[3] = 13.8206 days), rounded to the nearest integer day. {@code
   * reviewStage} is kept at 0 for all new entries — it is a legacy V1 field that the SRS V2 runtime
   * does NOT use for scheduling.
   */
  public static UserVocabulary createInitial(
      User user, Word word, VocabularyStatus status, Clock clock) {
    Objects.requireNonNull(user, "User must not be null");
    Objects.requireNonNull(word, "Word must not be null");
    Objects.requireNonNull(status, "Vocabulary status must not be null");
    Objects.requireNonNull(clock, "Clock must not be null");
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var learnedAt = status == VocabularyStatus.KNOWN ? nowUtc : null;
    var state = defaultStateForStatus(status);
    var stability = defaultStabilityForStatus(status);
    var difficulty = defaultDifficultyForStatus(status);
    // SRS V2: nextReviewAt is derived from initial stability, NOT from Leitner stage intervals.
    // KNOWN  → stability = 13.8206 → round → 14 days (canonical FSRS-4.5 EASY first impression)
    // LEARNING → immediately due
    // NEW / IGNORED → no schedule
    LocalDateTime nextReview =
        switch (status) {
          case LEARNING -> nowUtc;
          case KNOWN -> {
            int days = Math.max(1, (int) Math.round(stability));
            yield nowUtc.plusDays(days);
          }
          case NEW, IGNORED -> null;
        };
    int reps = (status == VocabularyStatus.KNOWN) ? 1 : 0;
    // reviewStage = 0: legacy V1 field — SRS V2 does NOT read or write it for scheduling.
    return new UserVocabulary(
        null,
        user,
        word,
        status,
        nowUtc,
        learnedAt,
        null,
        0,
        null,
        nextReview,
        state,
        stability,
        difficulty,
        reps,
        0);
  }

  /**
   * Manually changes the VocabularyStatus and re-initialises SRS V2 state accordingly.
   *
   * <p>For the KNOWN transition the nextReviewAt is derived from the word's current stability (SRS
   * V2 policy). {@code reviewStage} is treated as a deprecated legacy field: it is passed through
   * unchanged and is NOT used to compute scheduling intervals.
   */
  public UserVocabulary changeStatus(VocabularyStatus newStatus, Clock clock) {
    Objects.requireNonNull(newStatus, "Vocabulary status must not be null");
    Objects.requireNonNull(clock, "Clock must not be null");
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    return switch (newStatus) {
      case KNOWN -> {
        var newLearnedAt =
            (status == VocabularyStatus.KNOWN && learnedAt != null) ? learnedAt : nowUtc;
        // SRS V2: use existing stability if present; fall back to the canonical default for a
        // word explicitly marked KNOWN (w[3] = 13.8206, the EASY initial stability).
        var newStability = (stability > 0.0) ? stability : 13.8206;
        var newDifficulty = (difficulty > 1.0) ? difficulty : 3.9320;
        // nextReviewAt is based purely on SRS stability, NOT on reviewStage.
        int days = Math.max(1, (int) Math.round(newStability));
        var nextReview = nowUtc.plusDays(days);
        // reviewStage: passed through unchanged — legacy field, NOT read for V2 scheduling.
        yield new UserVocabulary(
            id,
            user,
            word,
            VocabularyStatus.KNOWN,
            firstSeenAt,
            newLearnedAt,
            version,
            reviewStage, // unchanged — deprecated, not used for scheduling
            lastReviewedAt,
            nextReview,
            SrsState.REVIEW,
            newStability,
            newDifficulty,
            Math.max(1, repetitions),
            lapses);
      }
      case LEARNING ->
          new UserVocabulary(
              id,
              user,
              word,
              VocabularyStatus.LEARNING,
              firstSeenAt,
              null,
              version,
              0,
              null,
              nowUtc,
              SrsState.LEARNING,
              0.4872,
              (difficulty > 0.0 ? difficulty : 7.6214),
              repetitions,
              lapses);
      case NEW ->
          new UserVocabulary(
              id,
              user,
              word,
              VocabularyStatus.NEW,
              firstSeenAt,
              null,
              version,
              0,
              null,
              null,
              SrsState.NEW,
              0.0,
              5.0,
              0,
              0);
      case IGNORED ->
          new UserVocabulary(
              id,
              user,
              word,
              VocabularyStatus.IGNORED,
              firstSeenAt,
              null,
              version,
              0,
              null,
              null,
              SrsState.NEW,
              0.0,
              5.0,
              0,
              0);
    };
  }

  public SrsItemParameters toSrsParameters() {
    return new SrsItemParameters(
        srsState != null ? srsState : defaultStateForStatus(status),
        status,
        stability,
        difficulty > 0.0 ? difficulty : 5.0,
        repetitions,
        lapses,
        lastReviewedAt,
        nextReviewAt);
  }

  public UserVocabulary applyRating(ReviewRating rating, Clock clock, FsrsScheduler scheduler) {
    Objects.requireNonNull(rating, "Review rating must not be null");
    Objects.requireNonNull(clock, "Clock must not be null");
    var calcEngine = (scheduler != null) ? scheduler : DEFAULT_SCHEDULER;
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

    var calc = calcEngine.calculateNextState(toSrsParameters(), rating, nowUtc);
    var newLearnedAt =
        calc.vocabularyStatus() == VocabularyStatus.KNOWN
            ? ((status == VocabularyStatus.KNOWN && learnedAt != null) ? learnedAt : nowUtc)
            : null;

    // SRS V2: reviewStage is NOT read or written during rating application.
    // It is a deprecated V1 Leitner field; pass it through unchanged.
    return new UserVocabulary(
        id,
        user,
        word,
        calc.vocabularyStatus(),
        firstSeenAt,
        newLearnedAt,
        version,
        reviewStage, // unchanged — deprecated, SRS V2 does not use this for scheduling
        nowUtc,
        calc.nextReviewAt(),
        calc.srsState(),
        calc.stability(),
        calc.difficulty(),
        calc.repetitions(),
        calc.lapses());
  }

  public UserVocabulary applyReviewAssessment(ReviewAssessment assessment, Clock clock) {
    Objects.requireNonNull(assessment, "Review assessment must not be null");
    Objects.requireNonNull(clock, "Clock must not be null");
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

    return switch (assessment) {
      case FORGOT ->
          new UserVocabulary(
              id,
              user,
              word,
              VocabularyStatus.LEARNING,
              firstSeenAt,
              null,
              version,
              0,
              nowUtc,
              nowUtc.plusDays(1));
      case STRUGGLED ->
          new UserVocabulary(
              id,
              user,
              word,
              VocabularyStatus.LEARNING,
              firstSeenAt,
              null,
              version,
              reviewStage,
              nowUtc,
              nowUtc.plusDays(1));
      case REMEMBERED -> {
        var newStage = Math.min(5, reviewStage + 1);
        var intervalDays = reviewIntervalDays(newStage);
        var nextReview = nowUtc.plusDays(intervalDays);
        var newLearnedAt =
            (status == VocabularyStatus.KNOWN && learnedAt != null) ? learnedAt : nowUtc;
        yield new UserVocabulary(
            id,
            user,
            word,
            VocabularyStatus.KNOWN,
            firstSeenAt,
            newLearnedAt,
            version,
            newStage,
            nowUtc,
            nextReview);
      }
    };
  }

  public static int reviewIntervalDays(int stage) {
    return switch (stage) {
      case 0 -> 1;
      case 1 -> 3;
      case 2 -> 7;
      case 3 -> 14;
      case 4, 5 -> 30;
      default -> 30;
    };
  }
}
