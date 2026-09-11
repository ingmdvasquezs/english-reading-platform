package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.WordRepositoryPort;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
@Transactional
class UserVocabularyPersistenceIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private UserVocabularyRepositoryPort vocabulary;
  @Autowired private WordRepositoryPort words;
  @Autowired private UserRepositoryPort users;

  private User userA;
  private User userB;

  @BeforeEach
  void setUp() {
    userA =
        users.save(
            new User(null, "User A", "user-a-" + UUID.randomUUID() + "@example.com", "hash", null));
    userB =
        users.save(
            new User(null, "User B", "user-b-" + UUID.randomUUID() + "@example.com", "hash", null));
  }

  @Test
  void handlesEmptyVocabularyCleanly() {
    var result = vocabulary.findByUserIdAndCriteria(userA.id(), null, null, new PageRequest(0, 20));
    var summary = vocabulary.countSummaryByUserId(userA.id());

    assertThat(result.content()).isEmpty();
    assertThat(result.totalElements()).isEqualTo(0L);
    assertThat(summary.totalCount()).isEqualTo(0L);
    assertThat(summary.newCount()).isEqualTo(0L);
    assertThat(summary.learningCount()).isEqualTo(0L);
    assertThat(summary.knownCount()).isEqualTo(0L);
    assertThat(summary.ignoredCount()).isEqualTo(0L);
  }

  @Test
  void supportsPaginationAndOrdersByFirstSeenAtDescWithDeterministicTieBreaker() {
    var baseTime = LocalDateTime.of(2026, 9, 1, 10, 0, 0);

    // Oldest
    createEntry(userA, "apple", "en", VocabularyStatus.KNOWN, baseTime.minusDays(3));
    // Same timestamp to test tie-breaker (normalizedValue ASC: "banana" before "cherry")
    createEntry(userA, "cherry", "en", VocabularyStatus.LEARNING, baseTime);
    createEntry(userA, "banana", "en", VocabularyStatus.LEARNING, baseTime);
    // Newest
    createEntry(userA, "date", "en", VocabularyStatus.NEW, baseTime.plusDays(1));

    // Page 0: size 2 -> "date" then "banana"
    var page0 = vocabulary.findByUserIdAndCriteria(userA.id(), null, null, new PageRequest(0, 2));
    assertThat(page0.totalElements()).isEqualTo(4L);
    assertThat(page0.content())
        .extracting(entry -> entry.word().normalizedValue())
        .containsExactly("date", "banana");

    // Page 1: size 2 -> "cherry" then "apple"
    var page1 = vocabulary.findByUserIdAndCriteria(userA.id(), null, null, new PageRequest(1, 2));
    assertThat(page1.content())
        .extracting(entry -> entry.word().normalizedValue())
        .containsExactly("cherry", "apple");
  }

  @Test
  void filtersByEachVocabularyStatus() {
    var now = LocalDateTime.now();
    createEntry(userA, "word-new", "en", VocabularyStatus.NEW, now);
    createEntry(userA, "word-learning", "en", VocabularyStatus.LEARNING, now);
    createEntry(userA, "word-known", "en", VocabularyStatus.KNOWN, now);
    createEntry(userA, "word-ignored", "en", VocabularyStatus.IGNORED, now);

    for (var status : VocabularyStatus.values()) {
      var result =
          vocabulary.findByUserIdAndCriteria(userA.id(), status, null, new PageRequest(0, 10));
      assertThat(result.totalElements()).isEqualTo(1L);
      assertThat(result.content().getFirst().status()).isEqualTo(status);
    }

    var summary = vocabulary.countSummaryByUserId(userA.id());
    assertThat(summary.totalCount()).isEqualTo(4L);
    assertThat(summary.newCount()).isEqualTo(1L);
    assertThat(summary.learningCount()).isEqualTo(1L);
    assertThat(summary.knownCount()).isEqualTo(1L);
    assertThat(summary.ignoredCount()).isEqualTo(1L);
  }

  @Test
  void searchesByWordPrefix() {
    var now = LocalDateTime.now();
    createEntry(userA, "journey", "en", VocabularyStatus.LEARNING, now);
    createEntry(userA, "journal", "en", VocabularyStatus.LEARNING, now);
    createEntry(userA, "journalist", "en", VocabularyStatus.KNOWN, now);
    createEntry(userA, "joy", "en", VocabularyStatus.KNOWN, now);
    createEntry(userA, "apple", "en", VocabularyStatus.NEW, now);

    var resultJour =
        vocabulary.findByUserIdAndCriteria(userA.id(), null, "jour", new PageRequest(0, 10));
    assertThat(resultJour.totalElements()).isEqualTo(3L);
    assertThat(resultJour.content())
        .extracting(entry -> entry.word().normalizedValue())
        .containsExactlyInAnyOrder("journey", "journal", "journalist");

    var resultJourna =
        vocabulary.findByUserIdAndCriteria(userA.id(), null, "journa", new PageRequest(0, 10));
    assertThat(resultJourna.totalElements()).isEqualTo(2L);
    assertThat(resultJourna.content())
        .extracting(entry -> entry.word().normalizedValue())
        .containsExactlyInAnyOrder("journal", "journalist");

    var resultNoMatch =
        vocabulary.findByUserIdAndCriteria(userA.id(), null, "xyz", new PageRequest(0, 10));
    assertThat(resultNoMatch.totalElements()).isEqualTo(0L);
    assertThat(resultNoMatch.content()).isEmpty();
  }

  @Test
  void combinesStatusFilterAndPrefixSearch() {
    var now = LocalDateTime.now();
    createEntry(userA, "journey", "en", VocabularyStatus.LEARNING, now);
    createEntry(userA, "journal", "en", VocabularyStatus.KNOWN, now);
    createEntry(userA, "apple", "en", VocabularyStatus.LEARNING, now);

    var filtered =
        vocabulary.findByUserIdAndCriteria(
            userA.id(), VocabularyStatus.LEARNING, "jour", new PageRequest(0, 10));

    assertThat(filtered.totalElements()).isEqualTo(1L);
    assertThat(filtered.content().getFirst().word().normalizedValue()).isEqualTo("journey");
    assertThat(filtered.content().getFirst().status()).isEqualTo(VocabularyStatus.LEARNING);

    // Summary reflects global totals, not filtered query
    var summary = vocabulary.countSummaryByUserId(userA.id());
    assertThat(summary.totalCount()).isEqualTo(3L);
    assertThat(summary.learningCount()).isEqualTo(2L);
    assertThat(summary.knownCount()).isEqualTo(1L);
  }

  @Test
  void enforcesStrictUserIsolation() {
    var now = LocalDateTime.now();
    createEntry(userA, "shared", "en", VocabularyStatus.LEARNING, now);
    createEntry(userA, "secret-a", "en", VocabularyStatus.KNOWN, now);

    createEntry(userB, "shared", "en", VocabularyStatus.NEW, now);
    createEntry(userB, "secret-b", "en", VocabularyStatus.IGNORED, now);

    var resultA =
        vocabulary.findByUserIdAndCriteria(userA.id(), null, null, new PageRequest(0, 10));
    assertThat(resultA.totalElements()).isEqualTo(2L);
    assertThat(resultA.content())
        .extracting(entry -> entry.word().normalizedValue())
        .containsExactlyInAnyOrder("shared", "secret-a");

    var summaryA = vocabulary.countSummaryByUserId(userA.id());
    assertThat(summaryA.totalCount()).isEqualTo(2L);
    assertThat(summaryA.learningCount()).isEqualTo(1L);
    assertThat(summaryA.knownCount()).isEqualTo(1L);
    assertThat(summaryA.newCount()).isEqualTo(0L);

    var resultB =
        vocabulary.findByUserIdAndCriteria(userB.id(), null, null, new PageRequest(0, 10));
    assertThat(resultB.totalElements()).isEqualTo(2L);
    assertThat(resultB.content())
        .extracting(entry -> entry.word().normalizedValue())
        .containsExactlyInAnyOrder("shared", "secret-b");

    var summaryB = vocabulary.countSummaryByUserId(userB.id());
    assertThat(summaryB.totalCount()).isEqualTo(2L);
    assertThat(summaryB.newCount()).isEqualTo(1L);
    assertThat(summaryB.ignoredCount()).isEqualTo(1L);
  }

  @Test
  void aggregatesStatusCountsWithMissingStatusesZero() {
    var now = LocalDateTime.now();
    createEntry(userA, "word1", "en", VocabularyStatus.KNOWN, now);
    createEntry(userA, "word2", "en", VocabularyStatus.KNOWN, now);
    createEntry(userA, "word3", "en", VocabularyStatus.LEARNING, now);

    var summary = vocabulary.countSummaryByUserId(userA.id());
    assertThat(summary.totalCount()).isEqualTo(3L);
    assertThat(summary.knownCount()).isEqualTo(2L);
    assertThat(summary.learningCount()).isEqualTo(1L);
    assertThat(summary.newCount()).isEqualTo(0L);
    assertThat(summary.ignoredCount()).isEqualTo(0L);
  }

  private void createEntry(
      User user, String wordValue, String language, VocabularyStatus status, LocalDateTime seenAt) {
    var word = words.resolve(wordValue, language);
    vocabulary.save(
        new UserVocabulary(
            null, user, word, status, seenAt, status == VocabularyStatus.KNOWN ? seenAt : null));
  }
}
