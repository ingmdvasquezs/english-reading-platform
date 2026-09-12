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
class OnboardingReadingV3MigrationIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static JdbcTemplate jdbc;
  private static MigrateResult v22Result;
  private static MigrateResult v23Result;

  @BeforeAll
  static void runMigrationPipeline() {
    var dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);

    // 1. Programmatically migrate schema up to V22
    var flywayV22 =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target("22")
            .load();
    v22Result = flywayV22.migrate();

    // 2. Programmatically execute the real V23 migration script
    var flywayV23 =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target("23")
            .load();
    v23Result = flywayV23.migrate();
  }

  @Test
  @DisplayName("Flyway V22 and V23 migrations executed successfully")
  void migrationsExecutedSuccessfully() {
    assertThat(v22Result.success).isTrue();
    assertThat(v22Result.targetSchemaVersion).isEqualTo("22");

    assertThat(v23Result.success).isTrue();
    assertThat(v23Result.targetSchemaVersion).isEqualTo("23");
    assertThat(v23Result.migrationsExecuted).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("V1 reading is physically preserved and deactivated (active = false)")
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
  @DisplayName("V2 reading is physically preserved and deactivated (active = false)")
  void v2ReadingIsPhysicallyPreservedAndDeactivated() {
    var row =
        jdbc.queryForMap(
            "SELECT title, active, version, content FROM onboarding_readings WHERE version = 2");

    assertThat(row.get("title")).isEqualTo("Finding a Voice in a New Harbor");
    assertThat((Boolean) row.get("active")).isFalse();
    assertThat(((Number) row.get("version")).intValue()).isEqualTo(2);
    assertThat((String) row.get("content")).isNotBlank();
  }

  @Test
  @DisplayName("V3 reading is inserted as active (active = true, version = 3)")
  void v3ReadingIsInsertedAsActive() {
    var row =
        jdbc.queryForMap(
            "SELECT title, active, version, content FROM onboarding_readings WHERE version = 3");

    assertThat(row.get("title")).isEqualTo("Finding a Voice in a New Harbor");
    assertThat((Boolean) row.get("active")).isTrue();
    assertThat(((Number) row.get("version")).intValue()).isEqualTo(3);
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
    assertThat(totalCount).isEqualTo(3L);
  }

  @Test
  @DisplayName("Adapter query selects the active V3 reading deterministically")
  void adapterQuerySelectsV3Reading() {
    var selected =
        jdbc.queryForMap(
            "SELECT id, title, version, active FROM onboarding_readings WHERE active = TRUE ORDER BY version DESC, created_at DESC, id DESC LIMIT 1");

    assertThat(selected.get("title")).isEqualTo("Finding a Voice in a New Harbor");
    assertThat(((Number) selected.get("version")).intValue()).isEqualTo(3);
    assertThat((Boolean) selected.get("active")).isTrue();
  }

  @Test
  @DisplayName(
      "V3 content tokenized with TextWordProcessor is in the 350-450 words range with 3 paragraphs and rich unique vocabulary")
  void v3ContentHasTargetLexicalMetrics() {
    var content =
        jdbc.queryForObject(
            "SELECT content FROM onboarding_readings WHERE version = 3", String.class);
    assertThat(content).isNotBlank();

    var paragraphs = content.split("\n\n");
    assertThat(paragraphs)
        .as("V3 text should have exactly 3 natural paragraphs/sections")
        .hasSize(3);

    var processor = new TextWordProcessor();
    var tokens = processor.tokenize(content);
    var uniqueWords =
        tokens.stream().map(TextWordProcessor.Token::normalizedValue).distinct().toList();

    System.out.println("V3 Total tokens: " + tokens.size());
    System.out.println("V3 Unique words: " + uniqueWords.size());
    for (int i = 0; i < paragraphs.length; i++) {
      var pTokens = processor.tokenize(paragraphs[i]);
      System.out.println("Paragraph " + (i + 1) + " tokens: " + pTokens.size());
    }

    assertThat(tokens.size()).as("Tokens count should be within 350-450 words").isBetween(350, 450);
    assertThat(uniqueWords.size())
        .as("Unique words should demonstrate rich vocabulary diversity")
        .isGreaterThan(200);

    // Verify presence of representative gradient vocabulary across the 3 sections
    assertThat(uniqueWords)
        .contains("morning", "market", "bread", "train", "street", "work"); // section 1 (everyday)
    assertThat(uniqueWords)
        .contains(
            "library",
            "conversations",
            "colleagues",
            "curiosity",
            "confidence",
            "opportunity"); // section 2 (intermediate)
    assertThat(uniqueWords)
        .contains(
            "subtle",
            "perspective",
            "adapt",
            "resilient",
            "uncertainty",
            "learning"); // section 3 (descriptive/conceptual)
  }
}
