package com.soap.soap.domain.service;

import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.model.SrsCalculationResult;
import com.soap.soap.domain.model.SrsItemParameters;
import com.soap.soap.domain.model.SrsState;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Product scheduling policy overlay on top of canonical FSRS-4.5.
 *
 * <p>Preserves canonical FSRS memory state (stability, difficulty, repetitions, lapses) while
 * enforcing binary review product semantics:
 *
 * <ul>
 *   <li>NEW / LEARNING / RELEARNING + GOOD: 1 day graduation / recovery step (86400s).
 *   <li>REVIEW + GOOD: Dynamic FSRS-derived interval based on memory state.
 *   <li>AGAIN: Short 10m step (600s), resetting to LEARNING (from NEW) or RELEARNING (from REVIEW).
 * </ul>
 */
@Component
public class VocabularyReviewSchedulingPolicy {

  private final FsrsScheduler scheduler;

  public VocabularyReviewSchedulingPolicy(FsrsScheduler scheduler) {
    this.scheduler = Objects.requireNonNull(scheduler, "FsrsScheduler must not be null");
  }

  public SrsCalculationResult calculateNextState(
      SrsItemParameters current, ReviewRating rating, LocalDateTime nowUtc) {
    Objects.requireNonNull(current, "Current SRS parameters must not be null");
    Objects.requireNonNull(rating, "Review rating must not be null");
    Objects.requireNonNull(nowUtc, "Now timestamp must not be null");

    // 1. Compute canonical FSRS memory state and raw transitions
    SrsCalculationResult canonical = scheduler.calculateNextState(current, rating, nowUtc);

    SrsState currentState = current.state() != null ? current.state() : SrsState.NEW;

    // 2. Binary Product Scheduling Policy overlay:
    // When rating is GOOD and card is in NEW, LEARNING, or RELEARNING:
    // Product policy sets nextReviewAt to +1 day (86400s), graduating/recovering to REVIEW.
    // FSRS memory state (stability, difficulty, repetitions, lapses) is strictly preserved!
    if (rating == ReviewRating.GOOD) {
      if (currentState == SrsState.NEW
          || currentState == SrsState.LEARNING
          || currentState == SrsState.RELEARNING) {
        return new SrsCalculationResult(
            SrsState.REVIEW,
            current.vocabularyStatus(),
            canonical.stability(),
            canonical.difficulty(),
            canonical.repetitions(),
            canonical.lapses(),
            nowUtc.plusDays(1),
            86400L);
      }
    }

    // In all other cases (e.g. AGAIN, or GOOD for a card already in REVIEW):
    // The canonical FSRS calculation is preserved directly, with current vocabularyStatus
    // preserved.
    return new SrsCalculationResult(
        canonical.srsState(),
        current.vocabularyStatus(),
        canonical.stability(),
        canonical.difficulty(),
        canonical.repetitions(),
        canonical.lapses(),
        canonical.nextReviewAt(),
        canonical.intervalSeconds());
  }

  public FsrsScheduler getScheduler() {
    return scheduler;
  }
}
