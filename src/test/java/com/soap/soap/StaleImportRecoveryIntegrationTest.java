package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.StaleImportRepositoryPort;
import com.soap.soap.application.service.StaleImportRecoveryService;
import com.soap.soap.application.usecase.DeleteDocumentUseCase;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.domain.model.StorageProvider;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * BACK-004 against real PostgreSQL 17: stale legacy and durable imports are recovered to FAILED,
 * active ones are never touched, and recovered documents can be deleted and uploaded again.
 * Defaults: legacy stale after 1h, durable lease stale after 30m.
 */
@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
class StaleImportRecoveryIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private StaleImportRecoveryService recovery;
  @Autowired private StaleImportRepositoryPort staleImports;
  @Autowired private DeleteDocumentUseCase deletions;
  @Autowired private ImportedDocumentRepositoryPort documents;

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("A/B/F. Legacy PROCESSING older than 1h → FAILED; recent or READY untouched")
  void legacyStaleProcessingIsRecoveredConservatively() {
    var user = user();
    var stale = legacyDocument(user, "PROCESSING", now().minusHours(2));
    var recent = legacyDocument(user, "PROCESSING", now().minusMinutes(10));
    var ready = legacyDocument(user, "READY", now().minusDays(3));

    recovery.recoverStaleImports();

    assertThat(document(stale))
        .containsEntry("import_status", "FAILED")
        .containsEntry("failure_reason", "IMPORT_FAILURE")
        .containsEntry("deduplication_sha256", null);
    assertThat(document(recent)).containsEntry("import_status", "PROCESSING");
    assertThat(document(ready)).containsEntry("import_status", "READY");
  }

  @Test
  @DisplayName("C. Durable PROCESSING with an active (or recently ended) lease is untouched")
  void durableJobWithActiveLeaseIsUntouched() {
    var user = user();
    var active = durableDocument(user);
    var activeJob = job(active, user, "PROCESSING", now().plusSeconds(40), now().minusHours(3));
    var recentlyEnded = durableDocument(user);
    var recentJob =
        job(recentlyEnded, user, "PROCESSING", now().minusMinutes(5), now().minusHours(3));

    recovery.recoverStaleImports();

    assertThat(jobStatus(activeJob)).isEqualTo("PROCESSING");
    assertThat(document(active)).containsEntry("import_status", "PROCESSING");
    assertThat(jobStatus(recentJob)).isEqualTo("PROCESSING");
    assertThat(document(recentlyEnded)).containsEntry("import_status", "PROCESSING");
  }

  @Test
  @DisplayName("D. Durable PROCESSING whose lease ended long ago → job and document FAILED")
  void durableJobWithLongExpiredLeaseIsRecovered() {
    var user = user();
    var documentId = durableDocument(user);
    var jobId = job(documentId, user, "PROCESSING", now().minusHours(2), now().minusHours(3));

    recovery.recoverStaleImports();

    var job = jdbc.queryForMap("SELECT * FROM import_jobs WHERE id = ?", jobId);
    assertThat(job.get("status")).isEqualTo("FAILED");
    assertThat(job.get("last_error_code")).isEqualTo("STALE_PROCESSING");
    assertThat(job.get("finished_at")).isNotNull();
    assertThat(document(documentId))
        .containsEntry("import_status", "FAILED")
        .containsEntry("failure_reason", "IMPORT_FAILURE")
        .containsEntry("deduplication_sha256", null);
  }

  @Test
  @DisplayName("E. COMPLETED job with an old lease and its READY document are untouched")
  void completedJobsAreUntouched() {
    var user = user();
    var documentId = durableDocument(user, "READY");
    var jobId = job(documentId, user, "COMPLETED", now().minusDays(2), now().minusDays(2));

    recovery.recoverStaleImports();

    assertThat(jobStatus(jobId)).isEqualTo("COMPLETED");
    assertThat(document(documentId)).containsEntry("import_status", "READY");
  }

  @Test
  @DisplayName("Durable documents are never treated as legacy, even when labelled FILESYSTEM")
  void durableDocumentIsNeverRecoveredByTheLegacyRule() {
    var user = user();
    // Pre-V42 S3 documents could carry FILESYSTEM; the job row is what makes them durable.
    var documentId = legacyDocument(user, "PROCESSING", now().minusHours(5));
    var jobId = job(documentId, user, "PENDING", null, now().minusHours(5));

    recovery.recoverStaleImports();

    assertThat(jobStatus(jobId)).isEqualTo("PENDING");
    assertThat(document(documentId)).containsEntry("import_status", "PROCESSING");
  }

  @Test
  @DisplayName("TOCTOU: a lease renewed after the scan makes the recovery update a no-op")
  void leaseRenewedAfterScanIsNotOverwritten() {
    var user = user();
    var documentId = durableDocument(user);
    var jobId = job(documentId, user, "PROCESSING", now().minusHours(2), now().minusHours(3));
    var cutoff = now().minusMinutes(30);

    var candidates = staleImports.findStaleDurableImports(cutoff, 1_000);
    assertThat(candidates).anyMatch(candidate -> candidate.jobId().equals(jobId));

    // The worker heartbeat renews the lease between the scan and the update.
    jdbc.update(
        "UPDATE import_jobs SET lease_until = ? WHERE id = ?",
        Timestamp.valueOf(now().plusMinutes(1)),
        jobId);

    assertThat(staleImports.failStaleDurableJob(jobId, cutoff, now(), "STALE_PROCESSING", "x"))
        .isFalse();
    assertThat(jobStatus(jobId)).isEqualTo("PROCESSING");
    assertThat(document(documentId)).containsEntry("import_status", "PROCESSING");
  }

  @Test
  @DisplayName("G/H. After recovery the document can be deleted and the same source re-uploaded")
  void recoveredDocumentCanBeDeletedAndUploadedAgain() {
    var user = user();
    var sha = sha();
    var stale = legacyDocument(user, "PROCESSING", now().minusHours(2), sha);
    var second = legacyDocument(user, "PROCESSING", now().minusHours(2), sha());

    recovery.recoverStaleImports();
    assertThat(document(stale)).containsEntry("import_status", "FAILED");

    // H: the deduplication hash was released, so the same file can be imported again even
    // before the FAILED document is deleted.
    var reupload =
        documents.saveDocument(
            new ImportedDocument(
                null,
                user,
                "Book again",
                null,
                "en",
                DocumentFormat.EPUB,
                null,
                "local/" + UUID.randomUUID() + "/source.epub",
                StorageProvider.FILESYSTEM,
                "book.epub",
                sha,
                DocumentImportStatus.PROCESSING,
                null,
                5,
                now(),
                now()));
    assertThat(document(reupload.id())).containsEntry("deduplication_sha256", sha);

    // G: a recovered (FAILED) document is no longer blocked by the PROCESSING delete guard.
    authenticate(user);
    deletions.delete(second);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM imported_documents WHERE id = ?", Long.class, second))
        .isZero();
  }

  // ---------------------------------------------------------------- helpers

  private UUID user() {
    var id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, created_at) VALUES (?, 'U', ?, 'h', ?)",
        id,
        "stale-" + id + "@example.com",
        Timestamp.valueOf(now()));
    return id;
  }

  private UUID legacyDocument(UUID user, String status, LocalDateTime updatedAt) {
    return legacyDocument(user, status, updatedAt, sha());
  }

  private UUID legacyDocument(UUID user, String status, LocalDateTime updatedAt, String sha) {
    return insertDocument(user, status, "FILESYSTEM", updatedAt, sha);
  }

  private UUID durableDocument(UUID user) {
    return durableDocument(user, "PROCESSING");
  }

  private UUID durableDocument(UUID user, String status) {
    return insertDocument(user, status, "S3", now().minusHours(3), sha());
  }

  private UUID insertDocument(
      UUID user, String status, String provider, LocalDateTime updatedAt, String sha) {
    var id = UUID.randomUUID();
    var active = "PROCESSING".equals(status) || "READY".equals(status);
    jdbc.update(
        "INSERT INTO imported_documents (id, user_id, title, language, format, import_status,"
            + " source_asset_key, original_filename, source_sha256, deduplication_sha256,"
            + " chunking_version, source_storage_provider, created_at, updated_at)"
            + " VALUES (?, ?, 'Doc', 'en', 'EPUB', ?, ?, 'book.epub', ?, ?, 5, ?, ?, ?)",
        id,
        user,
        status,
        ("S3".equals(provider) ? "documents/" + user + "/" : "local/") + id + "/source.epub",
        sha,
        active ? sha : null,
        provider,
        Timestamp.valueOf(updatedAt.minusMinutes(1)),
        Timestamp.valueOf(updatedAt));
    return id;
  }

  private UUID job(
      UUID documentId,
      UUID user,
      String status,
      LocalDateTime leaseUntil,
      LocalDateTime updatedAt) {
    var id = UUID.randomUUID();
    var processing = "PROCESSING".equals(status);
    jdbc.update(
        "INSERT INTO import_jobs (id, document_id, user_id, status, attempt_count, max_attempts,"
            + " source_asset_key, storage_provider, worker_id, lease_token, lease_until,"
            + " heartbeat_at, created_at, updated_at, version)"
            + " VALUES (?, ?, ?, ?, 1, 3, ?, 'S3', ?, ?, ?, ?, ?, ?, 0)",
        id,
        documentId,
        user,
        status,
        "documents/" + user + "/" + documentId + "/source.epub",
        processing ? "worker-1" : null,
        processing ? UUID.randomUUID() : null,
        leaseUntil == null ? null : Timestamp.valueOf(leaseUntil),
        leaseUntil == null ? null : Timestamp.valueOf(leaseUntil.minusSeconds(60)),
        Timestamp.valueOf(updatedAt.minusMinutes(1)),
        Timestamp.valueOf(updatedAt));
    return id;
  }

  private Map<String, Object> document(UUID id) {
    return jdbc.queryForMap(
        "SELECT import_status, failure_reason, deduplication_sha256 FROM imported_documents"
            + " WHERE id = ?",
        id);
  }

  private String jobStatus(UUID id) {
    return jdbc.queryForObject("SELECT status FROM import_jobs WHERE id = ?", String.class, id);
  }

  private static String sha() {
    return UUID.randomUUID().toString().replace("-", "") + "0".repeat(32);
  }

  private static LocalDateTime now() {
    return LocalDateTime.now();
  }

  private static void authenticate(UUID userId) {
    var jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(userId.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(Duration.ofHours(1)))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(jwt, "token", List.of()));
  }
}
