package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.ReadingWordFrequencyRepositoryPort;
import com.soap.soap.application.service.LanguageNormalizer;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.domain.model.Reading;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
class ReadingLexicalParityIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private ReadingRepositoryPort readingRepository;
  @Autowired private ReadingWordFrequencyRepositoryPort frequencyRepository;
  @Autowired private TextWordProcessor wordProcessor;
  @Autowired private LanguageNormalizer languageNormalizer;
  @Autowired private NamedParameterJdbcTemplate jdbc;

  @Test
  @DisplayName(
      "V30 leaves 0 platform readings in DB and verifies full lexical parity on platform reading fixtures")
  void assertFullLexicalParityAcrossPlatformReadings() {
    // 1. Post-V30 clean catalog check
    List<Reading> platformReadings = readingRepository.findAllPlatformReadings();
    assertThat(platformReadings).isEmpty();

    // 2. Insert a platform reading fixture with rich lexical content
    String content =
        "The small bakery opens at dawn. Warm bread smells wonderful, and fresh coffee fills the room. "
            + "Every morning, people gather to share stories and start their day together.";
    Reading reading =
        readingRepository.save(
            new Reading(
                null,
                null,
                "Parity Test Reading",
                content,
                "en",
                java.time.LocalDateTime.now(),
                com.soap.soap.domain.model.ReadingOrigin.PLATFORM,
                com.soap.soap.domain.model.EditorialLevel.A1,
                "Daily Life",
                null,
                com.soap.soap.domain.model.EditorialStatus.PUBLISHED));

    String canonicalLanguage = languageNormalizer.normalize(reading.language());

    // 3. In-memory processing via Java TextWordProcessor
    var tokens = wordProcessor.tokenize(reading.content());
    Map<String, Integer> expectedFrequencies = new TreeMap<>();
    for (var token : tokens) {
      expectedFrequencies.merge(token.normalizedValue(), 1, Integer::sum);
    }

    // 4. Save word frequencies to DB
    for (var entry : expectedFrequencies.entrySet()) {
      jdbc.update(
          "INSERT INTO reading_word_frequencies (reading_id, language, normalized_value, occurrence_count) VALUES (:readingId, :lang, :norm, :count)",
          new MapSqlParameterSource()
              .addValue("readingId", reading.id())
              .addValue("lang", canonicalLanguage)
              .addValue("norm", entry.getKey())
              .addValue("count", entry.getValue()));
    }

    // 5. Database query via repository port
    Map<String, Integer> actualFrequencies =
        frequencyRepository.findFrequenciesByReadingId(reading.id());

    // 6. Strict Map equality: fails on extra, missing, wrong count, or wrong normalized_value
    assertThat(actualFrequencies)
        .as("Lexical parity mismatch for reading %s (%s)", reading.id(), reading.title())
        .isEqualTo(expectedFrequencies);

    // 7. Verify language consistency in DB
    List<String> distinctLanguages =
        jdbc.query(
            "SELECT DISTINCT language FROM reading_word_frequencies WHERE reading_id = :readingId",
            new MapSqlParameterSource("readingId", reading.id()),
            (rs, rowNum) -> rs.getString("language"));
    assertThat(distinctLanguages)
        .as("Language mismatch for reading %s", reading.id())
        .containsExactly(canonicalLanguage);
  }
}
