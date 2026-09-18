package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.VocabularyEntryNotFoundException;
import com.soap.soap.application.model.VocabularyReviewHistoryEntry;
import com.soap.soap.application.port.in.RecordVocabularyReviewPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyReviewHistoryRepositoryPort;
import com.soap.soap.domain.model.ReviewAssessment;
import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.service.FsrsScheduler;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecordVocabularyReviewUseCase implements RecordVocabularyReviewPort {
  private final CurrentUserPort currentUser;
  private final UserVocabularyRepositoryPort vocabulary;
  private final UserVocabularyReviewHistoryRepositoryPort historyRepository;
  private final FsrsScheduler scheduler;
  private final Clock clock;

  @Override
  @Transactional
  public UserVocabulary recordReview(UUID wordId, ReviewRating rating) {
    if (wordId == null) {
      throw new InvalidApplicationArgumentException("Word ID must not be null");
    }
    if (rating == null) {
      throw new InvalidApplicationArgumentException("Review rating must not be null");
    }
    var userId = currentUser.requireUserId();
    var current =
        vocabulary
            .findByUserIdAndWordId(userId, wordId)
            .orElseThrow(() -> new VocabularyEntryNotFoundException(userId, wordId));

    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

    long prevIntervalSeconds = 0L;
    if (current.lastReviewedAt() != null && current.nextReviewAt() != null) {
      prevIntervalSeconds =
          Math.max(
              0L, Duration.between(current.lastReviewedAt(), current.nextReviewAt()).toSeconds());
    } else if (current.nextReviewAt() != null && current.firstSeenAt() != null) {
      prevIntervalSeconds =
          Math.max(0L, Duration.between(current.firstSeenAt(), current.nextReviewAt()).toSeconds());
    }

    double elapsedDays = 0.0;
    if (current.lastReviewedAt() != null) {
      long elapsedSeconds = Duration.between(current.lastReviewedAt(), nowUtc).toSeconds();
      elapsedDays = Math.max(0.0, elapsedSeconds / 86400.0);
    }
    double scheduledDays = prevIntervalSeconds / 86400.0;

    var updated = current.applyRating(rating, clock, scheduler);
    var saved = vocabulary.save(updated);

    long newIntervalSeconds = 0L;
    if (saved.nextReviewAt() != null) {
      newIntervalSeconds = Math.max(0L, Duration.between(nowUtc, saved.nextReviewAt()).toSeconds());
    }

    var historyEntry =
        new VocabularyReviewHistoryEntry(
            UUID.randomUUID(),
            saved.id(),
            userId,
            nowUtc,
            rating,
            current.srsState(),
            saved.srsState(),
            prevIntervalSeconds,
            newIntervalSeconds,
            current.stability(),
            saved.stability(),
            current.difficulty(),
            saved.difficulty(),
            elapsedDays,
            scheduledDays);

    historyRepository.recordReviewHistory(historyEntry);

    return saved;
  }

  @Override
  @Transactional
  public UserVocabulary recordReview(UUID wordId, ReviewAssessment assessment) {
    if (assessment == null) {
      throw new InvalidApplicationArgumentException("Review assessment must not be null");
    }
    ReviewRating rating =
        switch (assessment) {
          case FORGOT -> ReviewRating.AGAIN;
          case STRUGGLED -> ReviewRating.HARD;
          case REMEMBERED -> ReviewRating.GOOD;
        };
    return recordReview(wordId, rating);
  }
}
