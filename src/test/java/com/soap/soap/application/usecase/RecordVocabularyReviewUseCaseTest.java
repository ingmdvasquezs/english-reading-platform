package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.VocabularyEntryNotFoundException;
import com.soap.soap.application.model.VocabularyReviewHistoryEntry;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyReviewHistoryRepositoryPort;
import com.soap.soap.domain.model.ReviewAssessment;
import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.model.SrsState;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import com.soap.soap.domain.service.FsrsScheduler;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordVocabularyReviewUseCaseTest {
  @Mock private CurrentUserPort currentUser;
  @Mock private UserVocabularyRepositoryPort repository;
  @Mock private UserVocabularyReviewHistoryRepositoryPort historyRepository;

  private final Clock clock = Clock.fixed(Instant.parse("2026-09-11T12:00:00Z"), ZoneOffset.UTC);
  private final FsrsScheduler scheduler = new FsrsScheduler();
  private RecordVocabularyReviewUseCase useCase;
  private final UUID userId = UUID.randomUUID();
  private final UUID wordId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    useCase =
        new RecordVocabularyReviewUseCase(
            currentUser, repository, historyRepository, scheduler, clock);
  }

  @Test
  void rejectsNullArguments() {
    assertThatThrownBy(() -> useCase.recordReview(null, ReviewRating.AGAIN))
        .isInstanceOf(InvalidApplicationArgumentException.class);
    assertThatThrownBy(() -> useCase.recordReview(wordId, (ReviewRating) null))
        .isInstanceOf(InvalidApplicationArgumentException.class);
  }

  @Test
  void throwsWhenVocabularyEntryDoesNotExistOrBelongsToAnotherUser() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(repository.findByUserIdAndWordId(userId, wordId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.recordReview(wordId, ReviewRating.GOOD))
        .isInstanceOf(VocabularyEntryNotFoundException.class);
  }

  @Test
  void recordsReviewAndSavesUpdatedEntryWithHistory() {
    when(currentUser.requireUserId()).thenReturn(userId);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var user = new User(userId, "Ada", "ada@example.com");
    var word = new Word(wordId, "would", "en");
    var current =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(3),
            null,
            0L,
            0,
            null,
            nowUtc,
            SrsState.LEARNING,
            0.4872,
            7.6214,
            1,
            0);

    when(repository.findByUserIdAndWordId(userId, wordId)).thenReturn(Optional.of(current));
    when(repository.save(any(UserVocabulary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = useCase.recordReview(wordId, ReviewRating.GOOD);

    assertThat(result.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(result.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(result.nextReviewAt()).isEqualTo(nowUtc.plusDays(1));
    assertThat(result.lastReviewedAt()).isEqualTo(nowUtc);
    assertThat(result.learnedAt()).isNull();
    assertThat(result.repetitions()).isEqualTo(2);

    verify(repository).save(any(UserVocabulary.class));
    verify(historyRepository).recordReviewHistory(any(VocabularyReviewHistoryEntry.class));
  }

  @Test
  void recordsReviewWithLegacyAssessmentFallback() {
    when(currentUser.requireUserId()).thenReturn(userId);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var user = new User(userId, "Ada", "ada@example.com");
    var word = new Word(wordId, "would", "en");
    var current =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(3),
            null,
            0L,
            0,
            null,
            nowUtc,
            SrsState.LEARNING,
            0.4872,
            7.6214,
            1,
            0);

    when(repository.findByUserIdAndWordId(userId, wordId)).thenReturn(Optional.of(current));
    when(repository.save(any(UserVocabulary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = useCase.recordReview(wordId, ReviewAssessment.FORGOT);

    assertThat(result.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(result.srsState()).isEqualTo(SrsState.LEARNING);
    assertThat(result.nextReviewAt()).isEqualTo(nowUtc.plusSeconds(600L));
    assertThat(result.lastReviewedAt()).isEqualTo(nowUtc);

    verify(repository).save(any(UserVocabulary.class));
    verify(historyRepository).recordReviewHistory(any(VocabularyReviewHistoryEntry.class));
  }

  @Test
  @DisplayName(
      "14.3.3.2 - A: REVIEW + AGAIN returns RELEARNING with fresh rating options (10m, 15m, 1d, 2d)")
  void testReviewAgainReturnsRelearningWithFreshOptions() {
    when(currentUser.requireUserId()).thenReturn(userId);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var user = new User(userId, "Ada", "ada@example.com");
    var word = new Word(wordId, "targetWord", "en");

    // Mature card in REVIEW state
    var matureCard =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.KNOWN,
            nowUtc.minusDays(30),
            nowUtc.minusDays(10),
            864000L,
            4,
            nowUtc.minusDays(10),
            nowUtc,
            SrsState.REVIEW,
            14.0,
            4.0,
            4,
            0);

    when(repository.findByUserIdAndWordId(userId, wordId)).thenReturn(Optional.of(matureCard));
    when(repository.save(any(UserVocabulary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = useCase.recordReview(wordId, ReviewRating.AGAIN);

    // Assert updated state
    assertThat(result.srsState()).isEqualTo(SrsState.RELEARNING);
    assertThat(result.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(result.nextReviewAt()).isEqualTo(nowUtc.plusMinutes(10));

    // Assert fresh rating options for the new RELEARNING state (binary review: AGAIN, GOOD)
    assertThat(result.ratingOptions()).hasSize(2);
    var againOpt =
        result.ratingOptions().stream()
            .filter(o -> o.rating() == ReviewRating.AGAIN)
            .findFirst()
            .orElseThrow();
    var goodOpt =
        result.ratingOptions().stream()
            .filter(o -> o.rating() == ReviewRating.GOOD)
            .findFirst()
            .orElseThrow();

    assertThat(againOpt.intervalSeconds()).isEqualTo(600L); // 10m
    assertThat(goodOpt.intervalSeconds()).isEqualTo(86400L); // 1d recovery
  }

  @Test
  @DisplayName("14.3.3.2 - B: RELEARNING + AGAIN returns four fresh rating options again")
  void testRelearningAgainReturnsFreshOptionsAgain() {
    when(currentUser.requireUserId()).thenReturn(userId);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var user = new User(userId, "Ada", "ada@example.com");
    var word = new Word(wordId, "targetWord", "en");

    var inRelearning =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(30),
            null,
            0L,
            4,
            nowUtc.minusMinutes(10),
            nowUtc,
            SrsState.RELEARNING,
            1.5,
            5.0,
            4,
            1);

    when(repository.findByUserIdAndWordId(userId, wordId)).thenReturn(Optional.of(inRelearning));
    when(repository.save(any(UserVocabulary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = useCase.recordReview(wordId, ReviewRating.AGAIN);

    assertThat(result.srsState()).isEqualTo(SrsState.RELEARNING);
    assertThat(result.ratingOptions()).hasSize(2);
    var againOpt =
        result.ratingOptions().stream()
            .filter(o -> o.rating() == ReviewRating.AGAIN)
            .findFirst()
            .orElseThrow();
    var goodOpt =
        result.ratingOptions().stream()
            .filter(o -> o.rating() == ReviewRating.GOOD)
            .findFirst()
            .orElseThrow();

    assertThat(againOpt.intervalSeconds()).isEqualTo(600L);
    assertThat(goodOpt.intervalSeconds()).isEqualTo(86400L);
  }

  @Test
  @DisplayName(
      "14.3.3.2 - C: RELEARNING + GOOD transitions to REVIEW and fresh rating options calculated from REVIEW")
  void testRelearningGoodReturnsReviewStateWithReviewOptions() {
    when(currentUser.requireUserId()).thenReturn(userId);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var user = new User(userId, "Ada", "ada@example.com");
    var word = new Word(wordId, "targetWord", "en");

    var inRelearning =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(30),
            null,
            0L,
            4,
            nowUtc.minusMinutes(10),
            nowUtc,
            SrsState.RELEARNING,
            2.0,
            4.5,
            4,
            1);

    when(repository.findByUserIdAndWordId(userId, wordId)).thenReturn(Optional.of(inRelearning));
    when(repository.save(any(UserVocabulary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = useCase.recordReview(wordId, ReviewRating.GOOD);

    assertThat(result.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(result.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(result.nextReviewAt()).isEqualTo(nowUtc.plusDays(1)); // graduated to +1d

    // Options from the new REVIEW state:
    // In REVIEW, GOOD interval is based on FSRS stability (> 1d), NOT 1d fixed!
    assertThat(result.ratingOptions()).hasSize(2);
    var goodOpt =
        result.ratingOptions().stream()
            .filter(o -> o.rating() == ReviewRating.GOOD)
            .findFirst()
            .orElseThrow();
    assertThat(goodOpt.intervalSeconds())
        .isGreaterThan(86400L); // FSRS calculated interval for mature card
  }

  @Test
  @DisplayName("14.3.3.2 - D: record response options are NOT the previous card options")
  void testRecordResponseOptionsAreNotPreviousOptions() {
    when(currentUser.requireUserId()).thenReturn(userId);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var user = new User(userId, "Ada", "ada@example.com");
    var word = new Word(wordId, "targetWord", "en");

    // In REVIEW state, previous GOOD option was e.g. 14 days
    var matureCard =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.KNOWN,
            nowUtc.minusDays(60),
            nowUtc.minusDays(14),
            1209600L,
            5,
            nowUtc.minusDays(14),
            nowUtc,
            SrsState.REVIEW,
            25.0,
            3.5,
            5,
            0);

    when(repository.findByUserIdAndWordId(userId, wordId)).thenReturn(Optional.of(matureCard));
    when(repository.save(any(UserVocabulary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Previous options for matureCard in REVIEW would have GOOD = ~30+ days
    var previousGoodCalc =
        scheduler.calculateNextState(matureCard.toSrsParameters(), ReviewRating.GOOD, nowUtc);
    assertThat(previousGoodCalc.intervalSeconds()).isGreaterThan(1000000L);

    var result = useCase.recordReview(wordId, ReviewRating.AGAIN);

    // After lapse into RELEARNING, the new options MUST NOT match the old REVIEW options!
    var newGoodOpt =
        result.ratingOptions().stream()
            .filter(o -> o.rating() == ReviewRating.GOOD)
            .findFirst()
            .orElseThrow();
    assertThat(newGoodOpt.intervalSeconds()).isEqualTo(86400L); // 1d in relearning, NOT 30+ days!
    assertThat(newGoodOpt.intervalSeconds()).isNotEqualTo(previousGoodCalc.intervalSeconds());
  }

  // =========================================================================
  // FASE 14.3.5: Queue Sequence, Completion & Hexagonal Architecture Tests
  // =========================================================================

  @Test
  @DisplayName(
      "14.3.5 - K: Record AGAIN assigns tail sequence and increments session nextQueueSequence")
  void testRecordAgainAssignsTailSequence() {
    var sessionRepo =
        org.mockito.Mockito.mock(
            com.soap.soap.application.port.out.VocabularyReviewSessionRepositoryPort.class);
    var tzPort =
        org.mockito.Mockito.mock(com.soap.soap.application.port.out.UserTimezonePort.class);
    when(tzPort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);

    var useCaseWithSession =
        new RecordVocabularyReviewUseCase(
            currentUser, repository, historyRepository, sessionRepo, tzPort, scheduler, clock);

    when(currentUser.requireUserId()).thenReturn(userId);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var user = new User(userId, "Ada", "ada@example.com");
    var word = new Word(wordId, "targetWord", "en");

    var matureCard =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.KNOWN,
            nowUtc.minusDays(30),
            nowUtc.minusDays(10),
            864000L,
            4,
            nowUtc.minusDays(10),
            nowUtc,
            SrsState.REVIEW,
            14.0,
            4.0,
            4,
            0);

    when(repository.findByUserIdAndWordId(userId, wordId)).thenReturn(Optional.of(matureCard));
    when(repository.save(any(UserVocabulary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    UUID sessionId = UUID.randomUUID();
    var sessionItem =
        new com.soap.soap.domain.model.VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, matureCard, 1, null, null);
    var activeSession =
        new com.soap.soap.domain.model.VocabularyReviewSession(
            sessionId,
            userId,
            LocalDate.parse("2026-09-11"),
            com.soap.soap.domain.model.ReviewSessionStatus.ACTIVE,
            15,
            5L, // current nextQueueSequence is 5
            nowUtc.minusHours(1),
            null,
            List.of(sessionItem));

    when(sessionRepo.findSessionForUpdate(eq(userId), any()))
        .thenReturn(Optional.of(activeSession));
    var captor =
        org.mockito.ArgumentCaptor.forClass(
            com.soap.soap.domain.model.VocabularyReviewSession.class);
    when(sessionRepo.saveSession(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    useCaseWithSession.recordReview(wordId, ReviewRating.AGAIN);

    var savedSession = captor.getValue();
    assertThat(savedSession.nextQueueSequence()).isEqualTo(6L); // incremented from 5 to 6
    var savedItem = savedSession.items().get(0);
    assertThat(savedItem.pendingQueueSequence()).isEqualTo(5L); // assigned tail sequence 5
    assertThat(savedItem.introducedAt()).isEqualTo(nowUtc);
  }

  @Test
  @DisplayName(
      "14.3.5 - L: Record HARD assigns tail sequence and increments session nextQueueSequence")
  void testRecordHardAssignsTailSequence() {
    var sessionRepo =
        org.mockito.Mockito.mock(
            com.soap.soap.application.port.out.VocabularyReviewSessionRepositoryPort.class);
    var tzPort =
        org.mockito.Mockito.mock(com.soap.soap.application.port.out.UserTimezonePort.class);
    when(tzPort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);

    var useCaseWithSession =
        new RecordVocabularyReviewUseCase(
            currentUser, repository, historyRepository, sessionRepo, tzPort, scheduler, clock);

    when(currentUser.requireUserId()).thenReturn(userId);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var user = new User(userId, "Ada", "ada@example.com");
    var word = new Word(wordId, "targetWord", "en");

    var learningCard =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(1),
            null,
            0L,
            1,
            nowUtc.minusMinutes(10),
            nowUtc,
            SrsState.RELEARNING,
            0.5,
            7.0,
            1,
            1);

    when(repository.findByUserIdAndWordId(userId, wordId)).thenReturn(Optional.of(learningCard));
    when(repository.save(any(UserVocabulary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    UUID sessionId = UUID.randomUUID();
    var sessionItem =
        new com.soap.soap.domain.model.VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, learningCard, 1, nowUtc.minusMinutes(10), 2L);
    var activeSession =
        new com.soap.soap.domain.model.VocabularyReviewSession(
            sessionId,
            userId,
            LocalDate.parse("2026-09-11"),
            com.soap.soap.domain.model.ReviewSessionStatus.ACTIVE,
            15,
            8L, // current nextQueueSequence is 8
            nowUtc.minusHours(1),
            null,
            List.of(sessionItem));

    when(sessionRepo.findSessionForUpdate(eq(userId), any()))
        .thenReturn(Optional.of(activeSession));
    var captor =
        org.mockito.ArgumentCaptor.forClass(
            com.soap.soap.domain.model.VocabularyReviewSession.class);
    when(sessionRepo.saveSession(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    useCaseWithSession.recordReview(wordId, ReviewRating.HARD);

    var savedSession = captor.getValue();
    assertThat(savedSession.nextQueueSequence()).isEqualTo(9L); // incremented from 8 to 9
    var savedItem = savedSession.items().get(0);
    assertThat(savedItem.pendingQueueSequence()).isEqualTo(8L); // assigned tail sequence 8
  }

  @Test
  @DisplayName(
      "14.3.5 - M: Record GOOD graduates card to REVIEW and clears pendingQueueSequence to null")
  void testRecordGoodGraduationClearsPendingQueueSequence() {
    var sessionRepo =
        org.mockito.Mockito.mock(
            com.soap.soap.application.port.out.VocabularyReviewSessionRepositoryPort.class);
    var tzPort =
        org.mockito.Mockito.mock(com.soap.soap.application.port.out.UserTimezonePort.class);
    when(tzPort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);

    var useCaseWithSession =
        new RecordVocabularyReviewUseCase(
            currentUser, repository, historyRepository, sessionRepo, tzPort, scheduler, clock);

    when(currentUser.requireUserId()).thenReturn(userId);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var user = new User(userId, "Ada", "ada@example.com");
    var word = new Word(wordId, "targetWord", "en");

    var inRelearning =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(30),
            null,
            0L,
            4,
            nowUtc.minusMinutes(10),
            nowUtc,
            SrsState.RELEARNING,
            2.0,
            4.5,
            4,
            1);

    when(repository.findByUserIdAndWordId(userId, wordId)).thenReturn(Optional.of(inRelearning));
    when(repository.save(any(UserVocabulary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    UUID sessionId = UUID.randomUUID();
    var sessionItem =
        new com.soap.soap.domain.model.VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, inRelearning, 1, nowUtc.minusMinutes(10), 3L);
    var activeSession =
        new com.soap.soap.domain.model.VocabularyReviewSession(
            sessionId,
            userId,
            LocalDate.parse("2026-09-11"),
            com.soap.soap.domain.model.ReviewSessionStatus.ACTIVE,
            15,
            5L,
            nowUtc.minusHours(1),
            null,
            List.of(sessionItem));

    when(sessionRepo.findSessionForUpdate(eq(userId), any()))
        .thenReturn(Optional.of(activeSession));
    var captor =
        org.mockito.ArgumentCaptor.forClass(
            com.soap.soap.domain.model.VocabularyReviewSession.class);
    when(sessionRepo.saveSession(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    useCaseWithSession.recordReview(wordId, ReviewRating.GOOD);

    var savedSession = captor.getValue();
    var savedItem = savedSession.items().get(0);
    assertThat(savedItem.pendingQueueSequence()).isNull(); // cleared from pending queue!
    assertThat(savedSession.status())
        .isEqualTo(com.soap.soap.domain.model.ReviewSessionStatus.COMPLETED);
    assertThat(savedSession.completedAt()).isEqualTo(nowUtc);
  }

  @Test
  @DisplayName(
      "14.3.5 - N: Session completion requires all introduced, zero learning state, and zero queue sequence")
  void testCompletionRequiresAllIntroducedZeroLearningAndZeroQueueSeq() {
    var sessionRepo =
        org.mockito.Mockito.mock(
            com.soap.soap.application.port.out.VocabularyReviewSessionRepositoryPort.class);
    var tzPort =
        org.mockito.Mockito.mock(com.soap.soap.application.port.out.UserTimezonePort.class);
    when(tzPort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);

    var useCaseWithSession =
        new RecordVocabularyReviewUseCase(
            currentUser, repository, historyRepository, sessionRepo, tzPort, scheduler, clock);

    when(currentUser.requireUserId()).thenReturn(userId);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var user = new User(userId, "Ada", "ada@example.com");

    var word1 = new Word(wordId, "word1", "en");
    var word2 = new Word(UUID.randomUUID(), "word2", "en");

    var card1InRelearning =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word1,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(10),
            null,
            0L,
            1,
            nowUtc.minusMinutes(10),
            nowUtc,
            SrsState.RELEARNING,
            1.0,
            5.0,
            1,
            1);

    var card2InReview =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word2,
            VocabularyStatus.KNOWN,
            nowUtc.minusDays(10),
            nowUtc.minusDays(5),
            86400L,
            2,
            nowUtc.minusDays(5),
            nowUtc.plusDays(3),
            SrsState.REVIEW,
            5.0,
            3.5,
            2,
            0);

    when(repository.findByUserIdAndWordId(userId, wordId))
        .thenReturn(Optional.of(card1InRelearning));
    when(repository.save(any(UserVocabulary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    UUID sessionId = UUID.randomUUID();
    var item1 =
        new com.soap.soap.domain.model.VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, card1InRelearning, 1, nowUtc.minusMinutes(10), 1L);
    var item2 =
        new com.soap.soap.domain.model.VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, card2InReview, 2, nowUtc.minusMinutes(5), null);

    var activeSession =
        new com.soap.soap.domain.model.VocabularyReviewSession(
            sessionId,
            userId,
            LocalDate.parse("2026-09-11"),
            com.soap.soap.domain.model.ReviewSessionStatus.ACTIVE,
            15,
            2L,
            nowUtc.minusHours(1),
            null,
            List.of(item1, item2));

    when(sessionRepo.findSessionForUpdate(eq(userId), any()))
        .thenReturn(Optional.of(activeSession));
    var captor =
        org.mockito.ArgumentCaptor.forClass(
            com.soap.soap.domain.model.VocabularyReviewSession.class);
    when(sessionRepo.saveSession(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    // Card 1 rated AGAIN -> stays in RELEARNING
    useCaseWithSession.recordReview(wordId, ReviewRating.AGAIN);

    var savedSession = captor.getValue();
    // Item 1 is still in RELEARNING -> session MUST remain ACTIVE!
    assertThat(savedSession.status())
        .isEqualTo(com.soap.soap.domain.model.ReviewSessionStatus.ACTIVE);
    assertThat(savedSession.completedAt()).isNull();
  }

  @Test
  @DisplayName("14.3.5 - O: Hexagonal Boundary: Application port exposes NO JPA entities")
  void testHexagonalBoundaryNoJpaEntityInPort() {
    var methods =
        com.soap.soap.application.port.out.VocabularyReviewSessionRepositoryPort.class.getMethods();
    for (var method : methods) {
      // Check return type
      assertThat(method.getReturnType().getName())
          .doesNotContain("jakarta.persistence")
          .doesNotContain(".entity.");

      // Check parameters
      for (var paramType : method.getParameterTypes()) {
        assertThat(paramType.getName())
            .doesNotContain("jakarta.persistence")
            .doesNotContain(".entity.");
      }
    }
  }

  @Test
  @DisplayName(
      "14.3.5.1 - F: REVIEW card -> AGAIN response has fresh rating options calculated from RELEARNING")
  void testReviewCardAgainResponseHasFreshRatingOptions() {
    when(currentUser.requireUserId()).thenReturn(userId);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var user = new User(userId, "Ada", "ada@example.com");
    var word = new Word(wordId, "reviewWord", "en");

    // Existing card in REVIEW
    var currentReviewCard =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.KNOWN,
            nowUtc.minusDays(10),
            nowUtc.minusDays(5),
            86400L * 4,
            2,
            nowUtc.minusDays(4),
            nowUtc,
            SrsState.REVIEW,
            4.0,
            5.0,
            2,
            0);

    when(repository.findByUserIdAndWordId(userId, wordId))
        .thenReturn(Optional.of(currentReviewCard));
    when(repository.save(any(UserVocabulary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = useCase.recordReview(wordId, ReviewRating.AGAIN);

    // Persisted card is now RELEARNING
    assertThat(result.vocabulary().srsState()).isEqualTo(SrsState.RELEARNING);
    assertThat(result.vocabulary().nextReviewAt()).isEqualTo(nowUtc.plusMinutes(10));

    // Result must contain FRESH rating options calculated from the new RELEARNING state:
    // AGAIN = 10m, GOOD = 1d
    var options = result.ratingOptions();
    assertThat(options).isNotNull().hasSize(2);

    var againOpt =
        options.stream().filter(o -> o.rating() == ReviewRating.AGAIN).findFirst().orElseThrow();
    assertThat(againOpt.intervalSeconds()).isEqualTo(600L);
    assertThat(againOpt.nextReviewAt()).isEqualTo(nowUtc.plusMinutes(10));

    var goodOpt =
        options.stream().filter(o -> o.rating() == ReviewRating.GOOD).findFirst().orElseThrow();
    assertThat(goodOpt.intervalSeconds()).isEqualTo(86400L);
    assertThat(goodOpt.nextReviewAt()).isEqualTo(nowUtc.plusDays(1));
  }

  @Test
  @DisplayName(
      "14.3.5.1 - G: RELEARNING card -> AGAIN again response maintains fresh rating options")
  void testRelearningCardAgainAgainResponseMaintainsFreshRatingOptions() {
    when(currentUser.requireUserId()).thenReturn(userId);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var user = new User(userId, "Ada", "ada@example.com");
    var word = new Word(wordId, "relearningWord", "en");

    // Existing card ALREADY in RELEARNING
    var currentRelearningCard =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(10),
            null,
            600L,
            3,
            nowUtc.minusMinutes(10),
            nowUtc,
            SrsState.RELEARNING,
            1.1,
            6.5,
            3,
            1);

    when(repository.findByUserIdAndWordId(userId, wordId))
        .thenReturn(Optional.of(currentRelearningCard));
    when(repository.save(any(UserVocabulary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = useCase.recordReview(wordId, ReviewRating.AGAIN);

    // Stays in RELEARNING with incremented repetitions (lapses remain 1)
    assertThat(result.vocabulary().srsState()).isEqualTo(SrsState.RELEARNING);
    assertThat(result.vocabulary().lapses()).isEqualTo(1);
    assertThat(result.vocabulary().repetitions()).isEqualTo(4);
    assertThat(result.vocabulary().nextReviewAt()).isEqualTo(nowUtc.plusMinutes(10));

    // Fresh options calculated from the updated RELEARNING state: AGAIN = 10m, GOOD = 1d
    var options = result.ratingOptions();
    assertThat(options).isNotNull().hasSize(2);

    var againOpt =
        options.stream().filter(o -> o.rating() == ReviewRating.AGAIN).findFirst().orElseThrow();
    assertThat(againOpt.intervalSeconds()).isEqualTo(600L);
    assertThat(againOpt.nextReviewAt()).isEqualTo(nowUtc.plusMinutes(10));

    var goodOpt =
        options.stream().filter(o -> o.rating() == ReviewRating.GOOD).findFirst().orElseThrow();
    assertThat(goodOpt.intervalSeconds()).isEqualTo(86400L);
    assertThat(goodOpt.nextReviewAt()).isEqualTo(nowUtc.plusDays(1));
  }

  @Test
  @DisplayName(
      "14.3.5.2 - Repeated AGAIN assigns pendingQueueSequence to tail and returns in result (Section 11 C)")
  void testRepeatedAgainAssignsPendingQueueSequenceToTail() {
    var sessionRepo =
        org.mockito.Mockito.mock(
            com.soap.soap.application.port.out.VocabularyReviewSessionRepositoryPort.class);
    var tzPort =
        org.mockito.Mockito.mock(com.soap.soap.application.port.out.UserTimezonePort.class);
    var customUseCase =
        new RecordVocabularyReviewUseCase(
            currentUser, repository, historyRepository, sessionRepo, tzPort, scheduler, clock);

    when(currentUser.requireUserId()).thenReturn(userId);
    when(tzPort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse("2026-09-11");

    var user = new User(userId, "Ada", "ada@example.com");
    var wordA = new Word(wordId, "cardA", "en");
    var uvA =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            wordA,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(1),
            null,
            600L,
            1,
            nowUtc.minusMinutes(10),
            nowUtc,
            SrsState.LEARNING,
            0.5,
            7.0,
            1,
            0);

    UUID sessionId = UUID.randomUUID();
    var sessionItemA =
        new com.soap.soap.domain.model.VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, uvA, 1, nowUtc.minusMinutes(10), 1L);

    var session =
        new com.soap.soap.domain.model.VocabularyReviewSession(
            sessionId,
            userId,
            localDate,
            com.soap.soap.domain.model.ReviewSessionStatus.ACTIVE,
            15,
            4L, // nextQueueSequence is currently 4
            nowUtc.minusHours(1),
            null,
            List.of(sessionItemA));

    when(repository.findByUserIdAndWordId(userId, wordId)).thenReturn(Optional.of(uvA));
    when(repository.save(any(UserVocabulary.class))).thenAnswer(inv -> inv.getArgument(0));
    when(sessionRepo.findSessionForUpdate(userId, localDate)).thenReturn(Optional.of(session));
    when(sessionRepo.saveSession(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = customUseCase.recordReview(wordId, ReviewRating.AGAIN);

    // Section 11 C: Repeated AGAIN card A returns pendingQueueSequence = 4, baseOrder = 1
    assertThat(result.pendingQueueSequence()).isEqualTo(4L);
    assertThat(result.baseOrder()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "14.3.5.2 - GOOD rating returns pendingQueueSequence = null (graduated) (Section 11 D)")
  void testGoodRatingReturnsNullPendingQueueSequence() {
    var sessionRepo =
        org.mockito.Mockito.mock(
            com.soap.soap.application.port.out.VocabularyReviewSessionRepositoryPort.class);
    var tzPort =
        org.mockito.Mockito.mock(com.soap.soap.application.port.out.UserTimezonePort.class);
    var customUseCase =
        new RecordVocabularyReviewUseCase(
            currentUser, repository, historyRepository, sessionRepo, tzPort, scheduler, clock);

    when(currentUser.requireUserId()).thenReturn(userId);
    when(tzPort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse("2026-09-11");

    var user = new User(userId, "Ada", "ada@example.com");
    var wordA = new Word(wordId, "cardA", "en");
    var uvA =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            wordA,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(1),
            null,
            600L,
            1,
            nowUtc.minusMinutes(10),
            nowUtc,
            SrsState.LEARNING,
            0.5,
            7.0,
            1,
            0);

    UUID sessionId = UUID.randomUUID();
    var sessionItemA =
        new com.soap.soap.domain.model.VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, uvA, 1, nowUtc.minusMinutes(10), 1L);

    var session =
        new com.soap.soap.domain.model.VocabularyReviewSession(
            sessionId,
            userId,
            localDate,
            com.soap.soap.domain.model.ReviewSessionStatus.ACTIVE,
            15,
            4L,
            nowUtc.minusHours(1),
            null,
            List.of(sessionItemA));

    when(repository.findByUserIdAndWordId(userId, wordId)).thenReturn(Optional.of(uvA));
    when(repository.save(any(UserVocabulary.class))).thenAnswer(inv -> inv.getArgument(0));
    when(sessionRepo.findSessionForUpdate(userId, localDate)).thenReturn(Optional.of(session));
    when(sessionRepo.saveSession(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = customUseCase.recordReview(wordId, ReviewRating.GOOD);

    // Section 11 D: GOOD card graduates to REVIEW, pendingQueueSequence = null, baseOrder = 1
    assertThat(result.vocabulary().srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(result.pendingQueueSequence()).isNull();
    assertThat(result.baseOrder()).isEqualTo(1);
  }
}
