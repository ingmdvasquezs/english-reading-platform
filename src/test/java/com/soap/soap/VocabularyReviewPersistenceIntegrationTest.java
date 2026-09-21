package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.VocabularyReviewHistoryEntry;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyReviewHistoryRepositoryPort;
import com.soap.soap.application.port.out.WordRepositoryPort;
import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.model.SrsState;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.service.FsrsScheduler;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
@Transactional
class VocabularyReviewPersistenceIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private UserVocabularyRepositoryPort vocabulary;
  @Autowired private WordRepositoryPort words;
  @Autowired private UserRepositoryPort users;
  @Autowired private UserVocabularyReviewHistoryRepositoryPort historyRepository;

  @Autowired
  private com.soap.soap.application.port.out.VocabularyReviewSessionRepositoryPort sessionPort;

  @Autowired private FsrsScheduler scheduler;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

  private final Clock clock = Clock.fixed(Instant.parse("2026-09-11T12:00:00Z"), ZoneOffset.UTC);
  private User userA;
  private User userB;

  @BeforeEach
  void setUp() {
    userA =
        users.save(
            new User(null, "Ada", "ada-" + UUID.randomUUID() + "@example.com", "hash", null));
    userB =
        users.save(
            new User(
                null, "Charles", "charles-" + UUID.randomUUID() + "@example.com", "hash", null));
  }

  @Test
  void prioritizedReviewSelectionAndCounts() {
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

    var wordDue1 = words.resolve("dueone", "en");
    var wordDue2 = words.resolve("duetwo", "en");
    var wordFuture = words.resolve("future", "en");
    var wordLearning = words.resolve("learningunprog", "en");
    var wordNew = words.resolve("newunprog", "en");
    var wordIgnored = words.resolve("ignoredword", "en");

    // Due 1: due 2 hours ago (LEARNING)
    vocabulary.save(
        new UserVocabulary(
            null,
            userA,
            wordDue1,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(5),
            null,
            0L,
            0,
            nowUtc.minusDays(1),
            nowUtc.minusHours(2),
            SrsState.LEARNING,
            0.4872,
            7.6214,
            1,
            0));

    // Due 2: due 1 hour ago (REVIEW)
    vocabulary.save(
        new UserVocabulary(
            null,
            userA,
            wordDue2,
            VocabularyStatus.KNOWN,
            nowUtc.minusDays(7),
            nowUtc.minusDays(7),
            0L,
            1,
            nowUtc.minusDays(3),
            nowUtc.minusHours(1),
            SrsState.REVIEW,
            3.7145,
            3.9320,
            2,
            0));

    // Future: due tomorrow (should be excluded)
    vocabulary.save(
        new UserVocabulary(
            null,
            userA,
            wordFuture,
            VocabularyStatus.KNOWN,
            nowUtc.minusDays(2),
            nowUtc.minusDays(2),
            0L,
            1,
            nowUtc.minusDays(1),
            nowUtc.plusDays(1),
            SrsState.REVIEW,
            3.7145,
            3.9320,
            1,
            0));

    // Unprogrammed learning
    vocabulary.save(
        new UserVocabulary(
            null,
            userA,
            wordLearning,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(4),
            null,
            0L,
            0,
            null,
            null,
            SrsState.LEARNING,
            0.4872,
            7.6214,
            0,
            0));

    // Unprogrammed new
    vocabulary.save(
        new UserVocabulary(
            null,
            userA,
            wordNew,
            VocabularyStatus.NEW,
            nowUtc.minusDays(3),
            null,
            0L,
            0,
            null,
            null,
            SrsState.NEW,
            0.0,
            5.0,
            0,
            0));

    // Ignored (excluded)
    vocabulary.save(
        new UserVocabulary(
            null,
            userA,
            wordIgnored,
            VocabularyStatus.IGNORED,
            nowUtc.minusDays(1),
            null,
            0L,
            0,
            null,
            null,
            SrsState.NEW,
            0.0,
            5.0,
            0,
            0));

    // Assert counts: NEW is excluded from reviewable words
    assertThat(vocabulary.countDueWords(userA.id(), nowUtc)).isEqualTo(2L);
    assertThat(vocabulary.countTotalReviewableWords(userA.id(), nowUtc)).isEqualTo(3L);

    // Assert candidate order: Due1 (LEARNING due), Due2 (REVIEW due), Learning (LEARNING
    // unprogrammed)
    var candidates = vocabulary.findReviewCandidates(userA.id(), nowUtc, 10);
    assertThat(candidates).hasSize(3);
    assertThat(candidates.get(0).word().normalizedValue()).isEqualTo("dueone");
    assertThat(candidates.get(1).word().normalizedValue()).isEqualTo("learningunprog");
    assertThat(candidates.get(2).word().normalizedValue()).isEqualTo("duetwo");

    // Test limit 2
    var limited = vocabulary.findReviewCandidates(userA.id(), nowUtc, 2);
    assertThat(limited).hasSize(2);
    assertThat(limited.get(0).word().normalizedValue()).isEqualTo("dueone");
    assertThat(limited.get(1).word().normalizedValue()).isEqualTo("learningunprog");
  }

  @Test
  void postMigrationQueryFindsZeroFragmentedEnglishWords() {
    var count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM words WHERE LOWER(language) != 'en' AND (LOWER(language) LIKE 'en-%' OR LOWER(language) LIKE 'en_%')",
            Long.class);
    assertThat(count).isEqualTo(0L);
  }

  @Test
  void srsV2PersistenceAndHistoryRecording() {
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var word = words.resolve("srsword", "en");

    // Initial state: learning
    var initial =
        new UserVocabulary(
            null,
            userA,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(5),
            null,
            0L,
            0,
            null,
            nowUtc,
            SrsState.LEARNING,
            0.4872,
            7.6214,
            0,
            0);
    var saved = vocabulary.save(initial);
    assertThat(saved.srsState()).isEqualTo(SrsState.LEARNING);
    assertThat(saved.stability()).isEqualTo(0.4872);
    assertThat(saved.difficulty()).isEqualTo(7.6214);

    // Apply GOOD: graduates to REVIEW (+1 day under FASE 14.3.7 binary policy), status remains
    // LEARNING!
    var reviewed1 = vocabulary.save(saved.applyRating(ReviewRating.GOOD, clock, scheduler));
    assertThat(reviewed1.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(reviewed1.learnedAt()).isNull();
    assertThat(reviewed1.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(reviewed1.repetitions()).isEqualTo(1);
    assertThat(reviewed1.lapses()).isEqualTo(0);
    assertThat(reviewed1.nextReviewAt()).isEqualTo(nowUtc.plusDays(1));

    // Record history
    var historyEntry =
        new VocabularyReviewHistoryEntry(
            UUID.randomUUID(),
            reviewed1.id(),
            userA.id(),
            nowUtc,
            ReviewRating.GOOD,
            SrsState.LEARNING,
            SrsState.REVIEW,
            0L,
            86400L,
            0.4872,
            3.7145,
            7.6214,
            5.1618,
            0.0,
            0.0);
    historyRepository.recordReviewHistory(historyEntry);

    var historyCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_vocabulary_review_history WHERE user_vocabulary_id = ?",
            Long.class,
            reviewed1.id());
    assertThat(historyCount).isEqualTo(1L);

    // Apply AGAIN: lapse to RELEARNING (status LEARNING, +10 min step, lapses = 1)
    var lapsed = vocabulary.save(reviewed1.applyRating(ReviewRating.AGAIN, clock, scheduler));
    assertThat(lapsed.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(lapsed.srsState()).isEqualTo(SrsState.RELEARNING);
    assertThat(lapsed.lapses()).isEqualTo(1);
    assertThat(lapsed.repetitions()).isEqualTo(2);
    assertThat(lapsed.nextReviewAt()).isEqualTo(nowUtc.plusSeconds(600L));

    // Manual transition to KNOWN preserves SRS memory state intact!
    // FASE 14.3.7.1 Decoupling: changeStatus(KNOWN) sets status=KNOWN and learnedAt=nowUtc,
    // while strictly preserving srsState, stability, difficulty, repetitions, lapses, and
    // nextReviewAt.
    var manualKnown = vocabulary.save(lapsed.changeStatus(VocabularyStatus.KNOWN, clock));
    assertThat(manualKnown.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(manualKnown.srsState()).isEqualTo(SrsState.RELEARNING);
    assertThat(manualKnown.learnedAt()).isEqualTo(nowUtc);
    assertThat(manualKnown.nextReviewAt()).isEqualTo(lapsed.nextReviewAt());
    assertThat(manualKnown.stability()).isEqualTo(lapsed.stability());
    assertThat(manualKnown.repetitions()).isEqualTo(lapsed.repetitions());
    assertThat(manualKnown.lapses()).isEqualTo(lapsed.lapses());
  }

  @Test
  void testSessionPersistenceAndUniqueConstraintPerUserAndDate() {
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var localDate = java.time.LocalDate.parse("2026-09-11");

    var w1 = words.resolve("sessionWordOne", "en");
    var w2 = words.resolve("sessionWordTwo", "en");

    var uv1 =
        vocabulary.save(
            new UserVocabulary(
                null,
                userA,
                w1,
                VocabularyStatus.LEARNING,
                nowUtc.minusDays(2),
                null,
                0L,
                0,
                null,
                nowUtc,
                SrsState.LEARNING,
                0.4872,
                7.6214,
                0,
                0));
    var uv2 =
        vocabulary.save(
            new UserVocabulary(
                null,
                userA,
                w2,
                VocabularyStatus.KNOWN,
                nowUtc.minusDays(5),
                nowUtc.minusDays(5),
                86400L,
                1,
                nowUtc.minusDays(5),
                nowUtc,
                SrsState.REVIEW,
                2.5,
                4.0,
                1,
                0));

    UUID sessionId = UUID.randomUUID();
    var item1 =
        new com.soap.soap.domain.model.VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, uv1, 1, null, null);
    var item2 =
        new com.soap.soap.domain.model.VocabularyReviewSessionItem(
            UUID.randomUUID(), sessionId, uv2, 2, null, null);

    var session =
        new com.soap.soap.domain.model.VocabularyReviewSession(
            sessionId,
            userA.id(),
            localDate,
            com.soap.soap.domain.model.ReviewSessionStatus.ACTIVE,
            15,
            1L,
            nowUtc,
            null,
            List.of(item1, item2));

    // Save session
    var savedSession = sessionPort.saveSession(session);
    assertThat(savedSession).isNotNull();

    // Verify DB records via JDBC
    var sessionCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM vocabulary_review_sessions WHERE user_id = ? AND local_review_date = ?",
            Long.class,
            userA.id(),
            localDate);
    assertThat(sessionCount).isEqualTo(1L);

    var itemsCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM vocabulary_review_session_items WHERE session_id = ?",
            Long.class,
            savedSession.id());
    assertThat(itemsCount).isEqualTo(2L);

    // Retrieve via port
    var retrieved = sessionPort.findSession(userA.id(), localDate);
    assertThat(retrieved).isPresent();
    assertThat(retrieved.get().items()).hasSize(2);
    assertThat(retrieved.get().items().get(0).baseOrder()).isEqualTo(1);
    assertThat(retrieved.get().items().get(1).baseOrder()).isEqualTo(2);

    // Update with FIFO pendingQueueSequence
    var updatedItem1 = item1.withIntroducedAt(nowUtc).withPendingQueueSequence(1L);
    var updatedSession =
        savedSession.withNextQueueSequence(2L).withItems(List.of(updatedItem1, item2));
    sessionPort.saveSession(updatedSession);

    var reloaded = sessionPort.findSession(userA.id(), localDate).orElseThrow();
    assertThat(reloaded.nextQueueSequence()).isEqualTo(2L);
    assertThat(reloaded.items().get(0).pendingQueueSequence()).isEqualTo(1L);
    assertThat(reloaded.items().get(0).introducedAt()).isNotNull();

    // Verify unique constraint: user_id + local_review_date
    var sessionCountFinal =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM vocabulary_review_sessions WHERE user_id = ? AND local_review_date = ?",
            Long.class,
            userA.id(),
            localDate);
    assertThat(sessionCountFinal).isEqualTo(1L);
  }

  @Test
  void testFindReviewedWordsSummaryBetween() {
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var word1 = words.resolve("histWord1", "en");
    var word2 = words.resolve("histWord2", "en");

    var uv1 =
        vocabulary.save(
            new UserVocabulary(
                null,
                userA,
                word1,
                VocabularyStatus.LEARNING,
                nowUtc,
                null,
                0L,
                0,
                null,
                nowUtc,
                SrsState.LEARNING,
                0.5,
                5.0,
                0,
                0));
    var uv2 =
        vocabulary.save(
            new UserVocabulary(
                null,
                userA,
                word2,
                VocabularyStatus.LEARNING,
                nowUtc,
                null,
                0L,
                0,
                null,
                nowUtc,
                SrsState.LEARNING,
                0.5,
                5.0,
                0,
                0));

    var dayStart = nowUtc.minusHours(5);
    var dayEnd = nowUtc.plusHours(5);

    // uv1: reviewed at -3h and -1h
    historyRepository.recordReviewHistory(
        new VocabularyReviewHistoryEntry(
            UUID.randomUUID(),
            uv1.id(),
            userA.id(),
            nowUtc.minusHours(3),
            ReviewRating.AGAIN,
            SrsState.LEARNING,
            SrsState.LEARNING,
            0L,
            600L,
            0.5,
            0.5,
            5.0,
            5.0,
            0.0,
            0.0));
    historyRepository.recordReviewHistory(
        new VocabularyReviewHistoryEntry(
            UUID.randomUUID(),
            uv1.id(),
            userA.id(),
            nowUtc.minusHours(1),
            ReviewRating.GOOD,
            SrsState.LEARNING,
            SrsState.REVIEW,
            600L,
            86400L,
            0.5,
            2.0,
            5.0,
            4.0,
            0.1,
            0.01));

    // uv2: reviewed at -2h
    historyRepository.recordReviewHistory(
        new VocabularyReviewHistoryEntry(
            UUID.randomUUID(),
            uv2.id(),
            userA.id(),
            nowUtc.minusHours(2),
            ReviewRating.AGAIN,
            SrsState.LEARNING,
            SrsState.LEARNING,
            0L,
            600L,
            0.5,
            0.5,
            5.0,
            5.0,
            0.0,
            0.0));

    var summaries = historyRepository.findReviewedWordsSummaryBetween(userA.id(), dayStart, dayEnd);
    assertThat(summaries).hasSize(2);

    // Ordered by min(reviewedAt) ASC: uv1 first reviewed at -3h, uv2 at -2h
    assertThat(summaries.get(0).userVocabularyId()).isEqualTo(uv1.id());
    assertThat(summaries.get(0).firstReviewedAt()).isEqualTo(nowUtc.minusHours(3));
    assertThat(summaries.get(0).lastReviewedAt()).isEqualTo(nowUtc.minusHours(1));

    assertThat(summaries.get(1).userVocabularyId()).isEqualTo(uv2.id());
    assertThat(summaries.get(1).firstReviewedAt()).isEqualTo(nowUtc.minusHours(2));
    assertThat(summaries.get(1).lastReviewedAt()).isEqualTo(nowUtc.minusHours(2));
  }

  @Test
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void testConcurrentSessionCreationPreventDuplication() throws Exception {
    var user =
        users.save(
            new User(null, "ConcUser", "conc-" + UUID.randomUUID() + "@example.com", "hash", null));
    var localDate = java.time.LocalDate.parse("2026-09-15");
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

    int threadCount = 2;
    var executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
    var readyLatch = new java.util.concurrent.CountDownLatch(threadCount);
    var startLatch = new java.util.concurrent.CountDownLatch(1);
    var doneLatch = new java.util.concurrent.CountDownLatch(threadCount);

    var tt = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    tt.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    var errors = new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            readyLatch.countDown();
            try {
              startLatch.await();
              tt.execute(
                  status -> {
                    sessionPort.acquireSessionCreationLock(user.id());
                    var existing = sessionPort.findSession(user.id(), localDate);
                    if (existing.isEmpty()) {
                      var session =
                          new com.soap.soap.domain.model.VocabularyReviewSession(
                              null,
                              user.id(),
                              localDate,
                              com.soap.soap.domain.model.ReviewSessionStatus.ACTIVE,
                              15,
                              1L,
                              nowUtc,
                              null,
                              List.of());
                      sessionPort.saveSession(session);
                    }
                    return null;
                  });
            } catch (Throwable t) {
              errors.add(t);
            } finally {
              doneLatch.countDown();
            }
          });
    }

    readyLatch.await();
    startLatch.countDown();
    doneLatch.await();
    executor.shutdown();

    assertThat(errors).isEmpty();

    var count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM vocabulary_review_sessions WHERE user_id = ? AND local_review_date = ?",
            Long.class,
            user.id(),
            localDate);
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void testConcurrentQueueSequenceAllocationNoDuplicates() throws Exception {
    var user =
        users.save(
            new User(null, "SeqUser", "seq-" + UUID.randomUUID() + "@example.com", "hash", null));
    var localDate = java.time.LocalDate.parse("2026-09-16");
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

    var tt = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    tt.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    // Initial session
    tt.execute(
        status -> {
          var session =
              new com.soap.soap.domain.model.VocabularyReviewSession(
                  null,
                  user.id(),
                  localDate,
                  com.soap.soap.domain.model.ReviewSessionStatus.ACTIVE,
                  15,
                  1L,
                  nowUtc,
                  null,
                  List.of());
          return sessionPort.saveSession(session);
        });

    int threadCount = 2;
    var executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
    var readyLatch = new java.util.concurrent.CountDownLatch(threadCount);
    var startLatch = new java.util.concurrent.CountDownLatch(1);
    var doneLatch = new java.util.concurrent.CountDownLatch(threadCount);

    var allocatedSeqs = new java.util.concurrent.ConcurrentLinkedQueue<Long>();
    var errors = new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            readyLatch.countDown();
            try {
              startLatch.await();
              tt.execute(
                  status -> {
                    var session =
                        sessionPort.findSessionForUpdate(user.id(), localDate).orElseThrow();
                    long assigned = session.nextQueueSequence();
                    allocatedSeqs.add(assigned);
                    var updated = session.withNextQueueSequence(assigned + 1L);
                    sessionPort.saveSession(updated);
                    return null;
                  });
            } catch (Throwable t) {
              errors.add(t);
            } finally {
              doneLatch.countDown();
            }
          });
    }

    readyLatch.await();
    startLatch.countDown();
    doneLatch.await();
    executor.shutdown();

    assertThat(errors).isEmpty();
    assertThat(allocatedSeqs).containsExactlyInAnyOrder(1L, 2L);

    var finalSession = sessionPort.findSession(user.id(), localDate).orElseThrow();
    assertThat(finalSession.nextQueueSequence()).isEqualTo(3L);
  }
}
