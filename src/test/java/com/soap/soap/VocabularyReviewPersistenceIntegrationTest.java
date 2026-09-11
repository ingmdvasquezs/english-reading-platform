package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.WordRepositoryPort;
import com.soap.soap.domain.model.ReviewAssessment;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
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

    // Due 1: due 2 hours ago
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
            nowUtc.minusHours(2)));

    // Due 2: due 1 hour ago
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
            nowUtc.minusHours(1)));

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
            nowUtc.plusDays(1)));

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
            null));

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
            null));

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
            null));

    // Assert counts: NEW is excluded from reviewable words
    assertThat(vocabulary.countDueWords(userA.id(), nowUtc)).isEqualTo(2L);
    assertThat(vocabulary.countTotalReviewableWords(userA.id(), nowUtc)).isEqualTo(3L);

    // Assert candidate order: Due1 (earlier nextReviewAt), Due2, Learning (NEW is excluded)
    var candidates = vocabulary.findReviewCandidates(userA.id(), nowUtc, 10);
    assertThat(candidates).hasSize(3);
    assertThat(candidates.get(0).word().normalizedValue()).isEqualTo("dueone");
    assertThat(candidates.get(1).word().normalizedValue()).isEqualTo("duetwo");
    assertThat(candidates.get(2).word().normalizedValue()).isEqualTo("learningunprog");

    // Test limit 2
    var limited = vocabulary.findReviewCandidates(userA.id(), nowUtc, 2);
    assertThat(limited).hasSize(2);
    assertThat(limited.get(0).word().normalizedValue()).isEqualTo("dueone");
    assertThat(limited.get(1).word().normalizedValue()).isEqualTo("duetwo");
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
  void spacedRepetitionLadderPersistenceAndManualTransitions() {
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var word = words.resolve("ladderword", "en");

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
            nowUtc);
    var saved = vocabulary.save(initial);
    assertThat(saved.reviewStage()).isEqualTo(0);

    // Apply REMEMBERED: advances to stage 1 (+3 days)
    var reviewed1 =
        vocabulary.save(saved.applyReviewAssessment(ReviewAssessment.REMEMBERED, clock));
    assertThat(reviewed1.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(reviewed1.reviewStage()).isEqualTo(1);
    assertThat(reviewed1.nextReviewAt()).isEqualTo(nowUtc.plusDays(3));

    // Apply REMEMBERED again: advances to stage 2 (+7 days)
    var reviewed2 =
        vocabulary.save(reviewed1.applyReviewAssessment(ReviewAssessment.REMEMBERED, clock));
    assertThat(reviewed2.reviewStage()).isEqualTo(2);
    assertThat(reviewed2.nextReviewAt()).isEqualTo(nowUtc.plusDays(7));

    // Apply FORGOT: resets to stage 0 (+1 day, status LEARNING)
    var forgot = vocabulary.save(reviewed2.applyReviewAssessment(ReviewAssessment.FORGOT, clock));
    assertThat(forgot.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(forgot.reviewStage()).isEqualTo(0);
    assertThat(forgot.nextReviewAt()).isEqualTo(nowUtc.plusDays(1));
    assertThat(forgot.learnedAt()).isNull();

    // Manual transition to KNOWN: reviewStage = max(3, actual) = 3, nextReviewAt = +14 days
    var manualKnown = vocabulary.save(forgot.changeStatus(VocabularyStatus.KNOWN, clock));
    assertThat(manualKnown.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(manualKnown.reviewStage()).isEqualTo(3);
    assertThat(manualKnown.nextReviewAt()).isEqualTo(nowUtc.plusDays(14));
    assertThat(manualKnown.learnedAt()).isEqualTo(nowUtc);
  }
}
