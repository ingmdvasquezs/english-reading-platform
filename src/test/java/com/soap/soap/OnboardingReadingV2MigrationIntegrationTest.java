package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.service.TextWordProcessor;
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
class OnboardingReadingV2MigrationIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static JdbcTemplate jdbc;
  private static MigrateResult v21Result;
  private static MigrateResult v22Result;

  @BeforeAll
  static void runMigrationPipeline() {
    var dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);

    // 1. Programmatically migrate schema up to V21
    var flywayV21 =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target("21")
            .load();
    v21Result = flywayV21.migrate();

    // 2. Programmatically execute the real V22 migration script
    var flywayV22 =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target("22")
            .load();
    v22Result = flywayV22.migrate();
  }

  @Test
  @DisplayName("Flyway V21 and V22 migrations executed successfully")
  void migrationsExecutedSuccessfully() {
    assertThat(v21Result.success).isTrue();
    assertThat(v21Result.targetSchemaVersion).isEqualTo("21");

    assertThat(v22Result.success).isTrue();
    assertThat(v22Result.targetSchemaVersion).isEqualTo("22");
    assertThat(v22Result.migrationsExecuted).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("V1 reading is physically preserved but deactivated (active = false)")
  void v1ReadingIsPhysicallyPreservedAndDeactivated() {
    var row =
        jdbc.queryForMap(
            "SELECT title, active, version, content FROM onboarding_readings WHERE version = 1");

    assertThat(row.get("title")).isEqualTo("A Different Way to Learn");
    assertThat((Boolean) row.get("active")).isFalse();
    assertThat(((Number) row.get("version")).intValue()).isEqualTo(1);
    assertThat((String) row.get("content")).isNotBlank();
  }

  @Test
  @DisplayName("V2 reading is inserted as active (active = true, version = 2)")
  void v2ReadingIsInsertedAsActive() {
    var row =
        jdbc.queryForMap(
            "SELECT title, active, version, content FROM onboarding_readings WHERE version = 2");

    assertThat(row.get("title")).isEqualTo("Finding a Voice in a New Harbor");
    assertThat((Boolean) row.get("active")).isTrue();
    assertThat(((Number) row.get("version")).intValue()).isEqualTo(2);
    assertThat((String) row.get("content")).isNotBlank();
  }

  @Test
  @DisplayName("Exactly one active onboarding reading exists in the database")
  void exactlyOneActiveOnboardingReadingExists() {
    var activeCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM onboarding_readings WHERE active = TRUE", Long.class);
    assertThat(activeCount).isEqualTo(1L);

    var totalCount = jdbc.queryForObject("SELECT COUNT(*) FROM onboarding_readings", Long.class);
    assertThat(totalCount).isEqualTo(2L);
  }

  @Test
  @DisplayName("Adapter query selects the active V2 reading deterministically")
  void adapterQuerySelectsV2Reading() {
    var selected =
        jdbc.queryForMap(
            "SELECT id, title, version, active FROM onboarding_readings WHERE active = TRUE ORDER BY version DESC, created_at DESC, id DESC LIMIT 1");

    assertThat(selected.get("title")).isEqualTo("Finding a Voice in a New Harbor");
    assertThat(((Number) selected.get("version")).intValue()).isEqualTo(2);
    assertThat((Boolean) selected.get("active")).isTrue();
  }

  @Test
  @DisplayName(
      "V2 content tokenized with TextWordProcessor is in the 700-900 words range with rich unique vocabulary")
  void v2ContentHasTargetLexicalMetrics() {
    var content =
        jdbc.queryForObject(
            "SELECT content FROM onboarding_readings WHERE version = 2", String.class);
    assertThat(content).isNotBlank();

    var processor = new TextWordProcessor();
    var tokens = processor.tokenize(content);
    var uniqueWords =
        tokens.stream().map(TextWordProcessor.Token::normalizedValue).distinct().toList();

    assertThat(tokens.size()).as("Tokens count should be within 700-900 words").isBetween(700, 900);
    assertThat(uniqueWords.size())
        .as("Unique words should demonstrate rich vocabulary diversity")
        .isGreaterThan(400);

    // Verify presence of representative gradient vocabulary
    assertThat(uniqueWords).contains("morning", "market", "bread", "train"); // foundational
    assertThat(uniqueWords)
        .contains("library", "conversations", "colleagues", "curiosity"); // intermediate
    assertThat(uniqueWords).contains("subtle", "nuances", "ambiguity", "resilience"); // advanced
  }
}
