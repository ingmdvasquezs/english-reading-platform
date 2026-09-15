package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.WordRepositoryPort;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
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
class LegacyPlatformCatalogCleanStateIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ReadingRepositoryPort readingRepository;
  @Autowired private ReadingProgressRepositoryPort progressRepository;
  @Autowired private UserRepositoryPort userRepository;
  @Autowired private WordRepositoryPort wordRepository;
  @Autowired private UserVocabularyRepositoryPort userVocabularyRepository;

  @Test
  @DisplayName("V30 leaves zero legacy platform readings and zero legacy collections")
  void legacyPlatformCatalogIsCompletelyCleanedUp() {
    Integer platformReadingsCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM readings WHERE origin = 'PLATFORM'", Integer.class);
    assertThat(platformReadingsCount).isEqualTo(0);

    Integer collectionsCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM collections", Integer.class);
    assertThat(collectionsCount).isEqualTo(0);

    Integer readingCollectionsCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM reading_collections", Integer.class);
    assertThat(readingCollectionsCount).isEqualTo(0);

    Integer platformProgressCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM reading_progress rp "
                + "JOIN readings r ON r.id = rp.reading_id WHERE r.origin = 'PLATFORM'",
            Integer.class);
    assertThat(platformProgressCount).isEqualTo(0);

    Integer platformWordFrequenciesCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM reading_word_frequencies rwf "
                + "JOIN readings r ON r.id = rwf.reading_id WHERE r.origin = 'PLATFORM'",
            Integer.class);
    assertThat(platformWordFrequenciesCount).isEqualTo(0);

    Integer platformQuestionsCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM reading_comprehension_questions rcq "
                + "JOIN readings r ON r.id = rcq.reading_id WHERE r.origin = 'PLATFORM'",
            Integer.class);
    assertThat(platformQuestionsCount).isEqualTo(0);

    Integer platformOptionsCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM reading_comprehension_options rco "
                + "JOIN reading_comprehension_questions rcq ON rcq.id = rco.question_id "
                + "JOIN readings r ON r.id = rcq.reading_id WHERE r.origin = 'PLATFORM'",
            Integer.class);
    assertThat(platformOptionsCount).isEqualTo(0);

    Integer platformAttemptsCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_comprehension_attempts uca "
                + "JOIN readings r ON r.id = uca.reading_id WHERE r.origin = 'PLATFORM'",
            Integer.class);
    assertThat(platformAttemptsCount).isEqualTo(0);

    Integer platformAnswersCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_comprehension_answers uca_ans "
                + "JOIN user_comprehension_attempts uca ON uca.id = uca_ans.attempt_id "
                + "JOIN readings r ON r.id = uca.reading_id WHERE r.origin = 'PLATFORM'",
            Integer.class);
    assertThat(platformAnswersCount).isEqualTo(0);
  }

  @Test
  @DisplayName(
      "User readings, progress, vocabulary, and document infrastructure are fully preserved and functional")
  void userAndDocumentInfrastructurePreservedAndFunctional() {
    // 1. Verify user persistence and authentication capability
    User user =
        userRepository.save(
            new User(
                null,
                "Clean State User",
                "clean-state-" + UUID.randomUUID() + "@example.com",
                "hashedpassword",
                null));
    assertThat(user.id()).isNotNull();

    // 2. Verify user reading creation and querying
    Reading userReading =
        readingRepository.save(
            new Reading(
                null,
                user,
                "My Personal Essay",
                "Learning a second language requires consistent daily practice.",
                "en",
                LocalDateTime.now(),
                ReadingOrigin.USER,
                null,
                null,
                null,
                null));
    assertThat(userReading.id()).isNotNull();
    assertThat(userReading.origin()).isEqualTo(ReadingOrigin.USER);

    var userReadings =
        readingRepository.findUserReadingsByUserId(user.id(), new PageRequest(0, 10));
    assertThat(userReadings.content()).hasSize(1);
    assertThat(userReadings.content().get(0).title()).isEqualTo("My Personal Essay");

    // 3. Verify user reading progress tracking
    progressRepository.startIfAbsent(user.id(), userReading.id(), LocalDateTime.now());
    var progress = progressRepository.findByUserIdAndReadingId(user.id(), userReading.id());
    assertThat(progress).isPresent();
    assertThat(progress.get().status().name()).isEqualTo("IN_PROGRESS");

    progressRepository.complete(user.id(), userReading.id(), LocalDateTime.now());
    var completedProgress =
        progressRepository.findByUserIdAndReadingId(user.id(), userReading.id());
    assertThat(completedProgress).isPresent();
    assertThat(completedProgress.get().status().name()).isEqualTo("COMPLETED");

    // 4. Verify words and user vocabulary capability
    Word word = wordRepository.resolve("practice", "en");
    assertThat(word.id()).isNotNull();

    UserVocabulary userVocab =
        userVocabularyRepository.save(
            UserVocabulary.createInitial(
                user, word, VocabularyStatus.LEARNING, java.time.Clock.systemUTC()));
    assertThat(userVocab.id()).isNotNull();
    assertThat(userVocab.status()).isEqualTo(VocabularyStatus.LEARNING);

    // 5. Verify onboarding readings infrastructure exists
    Integer onboardingCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM onboarding_readings", Integer.class);
    assertThat(onboardingCount).isGreaterThanOrEqualTo(1);

    // 6. Verify imported documents table exists and is accessible
    Integer importedDocsTableExists =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'imported_documents'",
            Integer.class);
    assertThat(importedDocsTableExists).isEqualTo(1);
  }
}
