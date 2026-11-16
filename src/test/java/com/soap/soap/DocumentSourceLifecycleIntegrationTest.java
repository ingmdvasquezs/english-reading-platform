package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.exception.DocumentNotFoundException;
import com.soap.soap.application.exception.TransientStorageException;
import com.soap.soap.application.service.ExpiredUploadCleanupService;
import com.soap.soap.application.usecase.DeleteDocumentUseCase;
import com.soap.soap.infrastructure.storage.S3DocumentObjectStorageAdapter;
import com.soap.soap.infrastructure.storage.S3DocumentStorageProperties;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * BACK-003 with real PostgreSQL 17 and LocalStack S3: deleting a document removes its S3 source,
 * and never-confirmed upload intents are cleaned up from database state.
 */
@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentSourceLifecycleIntegrationTest {
  private static final String BUCKET = "document-lifecycle-test-bucket";
  private static final String SHA = "b".repeat(64);

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
          .withServices(LocalStackContainer.Service.S3);

  static {
    LOCALSTACK.start();
  }

  @DynamicPropertySource
  static void s3Properties(DynamicPropertyRegistry registry) {
    registry.add(
        "app.document-storage.s3.endpoint-override",
        () -> LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3).toString());
    registry.add("app.document-storage.s3.region", LOCALSTACK::getRegion);
    registry.add("app.document-storage.s3.path-style-access", () -> "true");
    registry.add("app.document-storage.s3.bucket", () -> BUCKET);
    registry.add("app.document-storage.provider", () -> "S3");
  }

  @Autowired private S3Client s3;
  @Autowired private S3Presigner presigner;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DeleteDocumentUseCase deletions;
  @Autowired private ExpiredUploadCleanupService expiredUploads;

  @BeforeAll
  void createBucket() {
    s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  // ---------------------------------------------------------------- document delete

  @Test
  @DisplayName("A. Deleting an S3 READY document removes the DB row and its S3 source object")
  void deletingAnS3DocumentRemovesItsSource() {
    var owner = user();
    var documentId = UUID.randomUUID();
    var key = sourceKey(owner, documentId);
    putObject(key);
    document(documentId, owner, key, "S3", "READY");

    authenticate(owner);
    deletions.delete(documentId);

    assertThat(documentExists(documentId)).isFalse();
    assertThat(objectExists(key)).isFalse();
  }

  @Test
  @DisplayName("B. Deleting an S3 document whose source is already absent still succeeds")
  void deletingWithAnAlreadyAbsentSourceSucceeds() {
    var owner = user();
    var documentId = UUID.randomUUID();
    var key = sourceKey(owner, documentId);
    document(documentId, owner, key, "S3", "FAILED");

    authenticate(owner);
    deletions.delete(documentId);

    assertThat(documentExists(documentId)).isFalse();
  }

  @Test
  @DisplayName("C. Another user's document cannot be deleted and its S3 object is untouched")
  void anotherUsersDocumentIsHiddenAndUntouched() {
    var owner = user();
    var intruder = user();
    var documentId = UUID.randomUUID();
    var key = sourceKey(owner, documentId);
    putObject(key);
    document(documentId, owner, key, "S3", "READY");

    authenticate(intruder);
    assertThatThrownBy(() -> deletions.delete(documentId))
        .isInstanceOf(DocumentNotFoundException.class);

    assertThat(documentExists(documentId)).isTrue();
    assertThat(objectExists(key)).isTrue();
  }

  @Test
  @DisplayName("D. A FILESYSTEM document never touches S3, even if an object shares its key")
  void filesystemDocumentUsesTheFilesystemAdapterOnly() {
    var owner = user();
    var documentId = UUID.randomUUID();
    var key = sourceKey(owner, documentId);
    putObject(key);
    document(documentId, owner, key, "FILESYSTEM", "READY");

    authenticate(owner);
    deletions.delete(documentId);

    assertThat(documentExists(documentId)).isFalse();
    assertThat(objectExists(key)).isTrue();
  }

  @Test
  @DisplayName("S3 source keys outside the document's own prefix are never deleted")
  void sourceKeyOutsideTheDocumentPrefixIsNotDeleted() {
    var owner = user();
    var victim = user();
    var documentId = UUID.randomUUID();
    var foreignKey = sourceKey(victim, UUID.randomUUID());
    putObject(foreignKey);
    document(documentId, owner, foreignKey, "S3", "READY");

    authenticate(owner);
    deletions.delete(documentId);

    assertThat(documentExists(documentId)).isFalse();
    assertThat(objectExists(foreignKey)).isTrue();
  }

  @Test
  @DisplayName("E. S3 delete failure is surfaced as a retryable storage error (no silent success)")
  void s3DeleteFailureIsSurfaced() {
    var missingBucket =
        new S3DocumentObjectStorageAdapter(
            s3,
            presigner,
            new S3DocumentStorageProperties(
                null,
                null,
                null,
                new S3DocumentStorageProperties.S3Properties(
                    "bucket-that-does-not-exist", LOCALSTACK.getRegion(), null, true)));

    assertThatThrownBy(() -> missingBucket.deleteObject("documents/u/d/source.epub"))
        .isInstanceOf(TransientStorageException.class);
  }

  // ---------------------------------------------------------------- expired upload intents

  @Test
  @DisplayName("Expired uploads A/B/C/D/E: only expired PENDING intents are cleaned, idempotently")
  void expiredPendingUploadsAreCleanedFromDatabaseState() {
    var user = user();
    var now = LocalDateTime.now();

    var expired = upload(user, "PENDING", now.minusHours(3), true);
    var expiredMissingObject = upload(user, "PENDING", now.minusHours(3), false);
    var notExpired = upload(user, "PENDING", now.plusHours(2), true);
    var withinGrace = upload(user, "PENDING", now.minusMinutes(10), true);
    var confirmed = upload(user, "CONFIRMED", now.minusHours(3), true);

    var firstRun = expiredUploads.cleanupExpiredUploads();

    assertThat(firstRun).isGreaterThanOrEqualTo(2);
    assertThat(uploadStatus(expired.id())).isEqualTo("EXPIRED");
    assertThat(objectExists(expired.key())).isFalse();
    assertThat(uploadStatus(expiredMissingObject.id())).isEqualTo("EXPIRED");
    for (var untouched : List.of(notExpired, withinGrace)) {
      assertThat(uploadStatus(untouched.id())).isEqualTo("PENDING");
      assertThat(objectExists(untouched.key())).isTrue();
    }
    assertThat(uploadStatus(confirmed.id())).isEqualTo("CONFIRMED");
    assertThat(objectExists(confirmed.key())).isTrue();

    assertThat(expiredUploads.cleanupExpiredUploads()).isZero();
    assertThat(uploadStatus(expired.id())).isEqualTo("EXPIRED");
  }

  @Test
  @DisplayName("Expired upload whose key is outside its own prefix is expired without deleting")
  void expiredUploadNeverDeletesAnotherUsersObject() {
    var user = user();
    var victim = user();
    var foreignKey = sourceKey(victim, UUID.randomUUID());
    putObject(foreignKey);
    var uploadId = UUID.randomUUID();
    insertUpload(uploadId, UUID.randomUUID(), user, foreignKey, "PENDING", now().minusHours(3));

    expiredUploads.cleanupExpiredUploads();

    assertThat(uploadStatus(uploadId)).isEqualTo("EXPIRED");
    assertThat(objectExists(foreignKey)).isTrue();
  }

  // ---------------------------------------------------------------- helpers

  private record Upload(UUID id, String key) {}

  private Upload upload(UUID user, String status, LocalDateTime expiresAt, boolean withObject) {
    var uploadId = UUID.randomUUID();
    var documentId = UUID.randomUUID();
    var key = sourceKey(user, documentId);
    if (withObject) putObject(key);
    insertUpload(uploadId, documentId, user, key, status, expiresAt);
    return new Upload(uploadId, key);
  }

  private void insertUpload(
      UUID uploadId,
      UUID documentId,
      UUID user,
      String key,
      String status,
      LocalDateTime expiresAt) {
    var created = Timestamp.valueOf(expiresAt.minusHours(24));
    jdbc.update(
        "INSERT INTO document_uploads (id, document_id, user_id, original_filename, format,"
            + " content_type, expected_size_bytes, expected_checksum_sha256, storage_key, status,"
            + " expires_at, confirmed_at, created_at, updated_at, version, storage_provider)"
            + " VALUES (?, ?, ?, 'book.epub', 'EPUB', 'application/epub+zip', 10, ?, ?, ?, ?, ?,"
            + " ?, ?, 0, 'S3')",
        uploadId,
        documentId,
        user,
        SHA,
        key,
        status,
        Timestamp.valueOf(expiresAt),
        "CONFIRMED".equals(status) ? created : null,
        created,
        created);
  }

  private UUID user() {
    var id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, created_at) VALUES (?, 'U', ?, 'h', ?)",
        id,
        "lifecycle-" + id + "@example.com",
        Timestamp.valueOf(now()));
    return id;
  }

  private void document(UUID id, UUID owner, String key, String provider, String status) {
    jdbc.update(
        "INSERT INTO imported_documents (id, user_id, title, language, format, import_status,"
            + " failure_reason, source_asset_key, original_filename, source_sha256,"
            + " chunking_version, source_storage_provider, created_at, updated_at)"
            + " VALUES (?, ?, 'Doc', 'en', 'EPUB', ?, ?, ?, 'book.epub', ?, 5, ?, ?, ?)",
        id,
        owner,
        status,
        "FAILED".equals(status) ? "INVALID_EPUB" : null,
        key,
        UUID.randomUUID().toString().replace("-", "") + "0".repeat(32),
        provider,
        Timestamp.valueOf(now()),
        Timestamp.valueOf(now()));
  }

  private static String sourceKey(UUID owner, UUID documentId) {
    return "documents/" + owner + "/" + documentId + "/source.epub";
  }

  private void putObject(String key) {
    s3.putObject(
        PutObjectRequest.builder().bucket(BUCKET).key(key).build(),
        RequestBody.fromString("epub bytes", StandardCharsets.UTF_8));
  }

  private boolean objectExists(String key) {
    try {
      s3.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(key).build());
      return true;
    } catch (NoSuchKeyException exception) {
      return false;
    } catch (software.amazon.awssdk.services.s3.model.S3Exception exception) {
      if (exception.statusCode() == 404) return false;
      throw exception;
    }
  }

  private boolean documentExists(UUID id) {
    return jdbc.queryForObject(
            "SELECT count(*) FROM imported_documents WHERE id = ?", Long.class, id)
        == 1;
  }

  private String uploadStatus(UUID id) {
    return jdbc.queryForObject(
        "SELECT status FROM document_uploads WHERE id = ?", String.class, id);
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
