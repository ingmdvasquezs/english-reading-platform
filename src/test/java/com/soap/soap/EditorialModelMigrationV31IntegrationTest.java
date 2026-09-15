package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
class EditorialModelMigrationV31IntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("1. Column capacities and types are normalized to VARCHAR(50) and TEXT")
  void verifyColumnCapacitiesAndTypes() {
    // readings.language VARCHAR(50)
    var readingLangLen =
        jdbc.queryForObject(
            """
            SELECT character_maximum_length FROM information_schema.columns
            WHERE table_name = 'readings' AND column_name = 'language'
            """,
            Integer.class);
    assertThat(readingLangLen).isEqualTo(50);

    // readings.source_language VARCHAR(50)
    var sourceLangLen =
        jdbc.queryForObject(
            """
            SELECT character_maximum_length FROM information_schema.columns
            WHERE table_name = 'readings' AND column_name = 'source_language'
            """,
            Integer.class);
    assertThat(sourceLangLen).isEqualTo(50);

    // words.language VARCHAR(50)
    var wordLangLen =
        jdbc.queryForObject(
            """
            SELECT character_maximum_length FROM information_schema.columns
            WHERE table_name = 'words' AND column_name = 'language'
            """,
            Integer.class);
    assertThat(wordLangLen).isEqualTo(50);

    // reading_word_frequencies.language VARCHAR(50)
    var freqLangLen =
        jdbc.queryForObject(
            """
            SELECT character_maximum_length FROM information_schema.columns
            WHERE table_name = 'reading_word_frequencies' AND column_name = 'language'
            """,
            Integer.class);
    assertThat(freqLangLen).isEqualTo(50);

    // users.learning_language VARCHAR(50)
    var userLearningLangLen =
        jdbc.queryForObject(
            """
            SELECT character_maximum_length FROM information_schema.columns
            WHERE table_name = 'users' AND column_name = 'learning_language'
            """,
            Integer.class);
    assertThat(userLearningLangLen).isEqualTo(50);

    // users.native_language VARCHAR(50)
    var userNativeLangLen =
        jdbc.queryForObject(
            """
            SELECT character_maximum_length FROM information_schema.columns
            WHERE table_name = 'users' AND column_name = 'native_language'
            """,
            Integer.class);
    assertThat(userNativeLangLen).isEqualTo(50);

    // readings.source_notes TEXT
    var sourceNotesType =
        jdbc.queryForObject(
            """
            SELECT data_type FROM information_schema.columns
            WHERE table_name = 'readings' AND column_name = 'source_notes'
            """,
            String.class);
    assertThat(sourceNotesType).isEqualTo("text");
  }

  @Test
  @DisplayName("2. USER origin rejects editorial metadata and requires user_id")
  void verifyUserOriginConstraints() {
    UUID userId = insertTestUser("user-v31-" + UUID.randomUUID() + "@example.com");

    // Valid USER reading: all editorial columns NULL
    UUID validUserReadingId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO readings (id, user_id, title, content, language, origin, created_at)
        VALUES (?, ?, 'My Diary', 'Private thoughts...', 'en-US', 'USER', NOW())
        """,
        validUserReadingId,
        userId);

    // USER reading without owner must fail
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO readings (id, user_id, title, content, language, origin, created_at)
                    VALUES (?, NULL, 'No Owner Diary', 'Private...', 'en', 'USER', NOW())
                    """,
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);

    // USER reading with short_description must fail
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO readings (id, user_id, title, content, language, origin, created_at, short_description)
                    VALUES (?, ?, 'Diary with metadata', 'Private...', 'en', 'USER', NOW(), 'Illegal desc')
                    """,
                    UUID.randomUUID(),
                    userId))
        .isInstanceOf(DataIntegrityViolationException.class);

    // USER reading with editorial_level must fail
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO readings (id, user_id, title, content, language, origin, created_at, editorial_level)
                    VALUES (?, ?, 'Diary with level', 'Private...', 'en', 'USER', NOW(), 'A1')
                    """,
                    UUID.randomUUID(),
                    userId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("3. PLATFORM origin requires user_id IS NULL and all mandatory editorial metadata")
  void verifyPlatformOriginMandatoryMetadata() {
    // Missing user_id IS NULL (having user_id) must fail
    UUID userId = insertTestUser("owner-plat-" + UUID.randomUUID() + "@example.com");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO readings (id, user_id, title, content, language, origin, created_at,
                                          editorial_level, category, editorial_status, short_description,
                                          content_type, region, source_kind, rights_status, adaptation_kind, access_tier)
                    VALUES (?, ?, 'Platform with owner', 'Content...', 'en', 'PLATFORM', NOW(),
                            'A1', 'Daily Life & Relationships', 'PUBLISHED', 'Desc',
                            'FICTION', 'GLOBAL', 'ORIGINAL_EDITORIAL', 'ORIGINAL', 'ORIGINAL', 'FREE')
                    """,
                    UUID.randomUUID(),
                    userId))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Missing short_description must fail
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO readings (id, user_id, title, content, language, origin, created_at,
                                          editorial_level, category, editorial_status,
                                          content_type, region, source_kind, rights_status, adaptation_kind, access_tier)
                    VALUES (?, NULL, 'Platform missing desc', 'Content...', 'en', 'PLATFORM', NOW(),
                            'A1', 'Daily Life & Relationships', 'PUBLISHED',
                            'FICTION', 'GLOBAL', 'ORIGINAL_EDITORIAL', 'ORIGINAL', 'ORIGINAL', 'FREE')
                    """,
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Missing access_tier must fail
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO readings (id, user_id, title, content, language, origin, created_at,
                                          editorial_level, category, editorial_status, short_description,
                                          content_type, region, source_kind, rights_status, adaptation_kind)
                    VALUES (?, NULL, 'Platform missing tier', 'Content...', 'en', 'PLATFORM', NOW(),
                            'A1', 'Daily Life & Relationships', 'PUBLISHED', 'Desc',
                            'FICTION', 'GLOBAL', 'ORIGINAL_EDITORIAL', 'ORIGINAL', 'ORIGINAL')
                    """,
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("4. Category constraint enforces exactly the 8 canonical categories")
  void verifyCanonicalCategoriesConstraint() {
    String[] canonical = {
      "Daily Life & Relationships",
      "Work & Society",
      "Science & Technology",
      "Nature & Environment",
      "Mystery & Exploration",
      "Travel & Places",
      "Culture, Arts & Fiction",
      "History & Memory"
    };

    for (String cat : canonical) {
      UUID id = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO readings (id, user_id, title, content, language, origin, created_at,
                                editorial_level, category, editorial_status, short_description,
                                content_type, region, source_kind, rights_status, adaptation_kind, access_tier)
          VALUES (?, NULL, ?, 'Content...', 'en', 'PLATFORM', NOW(),
                  'A1', ?, 'PUBLISHED', 'Short desc',
                  'FICTION', 'GLOBAL', 'ORIGINAL_EDITORIAL', 'ORIGINAL', 'ORIGINAL', 'FREE')
          """,
          id,
          "Title " + cat,
          cat);
    }

    // Invalid category must fail
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO readings (id, user_id, title, content, language, origin, created_at,
                                          editorial_level, category, editorial_status, short_description,
                                          content_type, region, source_kind, rights_status, adaptation_kind, access_tier)
                    VALUES (?, NULL, 'Invalid Category', 'Content...', 'en', 'PLATFORM', NOW(),
                            'A1', 'Non-Existent Category', 'PUBLISHED', 'Short desc',
                            'FICTION', 'GLOBAL', 'ORIGINAL_EDITORIAL', 'ORIGINAL', 'ORIGINAL', 'FREE')
                    """,
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("5. Region constraint enforces exactly the 14 geographical regions including GLOBAL")
  void verifyGeographicalRegionsConstraint() {
    String[] regions = {
      "SOUTH_AMERICA",
      "CENTRAL_AMERICA",
      "CARIBBEAN",
      "NORTHERN_AMERICA",
      "EUROPE",
      "EAST_ASIA",
      "SOUTHEAST_ASIA",
      "SOUTH_ASIA",
      "CENTRAL_ASIA",
      "MIDDLE_EAST",
      "NORTH_AFRICA",
      "SUB_SAHARAN_AFRICA",
      "OCEANIA",
      "GLOBAL"
    };

    for (String reg : regions) {
      UUID id = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO readings (id, user_id, title, content, language, origin, created_at,
                                editorial_level, category, editorial_status, short_description,
                                content_type, region, source_kind, rights_status, adaptation_kind, access_tier)
          VALUES (?, NULL, ?, 'Content...', 'en', 'PLATFORM', NOW(),
                  'A1', 'Nature & Environment', 'PUBLISHED', 'Short desc',
                  'EXPLAINER', ?, 'ORIGINAL_EDITORIAL', 'ORIGINAL', 'ORIGINAL', 'FREE')
          """,
          id,
          "Title " + reg,
          reg);
    }

    // Invalid region must fail
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO readings (id, user_id, title, content, language, origin, created_at,
                                          editorial_level, category, editorial_status, short_description,
                                          content_type, region, source_kind, rights_status, adaptation_kind, access_tier)
                    VALUES (?, NULL, 'Invalid Region', 'Content...', 'en', 'PLATFORM', NOW(),
                            'A1', 'Nature & Environment', 'PUBLISHED', 'Short desc',
                            'EXPLAINER', 'ANTARCTICA', 'ORIGINAL_EDITORIAL', 'ORIGINAL', 'ORIGINAL', 'FREE')
                    """,
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("6. TRANSLATED_ADAPTATION requires source_language NOT NULL")
  void verifyTranslatedAdaptationSourceLanguageConstraint() {
    // TRANSLATED_ADAPTATION with source_language -> SUCCESS
    jdbc.update(
        """
        INSERT INTO readings (id, user_id, title, content, language, origin, created_at,
                              editorial_level, category, editorial_status, short_description,
                              content_type, region, source_kind, rights_status, adaptation_kind,
                              source_language, access_tier)
        VALUES (?, NULL, 'La Llorona (EN)', 'Content...', 'en', 'PLATFORM', NOW(),
                'B1', 'Culture, Arts & Fiction', 'PUBLISHED', 'Short desc',
                'LEGEND', 'NORTHERN_AMERICA', 'ORAL_TRADITION', 'PUBLIC_DOMAIN', 'TRANSLATED_ADAPTATION',
                'es', 'FREE')
        """,
        UUID.randomUUID());

    // TRANSLATED_ADAPTATION with source_language NULL -> FAILURE
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO readings (id, user_id, title, content, language, origin, created_at,
                                          editorial_level, category, editorial_status, short_description,
                                          content_type, region, source_kind, rights_status, adaptation_kind,
                                          source_language, access_tier)
                    VALUES (?, NULL, 'Missing source language', 'Content...', 'en', 'PLATFORM', NOW(),
                            'B1', 'Culture, Arts & Fiction', 'PUBLISHED', 'Short desc',
                            'LEGEND', 'NORTHERN_AMERICA', 'ORAL_TRADITION', 'PUBLIC_DOMAIN', 'TRANSLATED_ADAPTATION',
                            NULL, 'FREE')
                    """,
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName(
      "7. Multilingual adaptation unique partial index uq_readings_platform_adaptation_lang_level")
  void verifyAdaptationUniqueIndex() {
    String groupKey = "adapt-grp-" + UUID.randomUUID();

    // 1. Group key with English A2 -> OK
    jdbc.update(
        """
        INSERT INTO readings (id, user_id, title, content, language, origin, created_at,
                              editorial_level, category, editorial_status, short_description,
                              content_type, region, source_kind, rights_status, adaptation_kind,
                              adaptation_group_key, access_tier)
        VALUES (?, NULL, 'La Llorona EN A2', 'Content...', 'en', 'PLATFORM', NOW(),
                'A2', 'Culture, Arts & Fiction', 'PUBLISHED', 'Short desc',
                'LEGEND', 'NORTHERN_AMERICA', 'ORAL_TRADITION', 'PUBLIC_DOMAIN', 'PEDAGOGICAL_ADAPTATION',
                ?, 'FREE')
        """,
        UUID.randomUUID(),
        groupKey);

    // 2. Same group key with Portuguese pt-BR A2 -> OK (different language)
    jdbc.update(
        """
        INSERT INTO readings (id, user_id, title, content, language, origin, created_at,
                              editorial_level, category, editorial_status, short_description,
                              content_type, region, source_kind, rights_status, adaptation_kind,
                              adaptation_group_key, access_tier)
        VALUES (?, NULL, 'A Chorona PT A2', 'Conteudo...', 'pt-BR', 'PLATFORM', NOW(),
                'A2', 'Culture, Arts & Fiction', 'PUBLISHED', 'Short desc',
                'LEGEND', 'NORTHERN_AMERICA', 'ORAL_TRADITION', 'PUBLIC_DOMAIN', 'PEDAGOGICAL_ADAPTATION',
                ?, 'FREE')
        """,
        UUID.randomUUID(),
        groupKey);

    // 3. Same group key with English B1 -> OK (different level)
    jdbc.update(
        """
        INSERT INTO readings (id, user_id, title, content, language, origin, created_at,
                              editorial_level, category, editorial_status, short_description,
                              content_type, region, source_kind, rights_status, adaptation_kind,
                              adaptation_group_key, access_tier)
        VALUES (?, NULL, 'La Llorona EN B1', 'Content...', 'en', 'PLATFORM', NOW(),
                'B1', 'Culture, Arts & Fiction', 'PUBLISHED', 'Short desc',
                'LEGEND', 'NORTHERN_AMERICA', 'ORAL_TRADITION', 'PUBLIC_DOMAIN', 'PEDAGOGICAL_ADAPTATION',
                ?, 'FREE')
        """,
        UUID.randomUUID(),
        groupKey);

    // 4. Duplicate (groupKey, 'en', 'A2') -> MUST FAIL
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO readings (id, user_id, title, content, language, origin, created_at,
                                          editorial_level, category, editorial_status, short_description,
                                          content_type, region, source_kind, rights_status, adaptation_kind,
                                          adaptation_group_key, access_tier)
                    VALUES (?, NULL, 'Duplicate EN A2', 'Content...', 'en', 'PLATFORM', NOW(),
                            'A2', 'Culture, Arts & Fiction', 'PUBLISHED', 'Short desc',
                            'LEGEND', 'NORTHERN_AMERICA', 'ORAL_TRADITION', 'PUBLIC_DOMAIN', 'PEDAGOGICAL_ADAPTATION',
                            ?, 'FREE')
                    """,
                    UUID.randomUUID(),
                    groupKey))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName(
      "8. Content type constraint enforces exactly the 10 approved content types and rejects legacy values like ARTICLE")
  void verifyApprovedContentTypesConstraint() {
    String[] approved = {
      "LEGEND",
      "MYTH",
      "HISTORICAL_ACCOUNT",
      "BIOGRAPHY",
      "REAL_STORY",
      "FICTION",
      "EXPLAINER",
      "TRAVEL_NARRATIVE",
      "DIALOGUE",
      "ANECDOTE"
    };

    for (String type : approved) {
      UUID id = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO readings (id, user_id, title, content, language, origin, created_at,
                                editorial_level, category, editorial_status, short_description,
                                content_type, region, source_kind, rights_status, adaptation_kind, access_tier)
          VALUES (?, NULL, ?, 'Content...', 'en', 'PLATFORM', NOW(),
                  'A1', 'Culture, Arts & Fiction', 'PUBLISHED', 'Short desc',
                  ?, 'GLOBAL', 'ORIGINAL_EDITORIAL', 'ORIGINAL', 'ORIGINAL', 'FREE')
          """,
          id,
          "Title " + type,
          type);
    }

    // Explicitly verify eliminated value ARTICLE is rejected by DB check constraint
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO readings (id, user_id, title, content, language, origin, created_at,
                                          editorial_level, category, editorial_status, short_description,
                                          content_type, region, source_kind, rights_status, adaptation_kind, access_tier)
                    VALUES (?, NULL, 'Article Test', 'Content...', 'en', 'PLATFORM', NOW(),
                            'A1', 'Culture, Arts & Fiction', 'PUBLISHED', 'Short desc',
                            'ARTICLE', 'GLOBAL', 'ORIGINAL_EDITORIAL', 'ORIGINAL', 'ORIGINAL', 'FREE')
                    """,
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private UUID insertTestUser(String email) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, created_at) VALUES (?, 'Test', ?, 'hash', NOW())",
        id,
        email);
    return id;
  }
}
