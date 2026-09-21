package com.soap.soap.application.service;

import com.soap.soap.application.model.ReviewRatingOption;
import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.service.FsrsScheduler;
import com.soap.soap.domain.service.VocabularyReviewSchedulingPolicy;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Shared engine service to build fresh binary preview rating options (AGAIN, GOOD) using the
 * VocabularyReviewSchedulingPolicy overlay on canonical FSRS-4.5.
 */
@Component
public class RatingOptionsBuilder {

  private final VocabularyReviewSchedulingPolicy policy;

  @org.springframework.beans.factory.annotation.Autowired
  public RatingOptionsBuilder(VocabularyReviewSchedulingPolicy policy) {
    this.policy = policy;
  }

  public RatingOptionsBuilder(FsrsScheduler scheduler) {
    this(new VocabularyReviewSchedulingPolicy(scheduler));
  }

  public List<ReviewRatingOption> buildRatingOptions(
      UserVocabulary vocabulary, LocalDateTime nowUtc) {
    if (vocabulary == null) {
      return List.of();
    }
    var params = vocabulary.toSrsParameters();
    return List.of(ReviewRating.AGAIN, ReviewRating.GOOD).stream()
        .map(
            rating -> {
              var calc = policy.calculateNextState(params, rating, nowUtc);
              return new ReviewRatingOption(rating, calc.nextReviewAt(), calc.intervalSeconds());
            })
        .toList();
  }
}
