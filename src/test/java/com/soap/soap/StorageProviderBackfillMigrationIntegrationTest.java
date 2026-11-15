package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** BACK-010: V42 restores S3 only for documents with unambiguous S3 import-job evidence. */
@Testcontainers
class StorageProviderBackfillMigrationIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static final LocalDateTime CREATED = LocalDateTime.parse("2026-10-01T10:00:00");
  private static final LocalDateTime UPDATED = LocalDateTime.parse("2026-10-01T10:05:00");
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID S3_IMPORTED = UUID.randomUUID();
  private static final UUID LEGACY_NO_JOB = UUID.randomUUID();
  private static final UUID FILESYSTEM_JOB = UUID.randomUUID();
  private static final UUID S3_JOB_OTHER_KEY = UUID.randomUUID();
  private static final UUID ALREADY_S3 = UUID.randomUUID();

  private static JdbcTemplate jdbc;

  @BeforeAll
  static void migrateAcrossV42() {
    var dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);

    flyway(dataSource, "41").migrate();

    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, created_at) VALUES (?, 'U', ?, 'h', ?)",
        USER_ID,
        "v42-" + USER_ID + "@example.com",
        Timestamp.valueOf(CREATED));
    document(S3_IMPORTED, "READY", "documents/u/1/source.epub", "FILESYSTEM", null);
    job(S3_IMPORTED, "documents/u/1/source.epub", "S3", "COMPLETED");

    document(LEGACY_NO_JOB, "READY", "local/2/source.epub", "FILESYSTEM", null);

    document(FILESYSTEM_JOB, "READY", "local/3/source.epub", "FILESYSTEM", null);
    job(FILESYSTEM_JOB, "local/3/source.epub", "FILESYSTEM", "COMPLETED");

    document(S3_JOB_OTHER_KEY, "READY", "local/4/source.epub", "FILESYSTEM", null);
    job(S3_JOB_OTHER_KEY, "documents/u/4/other.epub", "S3", "COMPLETED");

    document(ALREADY_S3, "FAILED", "documents/u/5/source.epub", "S3", "INVALID_EPUB");
    job(ALREADY_S3, "documents/u/5/source.epub", "S3", "FAILED");

    flyway(dataSource, "42").migrate();
  }

  @Test
  @DisplayName("Document with a matching S3 import job is restored to S3")
  void restoresS3ForDocumentsBackedByAnS3Job() {
    assertThat(provider(S3_IMPORTED)).isEqualTo("S3");
    assertThat(updatedAt(S3_IMPORTED)).isEqualTo(UPDATED);
  }

  @Test
  @DisplayName("Genuine or ambiguous FILESYSTEM documents are left untouched")
  void leavesFilesystemAndAmbiguousDocumentsUntouched() {
    assertThat(provider(LEGACY_NO_JOB)).isEqualTo("FILESYSTEM");
    assertThat(provider(FILESYSTEM_JOB)).isEqualTo("FILESYSTEM");
    assertThat(provider(S3_JOB_OTHER_KEY)).isEqualTo("FILESYSTEM");
    assertThat(provider(ALREADY_S3)).isEqualTo("S3");
  }

  private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(target)
        .load();
  }

  private static void document(
      UUID id, String status, String sourceKey, String provider, String failureReason) {
    jdbc.update(
        "INSERT INTO imported_documents (id, user_id, title, language, format, import_status,"
            + " failure_reason, source_asset_key, original_filename, source_sha256,"
            + " chunking_version, source_storage_provider, created_at, updated_at)"
            + " VALUES (?, ?, 'Doc', 'en', 'EPUB', ?, ?, ?, 'book.epub', ?, 5, ?, ?, ?)",
        id,
        USER_ID,
        status,
        failureReason,
        sourceKey,
        id.toString().replace("-", "") + "0".repeat(32),
        provider,
        Timestamp.valueOf(CREATED),
        Timestamp.valueOf(UPDATED));
  }

  private static void job(UUID documentId, String sourceKey, String provider, String status) {
    jdbc.update(
        "INSERT INTO import_jobs (id, document_id, user_id, status, source_asset_key,"
            + " storage_provider, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        documentId,
        USER_ID,
        status,
        sourceKey,
        provider,
        Timestamp.valueOf(CREATED),
        Timestamp.valueOf(UPDATED));
  }

  private static String provider(UUID documentId) {
    return jdbc.queryForObject(
        "SELECT source_storage_provider FROM imported_documents WHERE id = ?",
        String.class,
        documentId);
  }

  private static LocalDateTime updatedAt(UUID documentId) {
    return jdbc.queryForObject(
            "SELECT updated_at FROM imported_documents WHERE id = ?", Timestamp.class, documentId)
        .toLocalDateTime();
  }
}
