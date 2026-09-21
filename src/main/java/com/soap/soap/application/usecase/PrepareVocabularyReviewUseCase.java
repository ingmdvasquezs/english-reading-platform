package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.model.ReviewedWordDaySummary;
import com.soap.soap.application.model.VocabularyReviewItem;
import com.soap.soap.application.model.VocabularyReviewPreparation;
import com.soap.soap.application.port.in.PrepareVocabularyReviewPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserTimezonePort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyReviewHistoryRepositoryPort;
import com.soap.soap.application.port.out.VocabularyReviewSessionRepositoryPort;
import com.soap.soap.application.service.RatingOptionsBuilder;
import com.soap.soap.domain.model.ReviewSessionStatus;
import com.soap.soap.domain.model.SrsState;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyReviewSession;
import com.soap.soap.domain.model.VocabularyReviewSessionItem;
import com.soap.soap.domain.service.FsrsScheduler;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PrepareVocabularyReviewUseCase implements PrepareVocabularyReviewPort {
  public static final int DAILY_BASE_LIMIT = 15;

  private final CurrentUserPort currentUser;
  private final UserVocabularyRepositoryPort vocabulary;
  private final UserVocabularyReviewHistoryRepositoryPort historyRepository;
  private final VocabularyReviewSessionRepositoryPort sessionRepository;
  private final FsrsScheduler scheduler;
  private final Clock clock;
  private final UserTimezonePort userTimezonePort;
  private final RatingOptionsBuilder ratingOptionsBuilder;

  @Autowired
  public PrepareVocabularyReviewUseCase(
      CurrentUserPort currentUser,
      UserVocabularyRepositoryPort vocabulary,
      UserVocabularyReviewHistoryRepositoryPort historyRepository,
      VocabularyReviewSessionRepositoryPort sessionRepository,
      FsrsScheduler scheduler,
      Clock clock,
      UserTimezonePort userTimezonePort,
      RatingOptionsBuilder ratingOptionsBuilder) {
    this.currentUser = currentUser;
    this.vocabulary = vocabulary;
    this.historyRepository = historyRepository;
    this.sessionRepository = sessionRepository;
    this.scheduler = scheduler;
    this.clock = clock;
    this.userTimezonePort = userTimezonePort;
    this.ratingOptionsBuilder = ratingOptionsBuilder;
  }

  public PrepareVocabularyReviewUseCase(
      CurrentUserPort currentUser,
      UserVocabularyRepositoryPort vocabulary,
      UserVocabularyReviewHistoryRepositoryPort historyRepository,
      VocabularyReviewSessionRepositoryPort sessionRepository,
      FsrsScheduler scheduler,
      Clock clock,
      UserTimezonePort userTimezonePort) {
    this(
        currentUser,
        vocabulary,
        historyRepository,
        sessionRepository,
        scheduler,
        clock,
        userTimezonePort,
        new RatingOptionsBuilder(scheduler));
  }

  public PrepareVocabularyReviewUseCase(
      CurrentUserPort currentUser,
      UserVocabularyRepositoryPort vocabulary,
      UserVocabularyReviewHistoryRepositoryPort historyRepository,
      FsrsScheduler scheduler,
      Clock clock,
      UserTimezonePort userTimezonePort) {
    this(
        currentUser,
        vocabulary,
        historyRepository,
        createInMemorySessionRepository(),
        scheduler,
        clock,
        userTimezonePort,
        new RatingOptionsBuilder(scheduler));
  }

  public PrepareVocabularyReviewUseCase(
      CurrentUserPort currentUser,
      UserVocabularyRepositoryPort vocabulary,
      UserVocabularyReviewHistoryRepositoryPort historyRepository,
      FsrsScheduler scheduler,
      Clock clock) {
    this(
        currentUser,
        vocabulary,
        historyRepository,
        createInMemorySessionRepository(),
        scheduler,
        clock,
        userId -> ZoneOffset.UTC,
        new RatingOptionsBuilder(scheduler));
  }

  private static VocabularyReviewSessionRepositoryPort createInMemorySessionRepository() {
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
        // in-memory noop
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
  public VocabularyReviewPreparation prepareReview(int size) {
    if (size < 1 || size > 100) {
      throw new InvalidApplicationArgumentException("Review batch size must be between 1 and 100");
    }
    var userId = currentUser.requireUserId();
    var nowInstant = clock.instant();
    var nowUtc = LocalDateTime.ofInstant(nowInstant, ZoneOffset.UTC);

    // Resolve user's explicit ZoneId
    ZoneId userZoneId = userTimezonePort.resolveUserZoneId(userId);

    // CRITICAL: Calculate calendar day directly from Instant projected to userZoneId
    LocalDate localDate = nowInstant.atZone(userZoneId).toLocalDate();

    // Query general counts
    long dueCount = vocabulary.countDueWords(userId, nowUtc);
    long totalReviewable = vocabulary.countTotalReviewableWords(userId, nowUtc);

    // 1. Check if session exists for (userId, localDate)
    var sessionOpt = sessionRepository.findSession(userId, localDate);
    VocabularyReviewSession session;

    if (sessionOpt.isPresent()) {
      session = sessionOpt.get();
    } else {
      sessionRepository.acquireSessionCreationLock(userId);
      sessionOpt = sessionRepository.findSession(userId, localDate);
      if (sessionOpt.isPresent()) {
        session = sessionOpt.get();
      } else {
        LocalDateTime dayStartUtc =
            localDate
                .atStartOfDay(userZoneId)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        LocalDateTime dayEndUtc =
            localDate
                .plusDays(1)
                .atStartOfDay(userZoneId)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        var reviewedSummaries =
            historyRepository.findReviewedWordsSummaryBetween(userId, dayStartUtc, dayEndUtc);

        if (!reviewedSummaries.isEmpty()) {
          // Migration-day bootstrap from existing review history!
          var vocabIds =
              reviewedSummaries.stream().map(ReviewedWordDaySummary::userVocabularyId).toList();
          var reviewedVocabMap = vocabulary.findByIds(vocabIds);

          // Pending learning items ordered by lastReviewedAt ASC
          var pendingSummaries =
              reviewedSummaries.stream()
                  .filter(
                      s -> {
                        var uv = reviewedVocabMap.get(s.userVocabularyId());
                        return uv != null
                            && (uv.srsState() == SrsState.LEARNING
                                || uv.srsState() == SrsState.RELEARNING);
                      })
                  .sorted(Comparator.comparing(ReviewedWordDaySummary::lastReviewedAt))
                  .toList();

          Map<UUID, Long> pendingSeqMap = new HashMap<>();
          long seq = 1L;
          for (var ps : pendingSummaries) {
            pendingSeqMap.put(ps.userVocabularyId(), seq++);
          }
          long nextQueueSequence = seq;

          // Build session items for reviewed words ordered by firstReviewedAt ASC
          var items = new ArrayList<VocabularyReviewSessionItem>();
          UUID sessionId = UUID.randomUUID();
          int order = 1;
          Set<UUID> includedWordIds = new HashSet<>();
          for (var summary : reviewedSummaries) {
            var uv = reviewedVocabMap.get(summary.userVocabularyId());
            if (uv != null) {
              Long pendingQueueSeq = pendingSeqMap.get(summary.userVocabularyId());
              items.add(
                  new VocabularyReviewSessionItem(
                      UUID.randomUUID(),
                      sessionId,
                      uv,
                      order++,
                      summary.firstReviewedAt(),
                      pendingQueueSeq));
              includedWordIds.add(uv.word().id());
            }
          }

          // Remaining base capacity (up to 15)
          int remainingBaseCapacity = Math.max(0, DAILY_BASE_LIMIT - items.size());
          if (remainingBaseCapacity > 0) {
            var candidates = vocabulary.findReviewCandidates(userId, nowUtc, DAILY_BASE_LIMIT);
            var additionalCandidates =
                candidates.stream()
                    .filter(cand -> !includedWordIds.contains(cand.word().id()))
                    .limit(remainingBaseCapacity)
                    .toList();
            for (var cand : additionalCandidates) {
              items.add(
                  new VocabularyReviewSessionItem(
                      UUID.randomUUID(), sessionId, cand, order++, null, null));
            }
          }

          boolean allIntroduced = items.stream().allMatch(it -> it.introducedAt() != null);
          boolean hasLearning =
              items.stream()
                  .anyMatch(
                      it ->
                          it.vocabulary().srsState() == SrsState.LEARNING
                              || it.vocabulary().srsState() == SrsState.RELEARNING);
          boolean hasPending = items.stream().anyMatch(it -> it.pendingQueueSequence() != null);
          ReviewSessionStatus initialStatus =
              (allIntroduced && !hasLearning && !hasPending)
                  ? ReviewSessionStatus.COMPLETED
                  : ReviewSessionStatus.ACTIVE;
          LocalDateTime completedAt =
              initialStatus == ReviewSessionStatus.COMPLETED ? nowUtc : null;

          var newSession =
              new VocabularyReviewSession(
                  sessionId,
                  userId,
                  localDate,
                  initialStatus,
                  DAILY_BASE_LIMIT,
                  nextQueueSequence,
                  nowUtc,
                  completedAt,
                  items);
          session = sessionRepository.saveSession(newSession);
        } else {
          // Fresh day: no reviews today yet
          var candidates = vocabulary.findReviewCandidates(userId, nowUtc, DAILY_BASE_LIMIT);
          if (candidates.isEmpty()) {
            int pendingLearningCount = (int) vocabulary.countPendingLearningWords(userId);
            boolean dailyComplete = (totalReviewable == 0) && (pendingLearningCount == 0);
            return new VocabularyReviewPreparation(
                dueCount,
                totalReviewable,
                DAILY_BASE_LIMIT,
                0,
                0,
                pendingLearningCount,
                dailyComplete,
                List.of(),
                List.of());
          }

          var items = new ArrayList<VocabularyReviewSessionItem>();
          int order = 1;
          UUID sessionId = UUID.randomUUID();
          for (var cand : candidates) {
            items.add(
                new VocabularyReviewSessionItem(
                    UUID.randomUUID(), sessionId, cand, order++, null, null));
          }
          var newSession =
              new VocabularyReviewSession(
                  sessionId,
                  userId,
                  localDate,
                  ReviewSessionStatus.ACTIVE,
                  DAILY_BASE_LIMIT,
                  1L,
                  nowUtc,
                  null,
                  items);
          session = sessionRepository.saveSession(newSession);
        }
      }
    }

    // 2. Handle COMPLETED session
    if (session.status() == ReviewSessionStatus.COMPLETED) {
      int cohortSize = session.items().size();
      return new VocabularyReviewPreparation(
          0L, totalReviewable, session.dailyLimit(), cohortSize, 0, 0, true, List.of(), List.of());
    }

    // 3. Active session: refresh latest UserVocabulary for session items
    var wordIds = session.items().stream().map(it -> it.vocabulary().word().id()).toList();
    Map<UUID, UserVocabulary> latestVocabMap = vocabulary.findByUserIdAndWordIds(userId, wordIds);

    var refreshedItems = new ArrayList<VocabularyReviewSessionItem>();
    for (var item : session.items()) {
      var latest = latestVocabMap.get(item.vocabulary().word().id());
      refreshedItems.add(latest != null ? item.withVocabulary(latest) : item);
    }
    session = session.withItems(refreshedItems);

    int cohortSize = session.items().size();
    int dailyBaseCompleted =
        (int) session.items().stream().filter(it -> it.introducedAt() != null).count();
    int dailyBaseRemaining = Math.max(0, cohortSize - dailyBaseCompleted);
    int pendingLearningCount =
        (int)
            session.items().stream()
                .filter(
                    it ->
                        it.vocabulary().srsState() == SrsState.LEARNING
                            || it.vocabulary().srsState() == SrsState.RELEARNING)
                .count();

    // Check completion condition:
    // A. All base items introduced (dailyBaseRemaining == 0)
    // AND B. No item has SrsState in LEARNING or RELEARNING
    // AND C. No item has pendingQueueSequence != null
    boolean hasLearningState =
        session.items().stream()
            .anyMatch(
                it ->
                    it.vocabulary().srsState() == SrsState.LEARNING
                        || it.vocabulary().srsState() == SrsState.RELEARNING);
    boolean hasPendingQueue =
        session.items().stream().anyMatch(it -> it.pendingQueueSequence() != null);

    if (dailyBaseRemaining == 0 && !hasLearningState && !hasPendingQueue) {
      session = session.withStatus(ReviewSessionStatus.COMPLETED, nowUtc);
      sessionRepository.saveSession(session);
      return new VocabularyReviewPreparation(
          0L, totalReviewable, session.dailyLimit(), cohortSize, 0, 0, true, List.of(), List.of());
    }

    // Partition into unreviewed base cards and pending learning cards
    var unreviewedBase =
        session.items().stream()
            .filter(it -> it.introducedAt() == null)
            .sorted(Comparator.comparingInt(VocabularyReviewSessionItem::baseOrder))
            .toList();

    var pendingLearning =
        session.items().stream()
            .filter(
                it ->
                    it.pendingQueueSequence() != null
                        && (it.vocabulary().srsState() == SrsState.LEARNING
                            || it.vocabulary().srsState() == SrsState.RELEARNING))
            .sorted(Comparator.comparingLong(VocabularyReviewSessionItem::pendingQueueSequence))
            .toList();

    LocalDateTime maxLearnAheadUtc = nowUtc.plusMinutes(20);

    List<VocabularyReviewItem> entries = new ArrayList<>();
    List<VocabularyReviewItem> learnAheadEntries = new ArrayList<>();

    if (!unreviewedBase.isEmpty()) {
      // Base phase: present unreviewed base cards by baseOrder ASC
      int takeCount = Math.min(size, unreviewedBase.size());
      for (int i = 0; i < takeCount; i++) {
        var item = unreviewedBase.get(i);
        var uv = item.vocabulary();
        entries.add(
            new VocabularyReviewItem(
                uv.word().id(),
                uv.word().normalizedValue(),
                uv.word().language(),
                uv.status(),
                uv.srsState(),
                ratingOptionsBuilder.buildRatingOptions(uv, nowUtc),
                null,
                item.baseOrder()));
      }

      // Any pending learning cards within 20m learn-ahead go to learnAheadEntries
      for (var item : pendingLearning) {
        var uv = item.vocabulary();
        if (uv.nextReviewAt() != null && !uv.nextReviewAt().isAfter(maxLearnAheadUtc)) {
          learnAheadEntries.add(
              new VocabularyReviewItem(
                  uv.word().id(),
                  uv.word().normalizedValue(),
                  uv.word().language(),
                  uv.status(),
                  uv.srsState(),
                  ratingOptionsBuilder.buildRatingOptions(uv, nowUtc),
                  item.pendingQueueSequence(),
                  item.baseOrder()));
        }
      }
    } else {
      // Pending phase: all base cards have been introduced!
      // Eligible cards: nextReviewAt <= now + 20m, ordered strictly by pendingQueueSequence ASC
      // (FIFO)
      var eligiblePending =
          pendingLearning.stream()
              .filter(
                  it ->
                      it.vocabulary().nextReviewAt() == null
                          || !it.vocabulary().nextReviewAt().isAfter(maxLearnAheadUtc))
              .toList();

      for (var item : eligiblePending) {
        var uv = item.vocabulary();
        var reviewItem =
            new VocabularyReviewItem(
                uv.word().id(),
                uv.word().normalizedValue(),
                uv.word().language(),
                uv.status(),
                uv.srsState(),
                ratingOptionsBuilder.buildRatingOptions(uv, nowUtc),
                item.pendingQueueSequence(),
                item.baseOrder());

        if (uv.nextReviewAt() != null && uv.nextReviewAt().isAfter(nowUtc)) {
          learnAheadEntries.add(reviewItem);
        } else {
          entries.add(reviewItem);
        }
      }
    }

    return new VocabularyReviewPreparation(
        dueCount,
        totalReviewable,
        session.dailyLimit(),
        dailyBaseCompleted,
        dailyBaseRemaining,
        pendingLearningCount,
        false,
        entries,
        learnAheadEntries);
  }
}
