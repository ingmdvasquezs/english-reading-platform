package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.VocabularyEntryNotFoundException;
import com.soap.soap.application.model.ReviewRatingOption;
import com.soap.soap.application.model.VocabularyReviewHistoryEntry;
import com.soap.soap.application.model.VocabularyReviewRecordResult;
import com.soap.soap.application.port.in.RecordVocabularyReviewPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserTimezonePort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyReviewHistoryRepositoryPort;
import com.soap.soap.application.port.out.VocabularyReviewSessionRepositoryPort;
import com.soap.soap.application.service.RatingOptionsBuilder;
import com.soap.soap.domain.model.ReviewAssessment;
import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.model.ReviewSessionStatus;
import com.soap.soap.domain.model.SrsState;
import com.soap.soap.domain.model.VocabularyReviewSession;
import com.soap.soap.domain.model.VocabularyReviewSessionItem;
import com.soap.soap.domain.service.FsrsScheduler;
import com.soap.soap.domain.service.VocabularyReviewSchedulingPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RecordVocabularyReviewUseCase implements RecordVocabularyReviewPort {
  private final CurrentUserPort currentUser;
  private final UserVocabularyRepositoryPort vocabulary;
  private final UserVocabularyReviewHistoryRepositoryPort historyRepository;
  private final VocabularyReviewSessionRepositoryPort sessionRepository;
  private final UserTimezonePort userTimezonePort;
  private final FsrsScheduler scheduler;
  private final VocabularyReviewSchedulingPolicy policy;
  private final Clock clock;
  private final RatingOptionsBuilder ratingOptionsBuilder;

  @Autowired
  public RecordVocabularyReviewUseCase(
      CurrentUserPort currentUser,
      UserVocabularyRepositoryPort vocabulary,
      UserVocabularyReviewHistoryRepositoryPort historyRepository,
      VocabularyReviewSessionRepositoryPort sessionRepository,
      UserTimezonePort userTimezonePort,
      FsrsScheduler scheduler,
      VocabularyReviewSchedulingPolicy policy,
      Clock clock,
      RatingOptionsBuilder ratingOptionsBuilder) {
    this.currentUser = currentUser;
    this.vocabulary = vocabulary;
    this.historyRepository = historyRepository;
    this.sessionRepository = sessionRepository;
    this.userTimezonePort = userTimezonePort;
    this.scheduler = scheduler;
    this.policy = policy != null ? policy : new VocabularyReviewSchedulingPolicy(scheduler);
    this.clock = clock;
    this.ratingOptionsBuilder = ratingOptionsBuilder;
  }

  public RecordVocabularyReviewUseCase(
      CurrentUserPort currentUser,
      UserVocabularyRepositoryPort vocabulary,
      UserVocabularyReviewHistoryRepositoryPort historyRepository,
      VocabularyReviewSessionRepositoryPort sessionRepository,
      UserTimezonePort userTimezonePort,
      FsrsScheduler scheduler,
      Clock clock,
      RatingOptionsBuilder ratingOptionsBuilder) {
    this(
        currentUser,
        vocabulary,
        historyRepository,
        sessionRepository,
        userTimezonePort,
        scheduler,
        new VocabularyReviewSchedulingPolicy(scheduler),
        clock,
        ratingOptionsBuilder);
  }

  public RecordVocabularyReviewUseCase(
      CurrentUserPort currentUser,
      UserVocabularyRepositoryPort vocabulary,
      UserVocabularyReviewHistoryRepositoryPort historyRepository,
      VocabularyReviewSessionRepositoryPort sessionRepository,
      UserTimezonePort userTimezonePort,
      FsrsScheduler scheduler,
      Clock clock) {
    this(
        currentUser,
        vocabulary,
        historyRepository,
        sessionRepository,
        userTimezonePort,
        scheduler,
        new VocabularyReviewSchedulingPolicy(scheduler),
        clock,
        new RatingOptionsBuilder(scheduler));
  }

  public RecordVocabularyReviewUseCase(
      CurrentUserPort currentUser,
      UserVocabularyRepositoryPort vocabulary,
      UserVocabularyReviewHistoryRepositoryPort historyRepository,
      FsrsScheduler scheduler,
      Clock clock) {
    this(
        currentUser,
        vocabulary,
        historyRepository,
        createNoopSessionRepository(),
        userId -> ZoneOffset.UTC,
        scheduler,
        new VocabularyReviewSchedulingPolicy(scheduler),
        clock,
        new RatingOptionsBuilder(scheduler));
  }

  private static VocabularyReviewSessionRepositoryPort createNoopSessionRepository() {
    return new VocabularyReviewSessionRepositoryPort() {
      private final Map<String, VocabularyReviewSession> store = new ConcurrentHashMap<>();

      @Override
      public Optional<VocabularyReviewSession> findSession(UUID userId, LocalDate localDate) {
        return Optional.ofNullable(store.get(userId + ":" + localDate));
      }

      @Override
      public Optional<VocabularyReviewSession> findSessionForUpdate(
          UUID userId, LocalDate localDate) {
        return findSession(userId, localDate);
      }

      @Override
      public void acquireSessionCreationLock(UUID userId) {
        // no-op
      }

      @Override
      public VocabularyReviewSession saveSession(VocabularyReviewSession session) {
        store.put(session.userId() + ":" + session.localReviewDate(), session);
        return session;
      }
    };
  }

  @Override
  @Transactional
  public VocabularyReviewRecordResult recordReview(UUID wordId, ReviewRating rating) {
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

    var nowInstant = clock.instant();
    var nowUtc = LocalDateTime.ofInstant(nowInstant, ZoneOffset.UTC);
    var userZoneId = userTimezonePort.resolveUserZoneId(userId);
    var localDate = nowInstant.atZone(userZoneId).toLocalDate();

    long prevIntervalSeconds = 0L;
    if (current.lastReviewedAt() != null && current.nextReviewAt() != null) {
      prevIntervalSeconds =
          Math.max(
              0L, durationBetweenUtc(current.lastReviewedAt(), current.nextReviewAt()).toSeconds());
    } else if (current.nextReviewAt() != null && current.firstSeenAt() != null) {
      prevIntervalSeconds =
          Math.max(
              0L, durationBetweenUtc(current.firstSeenAt(), current.nextReviewAt()).toSeconds());
    }

    double elapsedDays = 0.0;
    if (current.lastReviewedAt() != null) {
      long elapsedSeconds = durationBetweenUtc(current.lastReviewedAt(), nowUtc).toSeconds();
      elapsedDays = Math.max(0.0, elapsedSeconds / 86400.0);
    }
    double scheduledDays = prevIntervalSeconds / 86400.0;

    var updated = current.applyRating(rating, clock, policy);
    var saved = vocabulary.save(updated);

    long newIntervalSeconds = 0L;
    if (saved.nextReviewAt() != null) {
      newIntervalSeconds =
          Math.max(0L, durationBetweenUtc(nowUtc, saved.nextReviewAt()).toSeconds());
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

    Long resultPendingQueueSequence = null;
    Integer resultBaseOrder = null;

    // Update session state if session exists for today
    var sessionOpt = sessionRepository.findSessionForUpdate(userId, localDate);
    if (sessionOpt.isPresent()) {
      var session = sessionOpt.get();
      var itemOpt =
          session.items().stream()
              .filter(
                  it ->
                      it.vocabulary().word().id().equals(wordId)
                          || it.vocabulary().id().equals(saved.id()))
              .findFirst();

      if (itemOpt.isPresent()) {
        resultBaseOrder = itemOpt.get().baseOrder();
      }

      if (session.status() == ReviewSessionStatus.ACTIVE && itemOpt.isPresent()) {
        var item = itemOpt.get();
        LocalDateTime introducedAt = item.introducedAt() != null ? item.introducedAt() : nowUtc;

        Long pendingQueueSeq;
        long nextSeq = session.nextQueueSequence();
        if (saved.srsState() == SrsState.LEARNING || saved.srsState() == SrsState.RELEARNING) {
          // Assign tail sequence and increment session next_queue_sequence
          pendingQueueSeq = nextSeq;
          nextSeq = nextSeq + 1;
        } else {
          // REVIEW state: graduated, remove from pending queue
          pendingQueueSeq = null;
        }
        resultPendingQueueSequence = pendingQueueSeq;

        var updatedItem =
            item.withIntroducedAt(introducedAt)
                .withPendingQueueSequence(pendingQueueSeq)
                .withVocabulary(saved);

        List<VocabularyReviewSessionItem> updatedItems = new ArrayList<>();
        for (var it : session.items()) {
          if (it.id().equals(updatedItem.id())) {
            updatedItems.add(updatedItem);
          } else {
            updatedItems.add(it);
          }
        }

        session = session.withNextQueueSequence(nextSeq).withItems(updatedItems);

        // Check completion conditions:
        // A. All session items: introducedAt != null
        // B. No session item has current SrsState in LEARNING, RELEARNING
        // C. No session item has pendingQueueSequence != null
        boolean allIntroduced = session.items().stream().allMatch(it -> it.introducedAt() != null);
        boolean hasLearningState =
            session.items().stream()
                .anyMatch(
                    it ->
                        it.vocabulary().srsState() == SrsState.LEARNING
                            || it.vocabulary().srsState() == SrsState.RELEARNING);
        boolean hasPendingQueueSeq =
            session.items().stream().anyMatch(it -> it.pendingQueueSequence() != null);

        if (allIntroduced && !hasLearningState && !hasPendingQueueSeq) {
          session = session.withStatus(ReviewSessionStatus.COMPLETED, nowUtc);
        }

        sessionRepository.saveSession(session);
      }
    }

    // Build fresh rating options for the new state
    List<ReviewRatingOption> freshRatingOptions =
        ratingOptionsBuilder.buildRatingOptions(saved, nowUtc);

    return new VocabularyReviewRecordResult(
        saved, freshRatingOptions, resultPendingQueueSequence, resultBaseOrder);
  }

  @Override
  @Transactional
  public VocabularyReviewRecordResult recordReview(UUID wordId, ReviewAssessment assessment) {
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

  private static Duration durationBetweenUtc(LocalDateTime start, LocalDateTime end) {
    return Duration.between(start.atOffset(ZoneOffset.UTC), end.atOffset(ZoneOffset.UTC));
  }
}
