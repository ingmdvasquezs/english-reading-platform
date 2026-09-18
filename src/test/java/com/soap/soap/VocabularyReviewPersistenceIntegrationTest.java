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
  @Autowired private FsrsScheduler scheduler;
  @Autowired private JdbcTemplate jdbc;

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

    // Apply GOOD: graduates to REVIEW (+4 days)
    var reviewed1 = vocabulary.save(saved.applyRating(ReviewRating.GOOD, clock, scheduler));
    assertThat(reviewed1.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(reviewed1.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(reviewed1.repetitions()).isEqualTo(1);
    assertThat(reviewed1.lapses()).isEqualTo(0);
    assertThat(reviewed1.nextReviewAt()).isEqualTo(nowUtc.plusDays(4));

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
            4 * 86400L,
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

    // Manual transition to KNOWN preserves integrity
    // SRS V2: nextReviewAt is derived from post-lapse stability, NOT from Leitner +14d.
    // After LEARNING(S=0.4872) → GOOD → REVIEW(S=3.7145) → AGAIN, the post-lapse stability
    // is approximately 1.1 days (canonical FSRS-4.5 lapse formula with S=3.7145, D=7.6214, R=1.0).
    // changeStatus(KNOWN) uses round(stability) = round(1.1) = 1 day.
    var manualKnown = vocabulary.save(lapsed.changeStatus(VocabularyStatus.KNOWN, clock));
    assertThat(manualKnown.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(manualKnown.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(manualKnown.learnedAt()).isEqualTo(nowUtc);
    // SRS V2: nextReviewAt from stability — must be short (post-lapse), NOT the Leitner +14d.
    assertThat(manualKnown.nextReviewAt()).isNotEqualTo(nowUtc.plusDays(14));
    assertThat(manualKnown.nextReviewAt()).isAfterOrEqualTo(nowUtc.plusDays(1));
    assertThat(manualKnown.nextReviewAt()).isBeforeOrEqualTo(nowUtc.plusDays(7));
  }
}
