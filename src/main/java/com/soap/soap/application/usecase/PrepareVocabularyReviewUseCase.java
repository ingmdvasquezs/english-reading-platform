package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.model.ReviewRatingOption;
import com.soap.soap.application.model.VocabularyReviewItem;
import com.soap.soap.application.model.VocabularyReviewPreparation;
import com.soap.soap.application.port.in.PrepareVocabularyReviewPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.service.FsrsScheduler;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PrepareVocabularyReviewUseCase implements PrepareVocabularyReviewPort {
  private final CurrentUserPort currentUser;
  private final UserVocabularyRepositoryPort vocabulary;
  private final FsrsScheduler scheduler;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public VocabularyReviewPreparation prepareReview(int size) {
    if (size < 1 || size > 100) {
      throw new InvalidApplicationArgumentException("Review batch size must be between 1 and 100");
    }
    var userId = currentUser.requireUserId();
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var dueCount = vocabulary.countDueWords(userId, nowUtc);
    var totalReviewable = vocabulary.countTotalReviewableWords(userId, nowUtc);
    var candidates = vocabulary.findReviewCandidates(userId, nowUtc, size);

    var items =
        candidates.stream()
            .map(
                uv -> {
                  var ratingOptions =
                      List.of(
                              ReviewRating.AGAIN,
                              ReviewRating.HARD,
                              ReviewRating.GOOD,
                              ReviewRating.EASY)
                          .stream()
                          .map(
                              rating -> {
                                var calc =
                                    scheduler.calculateNextState(
                                        uv.toSrsParameters(), rating, nowUtc);
                                return new ReviewRatingOption(
                                    rating, calc.nextReviewAt(), calc.intervalSeconds());
                              })
                          .toList();
                  return new VocabularyReviewItem(
                      uv.word().id(),
                      uv.word().normalizedValue(),
                      uv.word().language(),
                      uv.status(),
                      uv.srsState(),
                      ratingOptions);
                })
            .toList();

    return new VocabularyReviewPreparation(dueCount, totalReviewable, items);
  }
}
