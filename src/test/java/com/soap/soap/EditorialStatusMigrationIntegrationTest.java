package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class EditorialStatusMigrationIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static JdbcTemplate jdbc;
  private static MigrateResult v28Result;
  private static MigrateResult v29Result;

  @BeforeAll
  static void runMigrationPipeline() {
    var dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);

    // 1. Programmatically migrate schema up to V28
    var flywayV28 =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target("28")
            .load();
    v28Result = flywayV28.migrate();

    // Insert a test user reading before V29 to verify backfill leaves user readings with
    // editorial_status = null
    jdbc.execute(
        "INSERT INTO users (id, name, email, password_hash, created_at) "
            + "VALUES ('11111111-1111-1111-1111-111111111111', 'testuser', 'test@example.com', 'hash', NOW())");
    jdbc.execute(
        "INSERT INTO readings (id, user_id, title, content, language, origin, created_at) "
            + "VALUES ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'User Pre-V29', 'Content', 'en', 'USER', NOW())");

    // 2. Programmatically execute real V29 migration script
    var flywayV29 =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target("29")
            .load();
    v29Result = flywayV29.migrate();
  }

  @Test
  @DisplayName("Flyway V28 and V29 migrations executed successfully")
  void migrationsExecutedSuccessfully() {
    assertThat(v28Result.success).isTrue();
    assertThat(v28Result.targetSchemaVersion).isEqualTo("28");

    assertThat(v29Result.success).isTrue();
    assertThat(v29Result.targetSchemaVersion).isEqualTo("29");
    assertThat(v29Result.migrationsExecuted).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("All 74 existing platform readings are backfilled to PUBLISHED")
  void all74PlatformReadingsAreBackfilledToPublished() {
    Integer platformCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM readings WHERE origin = 'PLATFORM'", Integer.class);
    assertThat(platformCount).isEqualTo(74);

    Integer publishedCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM readings WHERE origin = 'PLATFORM' AND editorial_status = 'PUBLISHED'",
            Integer.class);
    assertThat(publishedCount).isEqualTo(74);
  }

  @Test
  @DisplayName("User readings have editorial_status = NULL")
  void userReadingsHaveNullEditorialStatus() {
    String status =
        jdbc.queryForObject(
            "SELECT editorial_status FROM readings WHERE id = '22222222-2222-2222-2222-222222222222'",
            String.class);
    assertThat(status).isNull();
  }

  @Test
  @DisplayName("Constraint ck_readings_editorial_status rejects invalid statuses")
  void constraintRejectsInvalidEditorialStatus() {
    assertThatThrownBy(
            () ->
                jdbc.execute(
                    "UPDATE readings SET editorial_status = 'UNKNOWN' WHERE origin = 'PLATFORM'"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName(
      "Constraint ck_readings_origin_ownership_metadata rejects PLATFORM with null editorial_status")
  void constraintRejectsPlatformWithNullEditorialStatus() {
    assertThatThrownBy(
            () ->
                jdbc.execute(
                    "INSERT INTO readings (id, title, content, language, origin, editorial_level, category, created_at, editorial_status) "
                        + "VALUES ('33333333-3333-3333-3333-333333333333', 'Invalid Platform', 'Content', 'en', 'PLATFORM', 'A1', 'Test', NOW(), NULL)"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName(
      "Constraint ck_readings_origin_ownership_metadata rejects USER with non-null editorial_status")
  void constraintRejectsUserWithNonNullEditorialStatus() {
    assertThatThrownBy(
            () ->
                jdbc.execute(
                    "INSERT INTO readings (id, user_id, title, content, language, origin, created_at, editorial_status) "
                        + "VALUES ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', 'Invalid User', 'Content', 'en', 'USER', NOW(), 'PUBLISHED')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName(
      "Partial index idx_readings_platform_published_created_at_desc exists and old index is dropped")
  void partialIndexConfiguredCorrectly() {
    Integer newIndexCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_readings_platform_published_created_at_desc'",
            Integer.class);
    assertThat(newIndexCount).isEqualTo(1);

    Integer oldIndexCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_readings_platform_created_at_desc'",
            Integer.class);
    assertThat(oldIndexCount).isEqualTo(0);
  }
}
