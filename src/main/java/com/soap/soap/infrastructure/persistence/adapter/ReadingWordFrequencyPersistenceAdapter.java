package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.model.ReadingLexicalEvidence;
import com.soap.soap.application.port.out.ReadingWordFrequencyRepositoryPort;
import com.soap.soap.application.service.LanguageNormalizer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadingWordFrequencyPersistenceAdapter implements ReadingWordFrequencyRepositoryPort {

  private final NamedParameterJdbcTemplate jdbc;
  private final LanguageNormalizer languages;

  public ReadingWordFrequencyPersistenceAdapter(
      DataSource dataSource, LanguageNormalizer languages) {
    this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    this.languages = languages;
  }

  @Override
  @Transactional
  public void replaceFrequencies(
      UUID readingId, String language, Map<String, Integer> frequencies) {
    if (readingId == null) {
      throw new IllegalArgumentException("Reading ID must not be null");
    }
    var canonical = languages.normalize(language);
    var deleteParams = new MapSqlParameterSource("readingId", readingId);
    jdbc.update("DELETE FROM reading_word_frequencies WHERE reading_id = :readingId", deleteParams);

    if (frequencies != null && !frequencies.isEmpty()) {
      var batchParams = new SqlParameterSource[frequencies.size()];
      int i = 0;
      for (var entry : frequencies.entrySet()) {
        batchParams[i++] =
            new MapSqlParameterSource()
                .addValue("readingId", readingId)
                .addValue("language", canonical)
                .addValue("normalizedValue", entry.getKey())
                .addValue("occurrenceCount", entry.getValue());
      }
      jdbc.batchUpdate(
          """
          INSERT INTO reading_word_frequencies (reading_id, language, normalized_value, occurrence_count)
          VALUES (:readingId, :language, :normalizedValue, :occurrenceCount)
          """,
          batchParams);
    }
  }

  @Override
  @Transactional
  public void deleteByReadingId(UUID readingId) {
    if (readingId == null) {
      throw new IllegalArgumentException("Reading ID must not be null");
    }
    jdbc.update(
        "DELETE FROM reading_word_frequencies WHERE reading_id = :readingId",
        new MapSqlParameterSource("readingId", readingId));
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Integer> findFrequenciesByReadingId(UUID readingId) {
    if (readingId == null) {
      throw new IllegalArgumentException("Reading ID must not be null");
    }
    var result = new LinkedHashMap<String, Integer>();
    jdbc.query(
        """
        SELECT normalized_value, occurrence_count
        FROM reading_word_frequencies
        WHERE reading_id = :readingId
        ORDER BY normalized_value ASC
        """,
        new MapSqlParameterSource("readingId", readingId),
        rs -> {
          result.put(rs.getString("normalized_value"), rs.getInt("occurrence_count"));
        });
    return Map.copyOf(result);
  }

  /**
   * Finds lexical evidence for a user across a set of readings.
   *
   * <p>Branch handling for {@code readingIds}:
   *
   * <ul>
   *   <li>{@code readingIds == null}: Queries the entire reading catalog for the canonical
   *       language.
   *   <li>{@code readingIds.isEmpty()}: Safe branch returning an empty list immediately, avoiding
   *       illegal {@code IN ()} syntax in PostgreSQL and NamedParameterJdbcTemplate.
   *   <li>{@code readingIds} with values: Appends {@code AND rwf.reading_id IN (:readingIds)} and
   *       binds parameters.
   * </ul>
   */
  @Override
  @Transactional(readOnly = true)
  public List<ReadingLexicalEvidence> findLexicalEvidenceByUserAndLanguage(
      UUID userId, String language, Collection<UUID> readingIds) {
    if (userId == null) {
      throw new IllegalArgumentException("User ID must not be null");
    }
    if (readingIds != null && readingIds.isEmpty()) {
      return List.of();
    }
    var canonical = languages.normalize(language);
    var equivalentLanguages = languages.equivalentLanguages(canonical);

    var params =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("languages", equivalentLanguages)
            .addValue("canonicalLanguage", canonical);

    var sql = new StringBuilder();
    sql.append(
        """
        WITH user_vocab AS (
            SELECT DISTINCT ON (w.normalized_value)
                   w.normalized_value, uv.status
            FROM user_vocabulary uv
            JOIN words w ON uv.word_id = w.id
            WHERE uv.user_id = :userId
              AND lower(w.language) IN (:languages)
            ORDER BY w.normalized_value,
                     CASE WHEN lower(w.language) = :canonicalLanguage THEN 0 ELSE 1 END,
                     uv.first_seen_at DESC,
                     lower(w.language) ASC,
                     uv.id ASC
        )
        SELECT
            rwf.reading_id,
            COALESCE(SUM(rwf.occurrence_count), 0) AS total_tokens,
            COALESCE(SUM(CASE WHEN uv.status = 'KNOWN' THEN rwf.occurrence_count ELSE 0 END), 0) AS known_tokens,
            COALESCE(SUM(CASE WHEN uv.status = 'LEARNING' THEN rwf.occurrence_count ELSE 0 END), 0) AS learning_tokens,
            COALESCE(SUM(CASE WHEN uv.status = 'IGNORED' THEN rwf.occurrence_count ELSE 0 END), 0) AS ignored_tokens,
            COUNT(rwf.normalized_value) AS total_unique,
            COUNT(CASE WHEN uv.status = 'KNOWN' THEN 1 END) AS known_unique,
            COUNT(CASE WHEN uv.status = 'LEARNING' THEN 1 END) AS learning_unique,
            COUNT(CASE WHEN uv.status = 'NEW' THEN 1 END) AS explicit_new_unique,
            COUNT(CASE WHEN uv.status = 'IGNORED' THEN 1 END) AS ignored_unique,
            COUNT(CASE WHEN uv.status IS NULL THEN 1 END) AS unclassified_unique
        FROM reading_word_frequencies rwf
        LEFT JOIN user_vocab uv ON rwf.normalized_value = uv.normalized_value
        WHERE rwf.language = :canonicalLanguage
        """);

    if (readingIds != null) {
      sql.append("  AND rwf.reading_id IN (:readingIds)\n");
      params.addValue("readingIds", readingIds);
    }

    sql.append("GROUP BY rwf.reading_id\nORDER BY rwf.reading_id ASC");

    return jdbc.query(
        sql.toString(),
        params,
        (rs, rowNum) ->
            new ReadingLexicalEvidence(
                rs.getObject("reading_id", UUID.class),
                rs.getInt("total_tokens"),
                rs.getInt("known_tokens"),
                rs.getInt("learning_tokens"),
                rs.getInt("ignored_tokens"),
                rs.getInt("total_unique"),
                rs.getInt("known_unique"),
                rs.getInt("learning_unique"),
                rs.getInt("explicit_new_unique"),
                rs.getInt("ignored_unique"),
                rs.getInt("unclassified_unique")));
  }
}
