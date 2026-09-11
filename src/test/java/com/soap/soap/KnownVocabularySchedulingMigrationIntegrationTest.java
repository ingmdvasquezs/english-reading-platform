package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class KnownVocabularySchedulingMigrationIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static JdbcTemplate jdbc;
  private static MigrateResult v20Result;
  private static MigrateResult v21Result;

  private static final UUID USER_ID = UUID.randomUUID();
  private static final List<UUID> UNSCHEDULED_KNOWN_WORD_IDS = new ArrayList<>();
  private static final UUID ALREADY_SCHEDULED_KNOWN_WORD_ID = UUID.randomUUID();
  private static final UUID LEARNING_WORD_ID = UUID.randomUUID();
  private static final UUID NEW_WORD_ID = UUID.randomUUID();
  private static final UUID IGNORED_WORD_ID = UUID.randomUUID();

  private static final LocalDateTime ALREADY_SCHEDULED_NEXT_REVIEW =
      LocalDateTime.parse("2026-09-15T12:00:00");
  private static final LocalDateTime LEARNING_NEXT_REVIEW =
      LocalDateTime.parse("2026-09-11T12:00:00");
  private static final LocalDateTime HISTORICAL_LEARNED_AT =
      LocalDateTime.parse("2026-02-01T10:00:00");

  private static LocalDateTime migrationStartUtc;

  @BeforeAll
  static void runMigrationPipeline() {
    var dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);

    // 1. Programmatically migrate schema up to V20
    var flywayV20 =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target("20")
            .load();
    v20Result = flywayV20.migrate();

    // 2. Insert pre-V21 fixtures (user, words, vocabulary entries)
    insertPreV21Fixtures();

    migrationStartUtc = LocalDateTime.now(ZoneOffset.UTC);

    // 3. Programmatically execute the real V21 migration script
    var flywayV21 =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target("21")
            .load();
    v21Result = flywayV21.migrate();
  }

  private static void insertPreV21Fixtures() {
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, created_at) VALUES (?, 'Test User', 'known_sched@example.com', 'hash', NOW())",
        USER_ID);

    // Insert 30 words for unscheduled KNOWN vocabulary
    for (int i = 1; i <= 30; i++) {
      var wordId = UUID.randomUUID();
      UNSCHEDULED_KNOWN_WORD_IDS.add(wordId);
      jdbc.update(
          "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, ?, 'en', NOW())",
          wordId,
          "knownword" + i);
      jdbc.update(
          "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version, review_stage, last_reviewed_at, next_review_at) VALUES (?, ?, ?, 'KNOWN', ?, ?, 0, 1, NULL, NULL)",
          UUID.randomUUID(),
          USER_ID,
          wordId,
          LocalDateTime.parse("2026-01-01T10:00:00").plusHours(i),
          HISTORICAL_LEARNED_AT);
    }

    // Insert word and entry for ALREADY SCHEDULED KNOWN (e.g. stage 2, scheduled at 2026-09-15)
    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'alreadyscheduled', 'en', NOW())",
        ALREADY_SCHEDULED_KNOWN_WORD_ID);
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version, review_stage, last_reviewed_at, next_review_at) VALUES (?, ?, ?, 'KNOWN', NOW(), ?, 0, 2, NULL, ?)",
        UUID.randomUUID(),
        USER_ID,
        ALREADY_SCHEDULED_KNOWN_WORD_ID,
        HISTORICAL_LEARNED_AT,
        ALREADY_SCHEDULED_NEXT_REVIEW);

    // Insert LEARNING entry
    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'learningword', 'en', NOW())",
        LEARNING_WORD_ID);
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version, review_stage, last_reviewed_at, next_review_at) VALUES (?, ?, ?, 'LEARNING', NOW(), NULL, 0, 0, NULL, ?)",
        UUID.randomUUID(),
        USER_ID,
        LEARNING_WORD_ID,
        LEARNING_NEXT_REVIEW);

    // Insert NEW entry
    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'newword', 'en', NOW())",
        NEW_WORD_ID);
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version, review_stage, last_reviewed_at, next_review_at) VALUES (?, ?, ?, 'NEW', NOW(), NULL, 0, 0, NULL, NULL)",
        UUID.randomUUID(),
        USER_ID,
        NEW_WORD_ID);

    // Insert IGNORED entry
    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'ignoredword', 'en', NOW())",
        IGNORED_WORD_ID);
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version, review_stage, last_reviewed_at, next_review_at) VALUES (?, ?, ?, 'IGNORED', NOW(), NULL, 0, 0, NULL, NULL)",
        UUID.randomUUID(),
        USER_ID,
        IGNORED_WORD_ID);
  }

  @Test
  @DisplayName("Flyway V20 and V21 migrations executed successfully")
  void migrationsExecutedSuccessfully() {
    assertThat(v20Result.success).isTrue();
    assertThat(v20Result.targetSchemaVersion).isEqualTo("20");

    assertThat(v21Result.success).isTrue();
    assertThat(v21Result.targetSchemaVersion).isEqualTo("21");
    assertThat(v21Result.migrationsExecuted).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("All previously unscheduled KNOWN vocabulary entries are scheduled with stage >= 3")
  void allUnscheduledKnownAreScheduledWithStageAtLeastThree() {
    for (var wordId : UNSCHEDULED_KNOWN_WORD_IDS) {
      var row =
          jdbc.queryForMap(
              "SELECT review_stage, next_review_at, learned_at, last_reviewed_at FROM user_vocabulary WHERE user_id = ? AND word_id = ?",
              USER_ID,
              wordId);

      var stage = ((Number) row.get("review_stage")).intValue();
      var nextReview = ((Timestamp) row.get("next_review_at")).toLocalDateTime();
      var learnedAt = ((Timestamp) row.get("learned_at")).toLocalDateTime();

      assertThat(stage).isGreaterThanOrEqualTo(3);
      assertThat(nextReview).isNotNull();
      assertThat(learnedAt).isEqualTo(HISTORICAL_LEARNED_AT);
      assertThat(row.get("last_reviewed_at")).isNull();
    }
  }

  @Test
  @DisplayName(
      "Scheduled dates for historical KNOWN entries are distributed between +7d and +30d without clustering")
  void scheduledDatesAreDistributedBetweenPlusSevenAndPlusThirtyDays() {
    var dates =
        jdbc.queryForList(
            "SELECT next_review_at FROM user_vocabulary WHERE user_id = ? AND word_id IN ("
                + String.join(
                    ",", UNSCHEDULED_KNOWN_WORD_IDS.stream().map(id -> "'" + id + "'").toList())
                + ")",
            Timestamp.class,
            USER_ID);

    assertThat(dates).hasSize(30);

    var minAllowed = migrationStartUtc.plusDays(7).minusMinutes(5);
    var maxAllowed = migrationStartUtc.plusDays(30).plusMinutes(5);

    for (var ts : dates) {
      var ldt = ts.toLocalDateTime();
      assertThat(ldt).isAfterOrEqualTo(minAllowed);
      assertThat(ldt).isBeforeOrEqualTo(maxAllowed);
    }

    // Since we inserted 30 items with modulo 24, all 24 distinct day offsets must be present
    var distinctDatesCount =
        jdbc.queryForObject(
            "SELECT COUNT(DISTINCT next_review_at::date) FROM user_vocabulary WHERE user_id = ? AND word_id IN ("
                + String.join(
                    ",", UNSCHEDULED_KNOWN_WORD_IDS.stream().map(id -> "'" + id + "'").toList())
                + ")",
            Long.class,
            USER_ID);
    assertThat(distinctDatesCount).isEqualTo(24L);
  }

  @Test
  @DisplayName("Already-scheduled KNOWN entry is not modified by V21")
  void alreadyScheduledKnownIsNotModified() {
    var row =
        jdbc.queryForMap(
            "SELECT review_stage, next_review_at, learned_at FROM user_vocabulary WHERE user_id = ? AND word_id = ?",
            USER_ID,
            ALREADY_SCHEDULED_KNOWN_WORD_ID);

    assertThat(((Number) row.get("review_stage")).intValue()).isEqualTo(2);
    assertThat(((Timestamp) row.get("next_review_at")).toLocalDateTime())
        .isEqualTo(ALREADY_SCHEDULED_NEXT_REVIEW);
    assertThat(((Timestamp) row.get("learned_at")).toLocalDateTime())
        .isEqualTo(HISTORICAL_LEARNED_AT);
  }

  @Test
  @DisplayName("LEARNING entry is not modified by V21")
  void learningEntryIsNotModified() {
    var row =
        jdbc.queryForMap(
            "SELECT review_stage, next_review_at, status FROM user_vocabulary WHERE user_id = ? AND word_id = ?",
            USER_ID,
            LEARNING_WORD_ID);

    assertThat(row.get("status")).isEqualTo("LEARNING");
    assertThat(((Number) row.get("review_stage")).intValue()).isEqualTo(0);
    assertThat(((Timestamp) row.get("next_review_at")).toLocalDateTime())
        .isEqualTo(LEARNING_NEXT_REVIEW);
  }

  @Test
  @DisplayName("NEW entry is not modified by V21 and keeps next_review_at null")
  void newEntryIsNotModified() {
    var row =
        jdbc.queryForMap(
            "SELECT review_stage, next_review_at, status FROM user_vocabulary WHERE user_id = ? AND word_id = ?",
            USER_ID,
            NEW_WORD_ID);

    assertThat(row.get("status")).isEqualTo("NEW");
    assertThat(((Number) row.get("review_stage")).intValue()).isEqualTo(0);
    assertThat(row.get("next_review_at")).isNull();
  }

  @Test
  @DisplayName("IGNORED entry is not modified by V21 and keeps next_review_at null")
  void ignoredEntryIsNotModified() {
    var row =
        jdbc.queryForMap(
            "SELECT review_stage, next_review_at, status FROM user_vocabulary WHERE user_id = ? AND word_id = ?",
            USER_ID,
            IGNORED_WORD_ID);

    assertThat(row.get("status")).isEqualTo("IGNORED");
    assertThat(((Number) row.get("review_stage")).intValue()).isEqualTo(0);
    assertThat(row.get("next_review_at")).isNull();
  }
}
