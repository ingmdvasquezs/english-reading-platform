package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.ReadingLexicalEvidence;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.ReadingWordFrequencyRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.WordRepositoryPort;
import com.soap.soap.application.service.ReadingLexicalIndexer;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
class ReadingWordFrequencyPersistenceAdapterIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private ReadingWordFrequencyRepositoryPort frequencyRepository;
  @Autowired private ReadingLexicalIndexer indexer;
  @Autowired private ReadingRepositoryPort readingRepository;
  @Autowired private UserRepositoryPort userRepository;
  @Autowired private WordRepositoryPort wordRepository;
  @Autowired private UserVocabularyRepositoryPort userVocabularyRepository;

  @Test
  @DisplayName(
      "Aggregated lexical evidence correctly computes tokens, unique counts and handles IGNORED")
  void testAggregatedLexicalEvidenceCalculation() {
    User user =
        userRepository.save(
            new User(
                null,
                "Lexical User " + UUID.randomUUID(),
                "lexical." + UUID.randomUUID() + "@example.com",
                "hashed",
                null));

    // Words in user vocabulary:
    // "sun" -> KNOWN
    // "sky" -> LEARNING
    // "bird" -> NEW
    // "the" -> IGNORED
    // Words in reading NOT in user vocabulary:
    // "clouds", "fly" -> UNCLASSIFIED
    Word sun = wordRepository.resolve("sun", "en");
    Word sky = wordRepository.resolve("sky", "en");
    Word bird = wordRepository.resolve("bird", "en");
    Word the = wordRepository.resolve("the", "en");

    LocalDateTime now = LocalDateTime.now();
    userVocabularyRepository.save(
        new UserVocabulary(null, user, sun, VocabularyStatus.KNOWN, now, now));
    userVocabularyRepository.save(
        new UserVocabulary(null, user, sky, VocabularyStatus.LEARNING, now, null));
    userVocabularyRepository.save(
        new UserVocabulary(null, user, bird, VocabularyStatus.NEW, now, null));
    userVocabularyRepository.save(
        new UserVocabulary(null, user, the, VocabularyStatus.IGNORED, now, null));

    // Text: "The sun sun sky bird clouds clouds fly."
    // Tokens:
    // "the" (1x, IGNORED)
    // "sun" (2x, KNOWN)
    // "sky" (1x, LEARNING)
    // "bird" (1x, NEW)
    // "clouds" (2x, UNCLASSIFIED)
    // "fly" (1x, UNCLASSIFIED)
    // Total tokens: 1 + 2 + 1 + 1 + 2 + 1 = 8 tokens
    // Total unique words: 6 ("the", "sun", "sky", "bird", "clouds", "fly")
    String content = "The sun sun sky bird clouds clouds fly.";
    Reading reading =
        readingRepository.save(
            new Reading(
                null,
                null,
                "Sky Observation " + UUID.randomUUID(),
                content,
                "en",
                null,
                ReadingOrigin.PLATFORM,
                EditorialLevel.A1,
                "NATURE",
                "cover-sky"));

    indexer.indexReading(reading.id(), "en", reading.content());

    List<ReadingLexicalEvidence> evidenceList =
        frequencyRepository.findLexicalEvidenceByUserAndLanguage(
            user.id(), "en", List.of(reading.id()));

    assertThat(evidenceList).hasSize(1);
    ReadingLexicalEvidence evidence = evidenceList.get(0);

    assertThat(evidence.readingId()).isEqualTo(reading.id());

    // Token counts
    assertThat(evidence.totalTokens()).isEqualTo(8);
    assertThat(evidence.knownTokens()).isEqualTo(2);
    assertThat(evidence.learningTokens()).isEqualTo(1);
    assertThat(evidence.ignoredTokens()).isEqualTo(1);
    assertThat(evidence.relevantTokens()).isEqualTo(7); // 8 - 1 = 7

    // Unique word counts
    assertThat(evidence.totalUnique()).isEqualTo(6);
    assertThat(evidence.knownUnique()).isEqualTo(1);
    assertThat(evidence.learningUnique()).isEqualTo(1);
    assertThat(evidence.explicitNewUnique()).isEqualTo(1);
    assertThat(evidence.ignoredUnique()).isEqualTo(1);
    assertThat(evidence.unclassifiedUnique()).isEqualTo(2); // "clouds", "fly"
    assertThat(evidence.relevantUnique()).isEqualTo(5); // 6 - 1 = 5
    assertThat(evidence.classifiedUnique()).isEqualTo(4); // 6 - 2 = 4 ("the", "sun", "sky", "bird")

    // Local classification confidence: 4 classified out of 6 total = 66.67%
    assertThat(evidence.localClassificationConfidence()).isEqualTo(new BigDecimal("66.67"));

    // Global classified count: KNOWN, LEARNING, NEW = 3. IGNORED is strictly excluded.
    long globalClassified =
        userVocabularyRepository.countClassifiedWordsByUserAndLanguage(user.id(), "en");
    assertThat(globalClassified).isEqualTo(3);
  }

  @Test
  @DisplayName("findLexicalEvidenceByUserAndLanguage returns empty list when readingIds is empty")
  void testEmptyReadingIdsReturnsEmptyList() {
    UUID userId = UUID.randomUUID();
    List<ReadingLexicalEvidence> resultEmptyList =
        frequencyRepository.findLexicalEvidenceByUserAndLanguage(
            userId, "en", java.util.Collections.emptyList());
    assertThat(resultEmptyList).isEmpty();

    List<ReadingLexicalEvidence> resultSet =
        frequencyRepository.findLexicalEvidenceByUserAndLanguage(userId, "en", java.util.Set.of());
    assertThat(resultSet).isEmpty();
  }

  @Test
  @DisplayName(
      "findLexicalEvidenceByUserAndLanguage supports querying all readings when readingIds is null")
  void testNullReadingIdsQueriesAllPlatformReadings() {
    User user =
        userRepository.save(
            new User(
                null,
                "All Readings User " + UUID.randomUUID(),
                "all." + UUID.randomUUID() + "@example.com",
                "hashed",
                null));

    List<ReadingLexicalEvidence> allEvidence =
        frequencyRepository.findLexicalEvidenceByUserAndLanguage(user.id(), "en", null);
    // At least the 74 seeded platform readings
    assertThat(allEvidence.size()).isGreaterThanOrEqualTo(74);
  }

  @Test
  @DisplayName(
      "DISTINCT ON tie-breaker chooses deterministically on timestamp tie using lower(language) and id")
  void testDistinctOnDeterministicTieBreaker() {
    User user =
        userRepository.save(
            new User(
                null,
                "Tie User " + UUID.randomUUID(),
                "tie." + UUID.randomUUID() + "@example.com",
                "hashed",
                null));

    LocalDateTime fixedTimestamp = LocalDateTime.of(2026, 1, 15, 10, 0, 0);

    // Two words with the same normalized value but different regional languages (neither is
    // canonical 'en')
    Word wordGb = wordRepository.resolve("tiebreaktok", "en-gb");
    Word wordUs = wordRepository.resolve("tiebreaktok", "en-us");

    // Two vocabulary entries with the exact same first_seen_at timestamp
    // wordGb -> KNOWN
    // wordUs -> LEARNING
    userVocabularyRepository.save(
        new UserVocabulary(
            null, user, wordGb, VocabularyStatus.KNOWN, fixedTimestamp, fixedTimestamp));
    userVocabularyRepository.save(
        new UserVocabulary(null, user, wordUs, VocabularyStatus.LEARNING, fixedTimestamp, null));

    Reading reading =
        readingRepository.save(
            new Reading(
                null,
                null,
                "Tie Reading " + UUID.randomUUID(),
                "tiebreaktok is unique here.",
                "en",
                null,
                ReadingOrigin.PLATFORM,
                EditorialLevel.A1,
                "CULTURE",
                "cover-tie"));

    indexer.indexReading(reading.id(), "en", reading.content());

    List<ReadingLexicalEvidence> evidence =
        frequencyRepository.findLexicalEvidenceByUserAndLanguage(
            user.id(), "en", List.of(reading.id()));

    assertThat(evidence).hasSize(1);
    // Because lower('en-gb') < lower('en-us'), 'en-gb' (KNOWN) wins deterministically over 'en-us'
    // (LEARNING)
    assertThat(evidence.get(0).knownUnique()).isEqualTo(1);
    assertThat(evidence.get(0).learningUnique()).isEqualTo(0);
  }
}
