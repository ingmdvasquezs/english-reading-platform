package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserTimezonePort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyReviewHistoryRepositoryPort;
import com.soap.soap.application.port.out.VocabularyReviewSessionRepositoryPort;
import com.soap.soap.application.service.RatingOptionsBuilder;
import com.soap.soap.domain.model.ReviewSessionStatus;
import com.soap.soap.domain.model.SrsState;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyReviewSession;
import com.soap.soap.domain.model.VocabularyReviewSessionItem;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import com.soap.soap.domain.service.FsrsScheduler;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrepareVocabularyReviewUseCaseTest {
  @Mock private CurrentUserPort currentUser;
  @Mock private UserVocabularyRepositoryPort repository;
  @Mock private UserVocabularyReviewHistoryRepositoryPort historyRepository;
  @Mock private VocabularyReviewSessionRepositoryPort sessionRepository;
  @Mock private UserTimezonePort userTimezonePort;

  private final Clock clock = Clock.fixed(Instant.parse("2026-09-11T12:00:00Z"), ZoneOffset.UTC);
  private final FsrsScheduler scheduler = new FsrsScheduler();
  private final RatingOptionsBuilder ratingOptionsBuilder = new RatingOptionsBuilder(scheduler);
  private PrepareVocabularyReviewUseCase useCase;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    useCase =
        new PrepareVocabularyReviewUseCase(
            currentUser,
            repository,
            historyRepository,
            sessionRepository,
            scheduler,
            clock,
            userTimezonePort,
            ratingOptionsBuilder);
  }

  @Test
  @DisplayName("Rejects invalid batch sizes (< 1 or > 100)")
  void rejectsInvalidBatchSizes() {
    for (int invalidSize : List.of(-1, 0, 101, 200)) {
      assertThatThrownBy(() -> useCase.prepareReview(invalidSize))
          .isInstanceOf(InvalidApplicationArgumentException.class)
          .hasMessageContaining("Review batch size must be between 1 and 100");
    }
  }

  @Test
  @DisplayName("14.3.5 - A: Create daily session freezes base cohort with baseOrder 1..N")
  void testCreateDailySessionFreezesBaseCohort() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(userTimezonePort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse("2026-09-11");

    when(sessionRepository.findSession(userId, localDate)).thenReturn(Optional.empty());

    var user = new User(userId, "Ada", "ada@example.com");
    var word1 = new Word(UUID.randomUUID(), "wordA", "en");
    var word2 = new Word(UUID.randomUUID(), "wordB", "en");
    var uv1 = createSampleVocab(user, word1, nowUtc, SrsState.REVIEW, VocabularyStatus.KNOWN);
    var uv2 = createSampleVocab(user, word2, nowUtc, SrsState.REVIEW, VocabularyStatus.KNOWN);

    when(repository.countDueWords(userId, nowUtc)).thenReturn(2L);
    when(repository.countTotalReviewableWords(userId, nowUtc)).thenReturn(2L);
    when(repository.findReviewCandidates(userId, nowUtc, 15)).thenReturn(List.of(uv1, uv2));
    when(repository.findByUserIdAndWordIds(eq(userId), any()))
        .thenReturn(Map.of(word1.id(), uv1, word2.id(), uv2));

    var captor = ArgumentCaptor.forClass(VocabularyReviewSession.class);
    when(sessionRepository.saveSession(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    var result = useCase.prepareReview(15);

    assertThat(result.dailyLimit()).isEqualTo(15);
    assertThat(result.dailyBaseCompleted()).isEqualTo(0);
    assertThat(result.dailyBaseRemaining()).isEqualTo(2); // cohort size 2 - 0 = 2
    assertThat(result.entries()).hasSize(2);
    assertThat(result.entries().get(0).word()).isEqualTo("wordA");
    assertThat(result.entries().get(1).word()).isEqualTo("wordB");

    var savedSession = captor.getValue();
    assertThat(savedSession.status()).isEqualTo(ReviewSessionStatus.ACTIVE);
    assertThat(savedSession.items()).hasSize(2);
    assertThat(savedSession.items().get(0).baseOrder()).isEqualTo(1);
    assertThat(savedSession.items().get(1).baseOrder()).isEqualTo(2);
    assertThat(savedSession.items().get(0).introducedAt()).isNull();
    assertThat(savedSession.items().get(0).pendingQueueSequence()).isNull();
  }

  @Test
  @DisplayName(
      "14.3.5 - B: Cohort size used for dailyBaseRemaining: cohort 6 -> 5 completed -> 1 remaining")
  void testCohortSizeUsedForDailyBaseRemaining() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(userTimezonePort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse("2026-09-11");

    var user = new User(userId, "Ada", "ada@example.com");
    List<VocabularyReviewSessionItem> items = new ArrayList<>();
    UUID sessionId = UUID.randomUUID();

    for (int i = 1; i <= 6; i++) {
      var word = new Word(UUID.randomUUID(), "word" + i, "en");
      var uv = createSampleVocab(user, word, nowUtc, SrsState.REVIEW, VocabularyStatus.KNOWN);
      LocalDateTime introducedAt = (i <= 5) ? nowUtc.minusMinutes(10 * i) : null;
      items.add(
          new VocabularyReviewSessionItem(UUID.randomUUID(), sessionId, uv, i, introducedAt, null));
    }

    var session =
        new VocabularyReviewSession(
            sessionId,
            userId,
            localDate,
            ReviewSessionStatus.ACTIVE,
            15,
            1L,
            nowUtc.minusHours(1),
            null,
            items);

    when(sessionRepository.findSession(userId, localDate)).thenReturn(Optional.of(session));
    when(repository.countDueWords(userId, nowUtc)).thenReturn(1L);
    when(repository.countTotalReviewableWords(userId, nowUtc)).thenReturn(20L);
    when(repository.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Collections.emptyMap());

    var result = useCase.prepareReview(15);

    // Cohort size 6, completed 5 -> exactly 1 remaining!
    assertThat(result.dailyLimit()).isEqualTo(15);
    assertThat(result.dailyBaseCompleted()).isEqualTo(5);
    assertThat(result.dailyBaseRemaining()).isEqualTo(1);
    assertThat(result.entries()).hasSize(1);
    assertThat(result.entries().get(0).word()).isEqualTo("word6");
  }

  @Test
  @DisplayName(
      "14.3.5 - C: Exit and re-entry returns EXACT same session and remaining base cards in baseOrder")
  void testExitReentryPreservesSameSessionAndRemainingBaseCards() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(userTimezonePort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse("2026-09-11");

    var user = new User(userId, "Ada", "ada@example.com");
    List<VocabularyReviewSessionItem> items = new ArrayList<>();
    UUID sessionId = UUID.randomUUID();

    // 15 base cards: 5 reviewed, 10 unreviewed
    for (int i = 1; i <= 15; i++) {
      var word = new Word(UUID.randomUUID(), "base" + i, "en");
      var uv = createSampleVocab(user, word, nowUtc, SrsState.REVIEW, VocabularyStatus.KNOWN);
      LocalDateTime introducedAt = (i <= 5) ? nowUtc.minusMinutes(5 * i) : null;
      items.add(
          new VocabularyReviewSessionItem(UUID.randomUUID(), sessionId, uv, i, introducedAt, null));
    }

    var session =
        new VocabularyReviewSession(
            sessionId,
            userId,
            localDate,
            ReviewSessionStatus.ACTIVE,
            15,
            1L,
            nowUtc.minusHours(1),
            null,
            items);

    when(sessionRepository.findSession(userId, localDate)).thenReturn(Optional.of(session));
    when(repository.countDueWords(userId, nowUtc)).thenReturn(10L);
    when(repository.countTotalReviewableWords(userId, nowUtc)).thenReturn(50L);
    when(repository.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Collections.emptyMap());

    var result = useCase.prepareReview(15);

    assertThat(result.dailyBaseCompleted()).isEqualTo(5);
    assertThat(result.dailyBaseRemaining()).isEqualTo(10);
    assertThat(result.entries()).hasSize(10);

    // Exactly cards 6 through 15 in order
    for (int i = 0; i < 10; i++) {
      assertThat(result.entries().get(i).word()).isEqualTo("base" + (i + 6));
    }
  }

  @Test
  @DisplayName("14.3.5 - D: prepareReview(100) cannot bypass session cohort")
  void testBypassRequestSizeCappedBySessionCohort() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(userTimezonePort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse("2026-09-11");

    var user = new User(userId, "Ada", "ada@example.com");
    List<VocabularyReviewSessionItem> items = new ArrayList<>();
    UUID sessionId = UUID.randomUUID();

    for (int i = 1; i <= 15; i++) {
      var word = new Word(UUID.randomUUID(), "word" + i, "en");
      var uv = createSampleVocab(user, word, nowUtc, SrsState.REVIEW, VocabularyStatus.KNOWN);
      LocalDateTime introducedAt = (i <= 10) ? nowUtc.minusMinutes(i) : null;
      items.add(
          new VocabularyReviewSessionItem(UUID.randomUUID(), sessionId, uv, i, introducedAt, null));
    }

    var session =
        new VocabularyReviewSession(
            sessionId,
            userId,
            localDate,
            ReviewSessionStatus.ACTIVE,
            15,
            1L,
            nowUtc.minusHours(1),
            null,
            items);

    when(sessionRepository.findSession(userId, localDate)).thenReturn(Optional.of(session));
    when(repository.countDueWords(userId, nowUtc)).thenReturn(5L);
    when(repository.countTotalReviewableWords(userId, nowUtc)).thenReturn(100L);
    when(repository.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Collections.emptyMap());

    // Request size 100
    var result = useCase.prepareReview(100);

    // Capped by remaining session base items (5), never 100!
    assertThat(result.dailyBaseRemaining()).isEqualTo(5);
    assertThat(result.entries()).hasSize(5);
  }

  @Test
  @DisplayName(
      "14.3.5 - E: Completed session re-entry returns dailyComplete=true and 0 entries (no second session)")
  void testCompletedSessionDoesNotCreateSecondSession() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(userTimezonePort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse("2026-09-11");

    var user = new User(userId, "Ada", "ada@example.com");
    List<VocabularyReviewSessionItem> items = new ArrayList<>();
    UUID sessionId = UUID.randomUUID();
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

    for (int i = 1; i <= 15; i++) {
      var word = new Word(UUID.randomUUID(), "word" + i, "en");
      var uv = createSampleVocab(user, word, nowUtc, SrsState.REVIEW, VocabularyStatus.KNOWN);
      items.add(
          new VocabularyReviewSessionItem(
              UUID.randomUUID(), sessionId, uv, i, nowUtc.minusHours(1), null));
    }

    var completedSession =
        new VocabularyReviewSession(
            sessionId,
            userId,
            localDate,
            ReviewSessionStatus.COMPLETED,
            15,
            16L,
            nowUtc.minusHours(2),
            nowUtc.minusMinutes(10),
            items);

    when(sessionRepository.findSession(userId, localDate))
        .thenReturn(Optional.of(completedSession));
    when(repository.countTotalReviewableWords(userId, nowUtc)).thenReturn(50L);

    var result = useCase.prepareReview(15);

    assertThat(result.dailyComplete()).isTrue();
    assertThat(result.dailyBaseCompleted()).isEqualTo(15);
    assertThat(result.dailyBaseRemaining()).isEqualTo(0);
    assertThat(result.entries()).isEmpty();
    assertThat(result.learnAheadEntries()).isEmpty();
  }

  @Test
  @DisplayName(
      "14.3.5 - F: Pending FIFO queue order: A Again, B Again, C Hard, then A Again -> B, C, A")
  void testRepeatedAgainMovesToBackOfQueue() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(userTimezonePort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse("2026-09-11");

    var user = new User(userId, "Ada", "ada@example.com");
    UUID sessionId = UUID.randomUUID();

    // Cards A, B, C. All base cards are introduced (dailyBaseRemaining = 0).
    // A was answered Again (seq 1), B Again (seq 2), C Hard (seq 3).
    // Then A was answered Again again -> A was assigned seq 4!
    // Next review dates:
    // B: due in 6m (now + 6m) -> seq 2
    // C: due in 12m (now + 12m) -> seq 3
    // A: due in 10m (now + 10m) -> seq 4
    var wordA = new Word(UUID.randomUUID(), "cardA", "en");
    var wordB = new Word(UUID.randomUUID(), "cardB", "en");
    var wordC = new Word(UUID.randomUUID(), "cardC", "en");

    var uvA =
        createSampleVocab(
            user, wordA, nowUtc.plusMinutes(10), SrsState.RELEARNING, VocabularyStatus.LEARNING);
    var uvB =
        createSampleVocab(
            user, wordB, nowUtc.plusMinutes(6), SrsState.RELEARNING, VocabularyStatus.LEARNING);
    var uvC =
        createSampleVocab(
            user, wordC, nowUtc.plusMinutes(12), SrsState.RELEARNING, VocabularyStatus.LEARNING);

    var itemB =
        new VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, uvB, 2, nowUtc.minusMinutes(4), 2L);
    var itemC =
        new VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, uvC, 3, nowUtc.minusMinutes(3), 3L);
    var itemA =
        new VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, uvA, 1, nowUtc.minusMinutes(1), 4L);

    var session =
        new VocabularyReviewSession(
            sessionId,
            userId,
            localDate,
            ReviewSessionStatus.ACTIVE,
            15,
            5L,
            nowUtc.minusHours(1),
            null,
            List.of(itemA, itemB, itemC));

    when(sessionRepository.findSession(userId, localDate)).thenReturn(Optional.of(session));
    when(repository.countDueWords(userId, nowUtc)).thenReturn(0L);
    when(repository.countTotalReviewableWords(userId, nowUtc)).thenReturn(20L);
    when(repository.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Collections.emptyMap());

    var result = useCase.prepareReview(15);

    // All are within 20m learn-ahead, sorted strictly by pending_queue_sequence ASC:
    // B (seq 2), C (seq 3), A (seq 4) -> B, C, A!
    assertThat(result.learnAheadEntries()).hasSize(3);
    assertThat(result.learnAheadEntries().get(0).word()).isEqualTo("cardB");
    assertThat(result.learnAheadEntries().get(1).word()).isEqualTo("cardC");
    assertThat(result.learnAheadEntries().get(2).word()).isEqualTo("cardA");
  }

  @Test
  @DisplayName("14.3.5 - G: Exit and re-entry preserves pending queue order B, C, A")
  void testExitReentryPreservesPendingQueueOrder() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(userTimezonePort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse("2026-09-11");

    var user = new User(userId, "Ada", "ada@example.com");
    UUID sessionId = UUID.randomUUID();

    var wordA = new Word(UUID.randomUUID(), "cardA", "en");
    var wordB = new Word(UUID.randomUUID(), "cardB", "en");
    var wordC = new Word(UUID.randomUUID(), "cardC", "en");

    var uvA =
        createSampleVocab(
            user, wordA, nowUtc.minusSeconds(10), SrsState.RELEARNING, VocabularyStatus.LEARNING);
    var uvB =
        createSampleVocab(
            user, wordB, nowUtc.minusSeconds(20), SrsState.RELEARNING, VocabularyStatus.LEARNING);
    var uvC =
        createSampleVocab(
            user, wordC, nowUtc.minusSeconds(15), SrsState.RELEARNING, VocabularyStatus.LEARNING);

    // All are due now
    var itemB =
        new VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, uvB, 2, nowUtc.minusMinutes(4), 2L);
    var itemC =
        new VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, uvC, 3, nowUtc.minusMinutes(3), 3L);
    var itemA =
        new VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, uvA, 1, nowUtc.minusMinutes(1), 4L);

    var session =
        new VocabularyReviewSession(
            sessionId,
            userId,
            localDate,
            ReviewSessionStatus.ACTIVE,
            15,
            5L,
            nowUtc.minusHours(1),
            null,
            List.of(itemA, itemB, itemC));

    when(sessionRepository.findSession(userId, localDate)).thenReturn(Optional.of(session));
    when(repository.countDueWords(userId, nowUtc)).thenReturn(3L);
    when(repository.countTotalReviewableWords(userId, nowUtc)).thenReturn(20L);
    when(repository.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Collections.emptyMap());

    var result = useCase.prepareReview(15);

    assertThat(result.entries()).hasSize(3);
    assertThat(result.entries().get(0).word()).isEqualTo("cardB");
    assertThat(result.entries().get(1).word()).isEqualTo("cardC");
    assertThat(result.entries().get(2).word()).isEqualTo("cardA");
  }

  @Test
  @DisplayName(
      "14.3.5 - H: Single pending card remains eligible and repeats indefinitely via learn-ahead")
  void testOnlyOneAgainCanRepeatIndefinitely() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(userTimezonePort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse("2026-09-11");

    var user = new User(userId, "Ada", "ada@example.com");
    UUID sessionId = UUID.randomUUID();
    var wordA = new Word(UUID.randomUUID(), "cardA", "en");
    var uvA =
        createSampleVocab(
            user, wordA, nowUtc.plusMinutes(10), SrsState.RELEARNING, VocabularyStatus.LEARNING);

    var itemA =
        new VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, uvA, 1, nowUtc.minusMinutes(1), 2L);

    var session =
        new VocabularyReviewSession(
            sessionId,
            userId,
            localDate,
            ReviewSessionStatus.ACTIVE,
            15,
            3L,
            nowUtc.minusHours(1),
            null,
            List.of(itemA));

    when(sessionRepository.findSession(userId, localDate)).thenReturn(Optional.of(session));
    when(repository.countDueWords(userId, nowUtc)).thenReturn(0L);
    when(repository.countTotalReviewableWords(userId, nowUtc)).thenReturn(10L);
    when(repository.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Collections.emptyMap());

    var result = useCase.prepareReview(15);

    // Since 10m <= 20m learn-ahead, A appears in learnAheadEntries
    assertThat(result.learnAheadEntries()).hasSize(1);
    assertThat(result.learnAheadEntries().get(0).word()).isEqualTo("cardA");
    assertThat(result.dailyComplete()).isFalse();
  }

  @Test
  @DisplayName(
      "14.3.5 - I: America/Bogota timezone: calendar day derived from Instant, not shifted by 5 hours")
  void testTimezoneAmericaBogotaDerivesCalendarDayFromInstant() {
    when(currentUser.requireUserId()).thenReturn(userId);
    ZoneId bogota = ZoneId.of("America/Bogota");
    when(userTimezonePort.resolveUserZoneId(userId)).thenReturn(bogota);

    // 2026-09-20 01:30:00 UTC is 2026-09-19 20:30:00 in America/Bogota!
    Clock bogotaClock = Clock.fixed(Instant.parse("2026-09-20T01:30:00Z"), ZoneOffset.UTC);

    var tzUseCase =
        new PrepareVocabularyReviewUseCase(
            currentUser,
            repository,
            historyRepository,
            sessionRepository,
            scheduler,
            bogotaClock,
            userTimezonePort,
            ratingOptionsBuilder);

    LocalDate expectedBogotaDate = LocalDate.parse("2026-09-19");
    when(sessionRepository.findSession(eq(userId), eq(expectedBogotaDate)))
        .thenReturn(Optional.empty());

    var user = new User(userId, "Ada", "ada@example.com");
    var word1 = new Word(UUID.randomUUID(), "bogotaWord", "en");
    var uv1 =
        createSampleVocab(
            user,
            word1,
            LocalDateTime.parse("2026-09-20T01:30:00"),
            SrsState.REVIEW,
            VocabularyStatus.KNOWN);

    when(repository.countDueWords(eq(userId), any())).thenReturn(1L);
    when(repository.countTotalReviewableWords(eq(userId), any())).thenReturn(1L);
    when(repository.findReviewCandidates(eq(userId), any(), eq(15))).thenReturn(List.of(uv1));
    when(repository.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Map.of(word1.id(), uv1));
    when(sessionRepository.saveSession(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = tzUseCase.prepareReview(15);

    // Verified: Session queried and saved with 2026-09-19 (Bogota local day), NOT 2026-09-20!
    verify(sessionRepository, org.mockito.Mockito.atLeastOnce())
        .findSession(userId, expectedBogotaDate);
    assertThat(result.entries()).hasSize(1);
    assertThat(result.entries().get(0).word()).isEqualTo("bogotaWord");
  }

  @Test
  @DisplayName(
      "14.3.5 - J: Active LEARNING/RELEARNING state prevents COMPLETED even if queueSequence is null")
  void testLearningStatePreventsCompletionEvenIfQueueSeqNull() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(userTimezonePort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse("2026-09-11");

    var user = new User(userId, "Ada", "ada@example.com");
    UUID sessionId = UUID.randomUUID();
    var wordA = new Word(UUID.randomUUID(), "cardA", "en");

    // Accidentally null pendingQueueSequence, but SRS state is RELEARNING
    var uvA =
        createSampleVocab(
            user, wordA, nowUtc.plusMinutes(10), SrsState.RELEARNING, VocabularyStatus.LEARNING);
    var itemA =
        new VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, uvA, 1, nowUtc.minusMinutes(1), null);

    var session =
        new VocabularyReviewSession(
            sessionId,
            userId,
            localDate,
            ReviewSessionStatus.ACTIVE,
            15,
            2L,
            nowUtc.minusHours(1),
            null,
            List.of(itemA));

    when(sessionRepository.findSession(userId, localDate)).thenReturn(Optional.of(session));
    when(repository.countDueWords(userId, nowUtc)).thenReturn(0L);
    when(repository.countTotalReviewableWords(userId, nowUtc)).thenReturn(10L);
    when(repository.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Collections.emptyMap());

    var result = useCase.prepareReview(15);

    // dailyComplete MUST be false because SrsState is RELEARNING!
    assertThat(result.dailyComplete()).isFalse();
    assertThat(result.pendingLearningCount()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "14.3.5.1 - A: No session + 5 distinct reviews today -> bootstrap -> completed=5, max 10 base capacity")
  void testBootstrapWith5ReviewsToday() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(userTimezonePort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse("2026-09-11");
    var user = new User(userId, "Ada", "ada@example.com");

    when(sessionRepository.findSession(userId, localDate)).thenReturn(Optional.empty());

    // 5 words reviewed earlier today
    var summaries = new ArrayList<com.soap.soap.application.model.ReviewedWordDaySummary>();
    var vocabMap = new HashMap<UUID, UserVocabulary>();
    for (int i = 1; i <= 5; i++) {
      var uvId = UUID.randomUUID();
      var word = new Word(UUID.randomUUID(), "word" + i, "en");
      var uv =
          createSampleVocab(
              user, word, nowUtc.plusDays(1), SrsState.REVIEW, VocabularyStatus.KNOWN);
      summaries.add(
          new com.soap.soap.application.model.ReviewedWordDaySummary(
              uvId, nowUtc.minusHours(6 - i), nowUtc.minusHours(6 - i)));
      vocabMap.put(uvId, uv);
    }

    when(historyRepository.findReviewedWordsSummaryBetween(eq(userId), any(), any()))
        .thenReturn(summaries);
    when(repository.findByIds(any())).thenReturn(vocabMap);

    // 15 candidates available, but only 10 should be added
    var candidates = new ArrayList<UserVocabulary>();
    for (int i = 6; i <= 20; i++) {
      var word = new Word(UUID.randomUUID(), "cand" + i, "en");
      candidates.add(
          createSampleVocab(user, word, nowUtc, SrsState.REVIEW, VocabularyStatus.KNOWN));
    }
    when(repository.findReviewCandidates(userId, nowUtc, 15)).thenReturn(candidates);

    var captor = org.mockito.ArgumentCaptor.forClass(VocabularyReviewSession.class);
    when(sessionRepository.saveSession(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
    when(repository.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Collections.emptyMap());

    var result = useCase.prepareReview(15);

    var savedSession = captor.getValue();
    assertThat(savedSession.items()).hasSize(15); // 5 reviewed + 10 candidates
    assertThat(result.dailyBaseCompleted()).isEqualTo(5);
    assertThat(result.dailyBaseRemaining()).isEqualTo(10);
  }

  @Test
  @DisplayName("14.3.5.1 - B: No session + 15 reviews today -> 0 new base words incorporated")
  void testBootstrapWith15ReviewsToday() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(userTimezonePort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse("2026-09-11");
    var user = new User(userId, "Ada", "ada@example.com");

    when(sessionRepository.findSession(userId, localDate)).thenReturn(Optional.empty());

    var summaries = new ArrayList<com.soap.soap.application.model.ReviewedWordDaySummary>();
    var vocabMap = new HashMap<UUID, UserVocabulary>();
    for (int i = 1; i <= 15; i++) {
      var uvId = UUID.randomUUID();
      var word = new Word(UUID.randomUUID(), "word" + i, "en");
      var uv =
          createSampleVocab(
              user, word, nowUtc.plusDays(1), SrsState.REVIEW, VocabularyStatus.KNOWN);
      summaries.add(
          new com.soap.soap.application.model.ReviewedWordDaySummary(
              uvId, nowUtc.minusHours(16 - i), nowUtc.minusHours(16 - i)));
      vocabMap.put(uvId, uv);
    }

    when(historyRepository.findReviewedWordsSummaryBetween(eq(userId), any(), any()))
        .thenReturn(summaries);
    when(repository.findByIds(any())).thenReturn(vocabMap);

    var captor = org.mockito.ArgumentCaptor.forClass(VocabularyReviewSession.class);
    when(sessionRepository.saveSession(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    var result = useCase.prepareReview(15);

    var savedSession = captor.getValue();
    assertThat(savedSession.items()).hasSize(15);
    assertThat(result.dailyBaseCompleted()).isEqualTo(15);
    assertThat(result.dailyBaseRemaining()).isEqualTo(0);
    // All were KNOWN/REVIEW so session is complete
    assertThat(result.dailyComplete()).isTrue();
  }

  @Test
  @DisplayName(
      "14.3.5.1 - C & D: No session + 18 reviews today -> no new base, pending learning preserved in FIFO order")
  void testBootstrapWith18ReviewsAndPendingLearningFIFO() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(userTimezonePort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse("2026-09-11");
    var user = new User(userId, "Ada", "ada@example.com");

    when(sessionRepository.findSession(userId, localDate)).thenReturn(Optional.empty());

    var summaries = new ArrayList<com.soap.soap.application.model.ReviewedWordDaySummary>();
    var vocabMap = new HashMap<UUID, UserVocabulary>();

    // 16 graduated (REVIEW), 2 in LEARNING
    for (int i = 1; i <= 16; i++) {
      var uvId = UUID.randomUUID();
      var word = new Word(UUID.randomUUID(), "graduated" + i, "en");
      var uv =
          createSampleVocab(
              user, word, nowUtc.plusDays(2), SrsState.REVIEW, VocabularyStatus.KNOWN);
      summaries.add(
          new com.soap.soap.application.model.ReviewedWordDaySummary(
              uvId, nowUtc.minusHours(20 - i), nowUtc.minusHours(20 - i)));
      vocabMap.put(uvId, uv);
    }

    // Pending card 1: first reviewed 3 hours ago, last reviewed 30 mins ago
    var p1Id = UUID.randomUUID();
    var p1Word = new Word(UUID.randomUUID(), "pending1", "en");
    var p1Uv =
        createSampleVocab(
            user, p1Word, nowUtc.plusMinutes(5), SrsState.LEARNING, VocabularyStatus.LEARNING);
    summaries.add(
        new com.soap.soap.application.model.ReviewedWordDaySummary(
            p1Id, nowUtc.minusHours(3), nowUtc.minusMinutes(30)));
    vocabMap.put(p1Id, p1Uv);

    // Pending card 2: first reviewed 4 hours ago, last reviewed 10 mins ago
    var p2Id = UUID.randomUUID();
    var p2Word = new Word(UUID.randomUUID(), "pending2", "en");
    var p2Uv =
        createSampleVocab(
            user, p2Word, nowUtc.plusMinutes(10), SrsState.RELEARNING, VocabularyStatus.LEARNING);
    summaries.add(
        new com.soap.soap.application.model.ReviewedWordDaySummary(
            p2Id, nowUtc.minusHours(4), nowUtc.minusMinutes(10)));
    vocabMap.put(p2Id, p2Uv);

    // Sort summaries by firstReviewedAt ASC (as the repository query does)
    summaries.sort(
        Comparator.comparing(
            com.soap.soap.application.model.ReviewedWordDaySummary::firstReviewedAt));

    when(historyRepository.findReviewedWordsSummaryBetween(eq(userId), any(), any()))
        .thenReturn(summaries);
    when(repository.findByIds(any())).thenReturn(vocabMap);

    var captor = org.mockito.ArgumentCaptor.forClass(VocabularyReviewSession.class);
    when(sessionRepository.saveSession(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
    when(repository.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Collections.emptyMap());

    var result = useCase.prepareReview(15);

    var savedSession = captor.getValue();
    assertThat(savedSession.items()).hasSize(18); // all 18 preserved!
    assertThat(result.dailyBaseRemaining()).isEqualTo(0);
    assertThat(result.dailyComplete()).isFalse();
    assertThat(result.pendingLearningCount()).isEqualTo(2);

    // Check FIFO pending order: pending1 lastReviewedAt was -30m, pending2 lastReviewedAt was -10m
    // So pending1 gets sequence 1, pending2 gets sequence 2
    var itemP1 =
        savedSession.items().stream()
            .filter(it -> it.vocabulary().word().normalizedValue().equals("pending1"))
            .findFirst()
            .orElseThrow();
    var itemP2 =
        savedSession.items().stream()
            .filter(it -> it.vocabulary().word().normalizedValue().equals("pending2"))
            .findFirst()
            .orElseThrow();
    assertThat(itemP1.pendingQueueSequence()).isEqualTo(1L);
    assertThat(itemP2.pendingQueueSequence()).isEqualTo(2L);
    assertThat(savedSession.nextQueueSequence()).isEqualTo(3L);
  }

  @Test
  @DisplayName("14.3.5.1 - E: Next day no history -> normal new session with max 15 base words")
  void testNextDayNoHistoryCreatesNormalNewSession() {
    var nextDayClock = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC);
    var nextDayUseCase =
        new PrepareVocabularyReviewUseCase(
            currentUser,
            repository,
            historyRepository,
            sessionRepository,
            scheduler,
            nextDayClock,
            userTimezonePort,
            ratingOptionsBuilder);

    when(currentUser.requireUserId()).thenReturn(userId);
    when(userTimezonePort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);
    var nowUtc = LocalDateTime.ofInstant(nextDayClock.instant(), ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse("2026-09-12");
    var user = new User(userId, "Ada", "ada@example.com");

    when(sessionRepository.findSession(userId, localDate)).thenReturn(Optional.empty());
    // No reviews on this day
    when(historyRepository.findReviewedWordsSummaryBetween(eq(userId), any(), any()))
        .thenReturn(Collections.emptyList());

    var candidates = new ArrayList<UserVocabulary>();
    for (int i = 1; i <= 15; i++) {
      var word = new Word(UUID.randomUUID(), "cand" + i, "en");
      candidates.add(
          createSampleVocab(user, word, nowUtc, SrsState.REVIEW, VocabularyStatus.KNOWN));
    }
    when(repository.findReviewCandidates(userId, nowUtc, 15)).thenReturn(candidates);

    var captor = org.mockito.ArgumentCaptor.forClass(VocabularyReviewSession.class);
    when(sessionRepository.saveSession(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
    when(repository.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Collections.emptyMap());

    var result = nextDayUseCase.prepareReview(15);

    var savedSession = captor.getValue();
    assertThat(savedSession.items()).hasSize(15);
    assertThat(savedSession.items().get(0).introducedAt()).isNull(); // unintroduced base
    assertThat(result.dailyBaseCompleted()).isEqualTo(0);
    assertThat(result.dailyBaseRemaining()).isEqualTo(15);
  }

  @Test
  @DisplayName(
      "14.3.5.2 - Prepare review populates pendingQueueSequence and baseOrder (Section 11 A, E, F)")
  void testPrepareReviewPopulatesPendingQueueSequenceAndBaseOrder() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(userTimezonePort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse("2026-09-11");

    var user = new User(userId, "Ada", "ada@example.com");
    UUID sessionId = UUID.randomUUID();

    var wordB = new Word(UUID.randomUUID(), "cardB", "en");
    var wordC = new Word(UUID.randomUUID(), "cardC", "en");

    // Card B: nextReviewAt 12:15 (+15m, learn-ahead within 20m), seq=2
    var uvB =
        createSampleVocab(
            user, wordB, nowUtc.plusMinutes(15), SrsState.LEARNING, VocabularyStatus.LEARNING);
    // Card C: nextReviewAt 12:00 (due now), seq=3
    var uvC = createSampleVocab(user, wordC, nowUtc, SrsState.LEARNING, VocabularyStatus.LEARNING);

    var itemB =
        new VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, uvB, 2, nowUtc.minusMinutes(10), 2L);
    var itemC =
        new VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, uvC, 3, nowUtc.minusMinutes(5), 3L);

    var session =
        new VocabularyReviewSession(
            sessionId,
            userId,
            localDate,
            ReviewSessionStatus.ACTIVE,
            15,
            4L,
            nowUtc.minusHours(1),
            null,
            List.of(itemB, itemC));

    when(sessionRepository.findSession(userId, localDate)).thenReturn(Optional.of(session));
    when(repository.countDueWords(userId, nowUtc)).thenReturn(1L);
    when(repository.countTotalReviewableWords(userId, nowUtc)).thenReturn(10L);
    when(repository.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Collections.emptyMap());

    var result = useCase.prepareReview(15);

    // Section 11 A: C is due (entries) with pendingQueueSequence=3, B is learn-ahead with
    // pendingQueueSequence=2
    assertThat(result.entries()).hasSize(1);
    var entryCardC = result.entries().get(0);
    assertThat(entryCardC.word()).isEqualTo("cardC");
    assertThat(entryCardC.pendingQueueSequence()).isEqualTo(3L);
    assertThat(entryCardC.baseOrder()).isEqualTo(3);

    assertThat(result.learnAheadEntries()).hasSize(1);
    var learnAheadCardB = result.learnAheadEntries().get(0);
    assertThat(learnAheadCardB.word()).isEqualTo("cardB");
    assertThat(learnAheadCardB.pendingQueueSequence()).isEqualTo(2L);
    assertThat(learnAheadCardB.baseOrder()).isEqualTo(2);

    // Section 11 F: Exit and reprepare preserves exact same sequences
    var resultAfterReenter = useCase.prepareReview(15);
    assertThat(resultAfterReenter.entries().get(0).pendingQueueSequence()).isEqualTo(3L);
    assertThat(resultAfterReenter.learnAheadEntries().get(0).pendingQueueSequence()).isEqualTo(2L);
  }

  @Test
  @DisplayName(
      "14.3.5.2 - Base cards have baseOrder populated and pendingQueueSequence null before introduction (Section 11 E)")
  void testBaseCardsHaveBaseOrderAndNullPendingQueueSequenceBeforeIntroduction() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(userTimezonePort.resolveUserZoneId(userId)).thenReturn(ZoneOffset.UTC);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse("2026-09-11");

    var user = new User(userId, "Ada", "ada@example.com");
    UUID sessionId = UUID.randomUUID();
    var word1 = new Word(UUID.randomUUID(), "card1", "en");
    var uv1 = createSampleVocab(user, word1, nowUtc, SrsState.NEW, VocabularyStatus.LEARNING);
    var item1 = new VocabularyReviewSessionItem(UUID.randomUUID(), sessionId, uv1, 1, null, null);

    var session =
        new VocabularyReviewSession(
            sessionId,
            userId,
            localDate,
            ReviewSessionStatus.ACTIVE,
            15,
            1L,
            nowUtc,
            null,
            List.of(item1));

    when(sessionRepository.findSession(userId, localDate)).thenReturn(Optional.of(session));
    when(repository.countDueWords(userId, nowUtc)).thenReturn(1L);
    when(repository.countTotalReviewableWords(userId, nowUtc)).thenReturn(10L);
    when(repository.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Collections.emptyMap());

    var result = useCase.prepareReview(15);

    assertThat(result.entries()).hasSize(1);
    var baseItem = result.entries().get(0);
    assertThat(baseItem.word()).isEqualTo("card1");
    assertThat(baseItem.baseOrder()).isEqualTo(1);
    assertThat(baseItem.pendingQueueSequence()).isNull();
  }

  private UserVocabulary createSampleVocab(
      User user, Word word, LocalDateTime nextReview, SrsState srsState, VocabularyStatus status) {
    return new UserVocabulary(
        UUID.randomUUID(),
        user,
        word,
        status,
        LocalDateTime.now().minusDays(10),
        status == VocabularyStatus.KNOWN ? LocalDateTime.now().minusDays(5) : null,
        status == VocabularyStatus.KNOWN ? 86400L : 0L,
        status == VocabularyStatus.KNOWN ? 1 : 0,
        status == VocabularyStatus.KNOWN ? LocalDateTime.now().minusDays(5) : null,
        nextReview,
        srsState,
        2.5,
        4.0,
        1,
        0);
  }
}
