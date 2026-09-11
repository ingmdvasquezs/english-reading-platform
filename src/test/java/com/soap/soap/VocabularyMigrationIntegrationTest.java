package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class VocabularyMigrationIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static JdbcTemplate jdbc;
  private static MigrateResult v19Result;
  private static MigrateResult v20Result;

  // Tracked fixture IDs
  private static final UUID USER_A_ID = UUID.randomUUID();
  private static final UUID USER_B_ID = UUID.randomUUID();
  private static final UUID USER_C_ID = UUID.randomUUID();

  private static final UUID WOULD_REGIONAL_ID = UUID.randomUUID();
  private static final UUID WOULD_CANONICAL_ID = UUID.randomUUID();
  private static final UUID COLOUR_REGIONAL_ID = UUID.randomUUID();

  private static final UUID BACKFILL_LEARNING_WORD_ID = UUID.randomUUID();
  private static final UUID BACKFILL_KNOWN_WORD_ID = UUID.randomUUID();
  private static final UUID BACKFILL_NEW_WORD_ID = UUID.randomUUID();
  private static final UUID BACKFILL_IGNORED_WORD_ID = UUID.randomUUID();

  private static final UUID HIERARCHY_1_CANONICAL_ID = UUID.randomUUID();
  private static final UUID HIERARCHY_2_CANONICAL_ID = UUID.randomUUID();
  private static final UUID HIERARCHY_3_CANONICAL_ID = UUID.randomUUID();
  private static final UUID HIERARCHY_4_CANONICAL_ID = UUID.randomUUID();

  private static final LocalDateTime USER_A_REGIONAL_FIRST_SEEN =
      LocalDateTime.parse("2026-01-01T10:00:00");
  private static final LocalDateTime USER_A_CANONICAL_FIRST_SEEN =
      LocalDateTime.parse("2026-02-01T10:00:00");
  private static final LocalDateTime USER_A_CANONICAL_LEARNED_AT =
      LocalDateTime.parse("2026-03-01T10:00:00");

  private static final LocalDateTime USER_B_REGIONAL_FIRST_SEEN =
      LocalDateTime.parse("2026-01-15T10:00:00");

  private static final LocalDateTime BACKFILL_KNOWN_LEARNED_AT =
      LocalDateTime.parse("2026-02-10T14:30:00");

  @BeforeAll
  static void runMigrationPipeline() {
    var dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);

    // 1. Programmatically migrate Flyway from V1 to V19 (pre-V20 state)
    var flywayV19 =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target("19")
            .load();
    v19Result = flywayV19.migrate();

    // 2. Insert pre-V20 fixtures matching genuine V19 schema
    insertPreV20Fixtures();

    // 3. Programmatically execute the REAL V20 migration script with Flyway
    var flywayV20 =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target("20")
            .load();
    v20Result = flywayV20.migrate();
  }

  private static void insertPreV20Fixtures() {
    // Insert Users
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, created_at) VALUES (?, 'User A', 'user_a@example.com', 'hash', NOW())",
        USER_A_ID);
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, created_at) VALUES (?, 'User B', 'user_b@example.com', 'hash', NOW())",
        USER_B_ID);
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, created_at) VALUES (?, 'User C', 'user_c@example.com', 'hash', NOW())",
        USER_C_ID);

    // Insert Words
    // Case multi-user 'would': regional en-GB and canonical en
    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'would', 'en-GB', NOW())",
        WOULD_REGIONAL_ID);
    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'would', 'en', NOW())",
        WOULD_CANONICAL_ID);

    // Standalone regional word with no existing canonical counterpart
    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'colour', 'en-US', NOW())",
        COLOUR_REGIONAL_ID);

    // Words for backfill verification
    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'backfill-learning', 'en', NOW())",
        BACKFILL_LEARNING_WORD_ID);
    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'backfill-known', 'en', NOW())",
        BACKFILL_KNOWN_WORD_ID);
    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'backfill-new', 'en', NOW())",
        BACKFILL_NEW_WORD_ID);
    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'backfill-ignored', 'en', NOW())",
        BACKFILL_IGNORED_WORD_ID);

    // Words for hierarchy test cases
    var h1Reg = UUID.randomUUID();
    var h2Reg = UUID.randomUUID();
    var h3Reg = UUID.randomUUID();
    var h4Reg = UUID.randomUUID();

    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'hierarchy1', 'en-GB', NOW())",
        h1Reg);
    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'hierarchy1', 'en', NOW())",
        HIERARCHY_1_CANONICAL_ID);

    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'hierarchy2', 'en-GB', NOW())",
        h2Reg);
    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'hierarchy2', 'en', NOW())",
        HIERARCHY_2_CANONICAL_ID);

    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'hierarchy3', 'en-GB', NOW())",
        h3Reg);
    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'hierarchy3', 'en', NOW())",
        HIERARCHY_3_CANONICAL_ID);

    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'hierarchy4', 'en-GB', NOW())",
        h4Reg);
    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'hierarchy4', 'en', NOW())",
        HIERARCHY_4_CANONICAL_ID);

    // Insert user_vocabulary entries with V19 schema (id, user_id, word_id, status, first_seen_at,
    // learned_at, version)
    // User A: has BOTH regional and canonical 'would'
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version) VALUES (?, ?, ?, 'LEARNING', ?, NULL, 0)",
        UUID.randomUUID(),
        USER_A_ID,
        WOULD_REGIONAL_ID,
        USER_A_REGIONAL_FIRST_SEEN);
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version) VALUES (?, ?, ?, 'KNOWN', ?, ?, 0)",
        UUID.randomUUID(),
        USER_A_ID,
        WOULD_CANONICAL_ID,
        USER_A_CANONICAL_FIRST_SEEN,
        USER_A_CANONICAL_LEARNED_AT);

    // User A: standalone regional entry 'colour'
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version) VALUES (?, ?, ?, 'LEARNING', ?, NULL, 0)",
        UUID.randomUUID(),
        USER_A_ID,
        COLOUR_REGIONAL_ID,
        LocalDateTime.parse("2026-01-20T10:00:00"));

    // User A: backfill status entries
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version) VALUES (?, ?, ?, 'LEARNING', ?, NULL, 0)",
        UUID.randomUUID(),
        USER_A_ID,
        BACKFILL_LEARNING_WORD_ID,
        LocalDateTime.parse("2026-01-10T10:00:00"));
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version) VALUES (?, ?, ?, 'KNOWN', ?, ?, 0)",
        UUID.randomUUID(),
        USER_A_ID,
        BACKFILL_KNOWN_WORD_ID,
        LocalDateTime.parse("2026-01-10T10:00:00"),
        BACKFILL_KNOWN_LEARNED_AT);
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version) VALUES (?, ?, ?, 'NEW', ?, NULL, 0)",
        UUID.randomUUID(),
        USER_A_ID,
        BACKFILL_NEW_WORD_ID,
        LocalDateTime.parse("2026-01-10T10:00:00"));
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version) VALUES (?, ?, ?, 'IGNORED', ?, NULL, 0)",
        UUID.randomUUID(),
        USER_A_ID,
        BACKFILL_IGNORED_WORD_ID,
        LocalDateTime.parse("2026-01-10T10:00:00"));

    // User B: has ONLY regional 'would'
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version) VALUES (?, ?, ?, 'LEARNING', ?, NULL, 0)",
        UUID.randomUUID(),
        USER_B_ID,
        WOULD_REGIONAL_ID,
        USER_B_REGIONAL_FIRST_SEEN);

    // User C: Hierarchy tests
    // 1. Hierarchy 1: KNOWN with valid learnedAt
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version) VALUES (?, ?, ?, 'LEARNING', ?, NULL, 0)",
        UUID.randomUUID(),
        USER_C_ID,
        h1Reg,
        LocalDateTime.parse("2026-01-01T10:00:00"));
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version) VALUES (?, ?, ?, 'KNOWN', ?, ?, 0)",
        UUID.randomUUID(),
        USER_C_ID,
        HIERARCHY_1_CANONICAL_ID,
        LocalDateTime.parse("2026-01-01T10:00:00"),
        LocalDateTime.parse("2026-04-01T12:00:00"));

    // 2. Hierarchy 2: Both KNOWN with learnedAt available -> canon preferred
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version) VALUES (?, ?, ?, 'KNOWN', ?, ?, 0)",
        UUID.randomUUID(),
        USER_C_ID,
        h2Reg,
        LocalDateTime.parse("2026-01-01T10:00:00"),
        LocalDateTime.parse("2026-04-01T12:00:00"));
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version) VALUES (?, ?, ?, 'KNOWN', ?, ?, 0)",
        UUID.randomUUID(),
        USER_C_ID,
        HIERARCHY_2_CANONICAL_ID,
        LocalDateTime.parse("2026-01-01T10:00:00"),
        LocalDateTime.parse("2026-05-01T12:00:00"));

    // 3. Hierarchy 3: KNOWN without learnedAt -> restores from first_seen_at
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version) VALUES (?, ?, ?, 'NEW', ?, NULL, 0)",
        UUID.randomUUID(),
        USER_C_ID,
        h3Reg,
        LocalDateTime.parse("2026-01-01T10:00:00"));
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version) VALUES (?, ?, ?, 'KNOWN', ?, NULL, 0)",
        UUID.randomUUID(),
        USER_C_ID,
        HIERARCHY_3_CANONICAL_ID,
        LocalDateTime.parse("2026-01-01T10:00:00"));

    // 4. Hierarchy 4: Result LEARNING -> learned_at ALWAYS null
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version) VALUES (?, ?, ?, 'NEW', ?, NULL, 0)",
        UUID.randomUUID(),
        USER_C_ID,
        h4Reg,
        LocalDateTime.parse("2026-01-01T10:00:00"));
    jdbc.update(
        "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, learned_at, version) VALUES (?, ?, ?, 'LEARNING', ?, NULL, 0)",
        UUID.randomUUID(),
        USER_C_ID,
        HIERARCHY_4_CANONICAL_ID,
        LocalDateTime.parse("2026-01-01T10:00:00"));
  }

  @Test
  @DisplayName("Flyway V19 and real V20 migrations executed successfully")
  void migrationsExecutedSuccessfully() {
    assertThat(v19Result.success).isTrue();
    assertThat(v19Result.targetSchemaVersion).isEqualTo("19");

    assertThat(v20Result.success).isTrue();
    assertThat(v20Result.targetSchemaVersion).isEqualTo("20");
    assertThat(v20Result.migrationsExecuted).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("Words are consolidated to canonical 'en' and regional words deleted or updated")
  void wordsAreConsolidatedAndZeroFragmentedEnglishWordsRemain() {
    // Regional 'would / en-GB' was deleted from words
    var regionalWouldCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM words WHERE id = ?", Long.class, WOULD_REGIONAL_ID);
    assertThat(regionalWouldCount).isEqualTo(0L);

    // Canonical 'would / en' still exists
    var canonicalWouldCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM words WHERE id = ?", Long.class, WOULD_CANONICAL_ID);
    assertThat(canonicalWouldCount).isEqualTo(1L);

    // Standalone regional word 'colour / en-US' was updated in-place to 'en'
    var colourLanguage =
        jdbc.queryForObject(
            "SELECT language FROM words WHERE id = ?", String.class, COLOUR_REGIONAL_ID);
    assertThat(colourLanguage).isEqualTo("en");

    // Zero fragmented English words remain across the entire words table
    var fragmentedCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM words WHERE LOWER(language) != 'en' AND (LOWER(language) LIKE 'en-%' OR LOWER(language) LIKE 'en_%')",
            Long.class);
    assertThat(fragmentedCount).isEqualTo(0L);
  }

  @Test
  @DisplayName(
      "Multi-user entries are merged safely with earliest first_seen_at and preserved learned_at")
  void multiUserEntriesAreMergedCorrectly() {
    // User A: merged to canonical word, earliest firstSeenAt, learnedAt preserved
    var uvARows =
        jdbc.queryForList(
            "SELECT word_id, status, first_seen_at, learned_at FROM user_vocabulary WHERE user_id = ?",
            USER_A_ID);
    // User A has would, colour, and 4 backfill words = 6 entries
    assertThat(uvARows).hasSize(6);

    var userAWould =
        jdbc.queryForMap(
            "SELECT word_id, status, first_seen_at, learned_at FROM user_vocabulary WHERE user_id = ? AND word_id = ?",
            USER_A_ID,
            WOULD_CANONICAL_ID);
    assertThat(userAWould.get("status")).isEqualTo("KNOWN");
    assertThat(((Timestamp) userAWould.get("first_seen_at")).toLocalDateTime())
        .isEqualTo(USER_A_REGIONAL_FIRST_SEEN); // LEAST(2026-01-01, 2026-02-01)
    assertThat(((Timestamp) userAWould.get("learned_at")).toLocalDateTime())
        .isEqualTo(USER_A_CANONICAL_LEARNED_AT);

    // User B: reassigned from regional to canonical word, status preserved as LEARNING
    var userBWould =
        jdbc.queryForMap(
            "SELECT word_id, status, first_seen_at, learned_at FROM user_vocabulary WHERE user_id = ? AND word_id = ?",
            USER_B_ID,
            WOULD_CANONICAL_ID);
    assertThat(userBWould.get("status")).isEqualTo("LEARNING");
    assertThat(((Timestamp) userBWould.get("first_seen_at")).toLocalDateTime())
        .isEqualTo(USER_B_REGIONAL_FIRST_SEEN);
    assertThat(userBWould.get("learned_at")).isNull();

    // No user_vocabulary row references the deleted regional word
    var regionalRef =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_vocabulary WHERE word_id = ?",
            Long.class,
            WOULD_REGIONAL_ID);
    assertThat(regionalRef).isEqualTo(0L);
  }

  @Test
  @DisplayName("Historical learnedAt merge hierarchy is respected across status combinations")
  void historicalLearnedAtMergeHierarchyIsRespected() {
    // 1. KNOWN with valid learnedAt
    var h1 =
        jdbc.queryForMap(
            "SELECT status, learned_at FROM user_vocabulary WHERE user_id = ? AND word_id = ?",
            USER_C_ID,
            HIERARCHY_1_CANONICAL_ID);
    assertThat(h1.get("status")).isEqualTo("KNOWN");
    assertThat(((Timestamp) h1.get("learned_at")).toLocalDateTime())
        .isEqualTo(LocalDateTime.parse("2026-04-01T12:00:00"));

    // 2. Both entries with learnedAt -> canon learnedAt preferred
    var h2 =
        jdbc.queryForMap(
            "SELECT status, learned_at FROM user_vocabulary WHERE user_id = ? AND word_id = ?",
            USER_C_ID,
            HIERARCHY_2_CANONICAL_ID);
    assertThat(h2.get("status")).isEqualTo("KNOWN");
    assertThat(((Timestamp) h2.get("learned_at")).toLocalDateTime())
        .isEqualTo(LocalDateTime.parse("2026-05-01T12:00:00"));

    // 3. KNOWN without learnedAt -> restored from first_seen_at
    var h3 =
        jdbc.queryForMap(
            "SELECT status, learned_at FROM user_vocabulary WHERE user_id = ? AND word_id = ?",
            USER_C_ID,
            HIERARCHY_3_CANONICAL_ID);
    assertThat(h3.get("status")).isEqualTo("KNOWN");
    assertThat(((Timestamp) h3.get("learned_at")).toLocalDateTime())
        .isEqualTo(LocalDateTime.parse("2026-01-01T10:00:00"));

    // 4. Result LEARNING -> learned_at ALWAYS null
    var h4 =
        jdbc.queryForMap(
            "SELECT status, learned_at FROM user_vocabulary WHERE user_id = ? AND word_id = ?",
            USER_C_ID,
            HIERARCHY_4_CANONICAL_ID);
    assertThat(h4.get("status")).isEqualTo("LEARNING");
    assertThat(h4.get("learned_at")).isNull();
  }

  @Test
  @DisplayName(
      "Structural changes: review_stage, last_reviewed_at, next_review_at, constraint, and index created")
  void schemaColumnsConstraintAndIndexAreCreated() {
    // Check columns
    var columns =
        jdbc.queryForList(
            "SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'user_vocabulary' AND column_name IN ('review_stage', 'last_reviewed_at', 'next_review_at')");
    assertThat(columns).hasSize(3);

    var stageCol =
        columns.stream()
            .filter(c -> c.get("column_name").equals("review_stage"))
            .findFirst()
            .orElseThrow();
    assertThat(stageCol.get("data_type")).isEqualTo("integer");
    assertThat(stageCol.get("is_nullable")).isEqualTo("NO");

    var lastReviewedCol =
        columns.stream()
            .filter(c -> c.get("column_name").equals("last_reviewed_at"))
            .findFirst()
            .orElseThrow();
    assertThat(lastReviewedCol.get("data_type")).isEqualTo("timestamp without time zone");
    assertThat(lastReviewedCol.get("is_nullable")).isEqualTo("YES");

    var nextReviewCol =
        columns.stream()
            .filter(c -> c.get("column_name").equals("next_review_at"))
            .findFirst()
            .orElseThrow();
    assertThat(nextReviewCol.get("data_type")).isEqualTo("timestamp without time zone");
    assertThat(nextReviewCol.get("is_nullable")).isEqualTo("YES");

    // Check constraint chk_user_vocabulary_review_stage
    var constraintCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_name = 'user_vocabulary' AND constraint_name = 'chk_user_vocabulary_review_stage'",
            Long.class);
    assertThat(constraintCount).isEqualTo(1L);

    // Check index idx_user_vocabulary_due_review
    var indexCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'user_vocabulary' AND indexname = 'idx_user_vocabulary_due_review'",
            Long.class);
    assertThat(indexCount).isEqualTo(1L);
  }

  @Test
  @DisplayName("Review stage constraint is strictly enforced (0 <= review_stage <= 5)")
  void reviewStageConstraintIsEnforced() {
    var dummyWordId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO words (id, normalized_value, language, created_at) VALUES (?, 'dummyconstraint', 'en', NOW())",
        dummyWordId);

    // Attempting to insert review_stage = 6 should fail
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, version, review_stage) VALUES (?, ?, ?, 'LEARNING', NOW(), 0, 6)",
                    UUID.randomUUID(),
                    USER_A_ID,
                    dummyWordId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("chk_user_vocabulary_review_stage");

    // Attempting to insert review_stage = -1 should fail
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO user_vocabulary (id, user_id, word_id, status, first_seen_at, version, review_stage) VALUES (?, ?, ?, 'LEARNING', NOW(), 0, -1)",
                    UUID.randomUUID(),
                    USER_A_ID,
                    dummyWordId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("chk_user_vocabulary_review_stage");
  }

  @Test
  @DisplayName("V20 backfill sets review_stage, last_reviewed_at, and next_review_at correctly")
  void backfillSetsCorrectReviewStageAndNextReviewAt() {
    // LEARNING backfill: stage = 0, last_reviewed_at = null, next_review_at = NOW (UTC)
    var learningRow =
        jdbc.queryForMap(
            "SELECT review_stage, last_reviewed_at, next_review_at FROM user_vocabulary WHERE user_id = ? AND word_id = ?",
            USER_A_ID,
            BACKFILL_LEARNING_WORD_ID);
    assertThat(learningRow.get("review_stage")).isEqualTo(0);
    assertThat(learningRow.get("last_reviewed_at")).isNull();
    assertThat(learningRow.get("next_review_at")).isNotNull();

    // KNOWN backfill: stage = 1, last_reviewed_at = learned_at, next_review_at = null
    var knownRow =
        jdbc.queryForMap(
            "SELECT review_stage, last_reviewed_at, next_review_at FROM user_vocabulary WHERE user_id = ? AND word_id = ?",
            USER_A_ID,
            BACKFILL_KNOWN_WORD_ID);
    assertThat(knownRow.get("review_stage")).isEqualTo(1);
    assertThat(((Timestamp) knownRow.get("last_reviewed_at")).toLocalDateTime())
        .isEqualTo(BACKFILL_KNOWN_LEARNED_AT);
    assertThat(knownRow.get("next_review_at")).isNull();

    // NEW backfill: stage = 0, last_reviewed_at = null, next_review_at = null
    var newRow =
        jdbc.queryForMap(
            "SELECT review_stage, last_reviewed_at, next_review_at FROM user_vocabulary WHERE user_id = ? AND word_id = ?",
            USER_A_ID,
            BACKFILL_NEW_WORD_ID);
    assertThat(newRow.get("review_stage")).isEqualTo(0);
    assertThat(newRow.get("last_reviewed_at")).isNull();
    assertThat(newRow.get("next_review_at")).isNull();

    // IGNORED backfill: stage = 0, last_reviewed_at = null, next_review_at = null
    var ignoredRow =
        jdbc.queryForMap(
            "SELECT review_stage, last_reviewed_at, next_review_at FROM user_vocabulary WHERE user_id = ? AND word_id = ?",
            USER_A_ID,
            BACKFILL_IGNORED_WORD_ID);
    assertThat(ignoredRow.get("review_stage")).isEqualTo(0);
    assertThat(ignoredRow.get("last_reviewed_at")).isNull();
    assertThat(ignoredRow.get("next_review_at")).isNull();
  }
}
