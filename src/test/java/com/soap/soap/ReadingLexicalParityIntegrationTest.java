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

  private static final int EXPECTED_PLATFORM_READINGS = 74;
  private static final int EXPECTED_TOTAL_RWF_ROWS = 16624;
  private static final int EXPECTED_TOTAL_TOKENS = 39334;
  private static final int EXPECTED_GLOBAL_DISTINCT_NORMALIZED_VALUES = 2384;

  @Autowired private ReadingRepositoryPort readingRepository;
  @Autowired private ReadingWordFrequencyRepositoryPort frequencyRepository;
  @Autowired private TextWordProcessor wordProcessor;
  @Autowired private LanguageNormalizer languageNormalizer;
  @Autowired private NamedParameterJdbcTemplate jdbc;

  @Test
  @DisplayName(
      "Full 100% parity between TextWordProcessor and reading_word_frequencies for all platform readings")
  void assertFullLexicalParityAcrossAllPlatformReadings() {
    List<Reading> platformReadings = readingRepository.findAllPlatformReadings();
    assertThat(platformReadings).hasSize(EXPECTED_PLATFORM_READINGS);

    Integer totalRowsInDb =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM reading_word_frequencies",
            new MapSqlParameterSource(),
            Integer.class);
    assertThat(totalRowsInDb).isEqualTo(EXPECTED_TOTAL_RWF_ROWS);

    Long totalTokensInDb =
        jdbc.queryForObject(
            "SELECT SUM(occurrence_count) FROM reading_word_frequencies",
            new MapSqlParameterSource(),
            Long.class);
    assertThat(totalTokensInDb).isEqualTo((long) EXPECTED_TOTAL_TOKENS);

    Long distinctWordsInDb =
        jdbc.queryForObject(
            "SELECT COUNT(DISTINCT normalized_value) FROM reading_word_frequencies",
            new MapSqlParameterSource(),
            Long.class);
    assertThat(distinctWordsInDb).isEqualTo((long) EXPECTED_GLOBAL_DISTINCT_NORMALIZED_VALUES);

    int verifiedReadings = 0;
    int verifiedRows = 0;

    for (Reading reading : platformReadings) {
      verifiedReadings++;
      String canonicalLanguage = languageNormalizer.normalize(reading.language());

      // 1. In-memory processing via Java TextWordProcessor
      var tokens = wordProcessor.tokenize(reading.content());
      Map<String, Integer> expectedFrequencies = new TreeMap<>();
      for (var token : tokens) {
        expectedFrequencies.merge(token.normalizedValue(), 1, Integer::sum);
      }

      // 2. Database query via repository port
      Map<String, Integer> actualFrequencies =
          frequencyRepository.findFrequenciesByReadingId(reading.id());

      // 3. Strict Map equality: fails on extra, missing, wrong count, or wrong normalized_value
      assertThat(actualFrequencies)
          .as("Lexical parity mismatch for reading %s (%s)", reading.id(), reading.title())
          .isEqualTo(expectedFrequencies);

      verifiedRows += actualFrequencies.size();

      // 4. Verify language consistency in DB
      List<String> distinctLanguages =
          jdbc.query(
              "SELECT DISTINCT language FROM reading_word_frequencies WHERE reading_id = :readingId",
              new MapSqlParameterSource("readingId", reading.id()),
              (rs, rowNum) -> rs.getString("language"));
      assertThat(distinctLanguages)
          .as("Language mismatch for reading %s", reading.id())
          .containsExactly(canonicalLanguage);
    }

    assertThat(verifiedReadings).isEqualTo(EXPECTED_PLATFORM_READINGS);
    assertThat(verifiedRows).isEqualTo(EXPECTED_TOTAL_RWF_ROWS);
  }
}
