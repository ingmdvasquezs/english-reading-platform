package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.ReadingWordFrequencyRepositoryPort;
import com.soap.soap.application.service.ReadingLexicalIndexer;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import java.util.Map;
import java.util.UUID;
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
class ReadingLexicalIndexerIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private ReadingLexicalIndexer indexer;
  @Autowired private ReadingWordFrequencyRepositoryPort frequencyRepository;
  @Autowired private ReadingRepositoryPort readingRepository;
  @Autowired private NamedParameterJdbcTemplate jdbc;

  @Test
  @DisplayName(
      "Index reading preserves canonical language, normalizes apostrophes and handles Unicode")
  void testIndexReadingCanonicalLanguageAndUnicode() {
    Reading reading = createTestReading("It’s a sunny day, isn't it? Don't worry about the café.");
    reading = readingRepository.save(reading);

    indexer.indexReading(reading.id(), "en-US", reading.content());

    Map<String, Integer> freqs = frequencyRepository.findFrequenciesByReadingId(reading.id());
    assertThat(freqs).isNotEmpty();
    assertThat(freqs.get("it's")).isEqualTo(1);
    assertThat(freqs.get("isn't")).isEqualTo(1);
    assertThat(freqs.get("don't")).isEqualTo(1);
    assertThat(freqs.get("worry")).isEqualTo(1);
    assertThat(freqs.get("about")).isEqualTo(1);
    assertThat(freqs.get("the")).isEqualTo(1);

    // Verify language in DB is normalized to canonical 'en'
    String lang =
        jdbc.queryForObject(
            "SELECT DISTINCT language FROM reading_word_frequencies WHERE reading_id = :id",
            new MapSqlParameterSource("id", reading.id()),
            String.class);
    assertThat(lang).isEqualTo("en");
  }

  @Test
  @DisplayName("Index reading is idempotent")
  void testIndexReadingIdempotency() {
    Reading reading = createTestReading("Learning English is fun and rewarding. English is great.");
    reading = readingRepository.save(reading);

    indexer.indexReading(reading.id(), "en", reading.content());
    Map<String, Integer> firstRun = frequencyRepository.findFrequenciesByReadingId(reading.id());

    indexer.indexReading(reading.id(), "en", reading.content());
    Map<String, Integer> secondRun = frequencyRepository.findFrequenciesByReadingId(reading.id());

    assertThat(secondRun).isEqualTo(firstRun);
    assertThat(secondRun.get("english")).isEqualTo(2);
    assertThat(secondRun.get("is")).isEqualTo(2);
  }

  @Test
  @DisplayName("Updating content atomically replaces all previous frequencies")
  void testAtomicUpdateReplacesFrequencies() {
    Reading reading = createTestReading("Old content with old words.");
    reading = readingRepository.save(reading);

    indexer.indexReading(reading.id(), "en", reading.content());
    Map<String, Integer> oldFreqs = frequencyRepository.findFrequenciesByReadingId(reading.id());
    assertThat(oldFreqs).containsKey("old");

    // Re-index with new content
    String newContent = "Brand new text with completely fresh ideas.";
    indexer.indexReading(reading.id(), "en", newContent);

    Map<String, Integer> newFreqs = frequencyRepository.findFrequenciesByReadingId(reading.id());
    assertThat(newFreqs).containsKey("fresh");
    assertThat(newFreqs).containsKey("ideas");
    assertThat(newFreqs).doesNotContainKey("old");
  }

  @Test
  @DisplayName("Deleting reading triggers ON DELETE CASCADE in reading_word_frequencies")
  void testOnDeleteCascade() {
    Reading reading = createTestReading("A temporary text to be deleted.");
    reading = readingRepository.save(reading);

    indexer.indexReading(reading.id(), "en", reading.content());
    assertThat(frequencyRepository.findFrequenciesByReadingId(reading.id())).isNotEmpty();

    // Delete reading via repository
    readingRepository.deleteById(reading.id());

    // Frequencies must be gone via ON DELETE CASCADE
    Map<String, Integer> remaining = frequencyRepository.findFrequenciesByReadingId(reading.id());
    assertThat(remaining).isEmpty();

    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM reading_word_frequencies WHERE reading_id = :id",
            new MapSqlParameterSource("id", reading.id()),
            Integer.class);
    assertThat(count).isZero();
  }

  @Test
  @DisplayName("removeReadingIndex explicitly removes frequencies")
  void testRemoveReadingIndex() {
    Reading reading = createTestReading("Index removal test text.");
    reading = readingRepository.save(reading);

    indexer.indexReading(reading.id(), "en", reading.content());
    assertThat(frequencyRepository.findFrequenciesByReadingId(reading.id())).isNotEmpty();

    indexer.removeReadingIndex(reading.id());
    assertThat(frequencyRepository.findFrequenciesByReadingId(reading.id())).isEmpty();
  }

  private Reading createTestReading(String content) {
    return new Reading(
        null,
        null,
        "Test Title " + UUID.randomUUID(),
        content,
        "en",
        null,
        ReadingOrigin.PLATFORM,
        EditorialLevel.B1,
        "Culture, Arts & Fiction",
        "cover-key",
        com.soap.soap.domain.model.EditorialStatus.PUBLISHED);
  }
}
