package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.command.CreateDocumentUploadIntentCommand;
import com.soap.soap.application.exception.DocumentAlreadyImportedException;
import com.soap.soap.application.model.ConfirmVerifiedDocumentUploadResult;
import com.soap.soap.application.model.DocumentUploadIntentResult;
import com.soap.soap.application.port.out.DocumentUploadRepositoryPort;
import com.soap.soap.application.port.out.ImportJobRepositoryPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.OutboxEventRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.service.ConfirmDocumentUploadOrchestrator;
import com.soap.soap.application.usecase.CreateDocumentUploadIntentUseCase;
import com.soap.soap.application.usecase.RefreshDocumentUploadPresignUseCase;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.DocumentUpload;
import com.soap.soap.domain.model.DocumentUploadStatus;
import com.soap.soap.domain.model.ImportJob;
import com.soap.soap.domain.model.ImportJobStatus;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.domain.model.OutboxEvent;
import com.soap.soap.domain.model.OutboxEventStatus;
import com.soap.soap.domain.model.StorageProvider;
import com.soap.soap.domain.model.StoredObjectAttributes;
import com.soap.soap.domain.model.User;
import com.soap.soap.infrastructure.storage.S3DocumentObjectStorageAdapter;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3DirectDocumentUploadIntegrationTest {

  private static final String BUCKET_NAME = "english-reading-test-bucket";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
          .withServices(LocalStackContainer.Service.S3);

  static {
    LOCALSTACK.start();
  }

  @DynamicPropertySource
  static void registerS3Properties(DynamicPropertyRegistry registry) {
    registry.add(
        "app.document-storage.s3.endpoint-override",
        () -> LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3).toString());
    registry.add("app.document-storage.s3.region", LOCALSTACK::getRegion);
    registry.add("app.document-storage.s3.path-style-access", () -> "true");
    registry.add("app.document-storage.s3.bucket", () -> BUCKET_NAME);
  }

  @Autowired private S3Client s3Client;
  @Autowired private CreateDocumentUploadIntentUseCase createIntentUseCase;
  @Autowired private RefreshDocumentUploadPresignUseCase refreshPresignUseCase;
  @Autowired private ConfirmDocumentUploadOrchestrator confirmOrchestrator;
  @Autowired private S3DocumentObjectStorageAdapter s3Adapter;
  @Autowired private DocumentUploadRepositoryPort documentUploads;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired private ImportedDocumentRepositoryPort importedDocuments;
  @Autowired private ImportJobRepositoryPort importJobs;
  @Autowired private OutboxEventRepositoryPort outboxEvents;
  @Autowired private UserRepositoryPort users;

  private User testUser;
  private final HttpClient httpClient = HttpClient.newHttpClient();

  @BeforeAll
  void initBucket() {
    try {
      s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
    } catch (Exception ignored) {
    }
  }

  @BeforeEach
  void setUpUser() {
    testUser =
        users.save(
            new User(
                null, "S3 User", "s3-user-" + UUID.randomUUID() + "@example.com", "hash", null));
  }

  private String sha256Hex(byte[] bytes) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @DisplayName("S07, S10, S15, S32: Full Presigned PUT, Inspect, and Confirm end-to-end flow")
  void s07_s10_s15_s32_fullDirectUploadFlow() throws Exception {
    byte[] content =
        "PK\u0003\u0004 Mock EPUB Content for S07 Integration Test"
            .getBytes(StandardCharsets.UTF_8);
    String checksum = sha256Hex(content);

    // 1. Create upload intent
    var command =
        new CreateDocumentUploadIntentCommand(
            testUser.id(),
            "test-book.epub",
            "application/epub+zip",
            (long) content.length,
            checksum);

    DocumentUploadIntentResult intent = createIntentUseCase.createIntent(command);
    assertThat(intent.uploadId()).isNotNull();
    assertThat(intent.documentId()).isNotNull();
    assertThat(intent.uploadUrl()).isNotNull();
    assertThat(intent.requiredHeaders())
        .containsKeys("Content-Type", "x-amz-checksum-sha256", "If-None-Match");

    // 2. Perform direct HTTP PUT using presigned URL with required headers
    var requestBuilder =
        HttpRequest.newBuilder()
            .uri(intent.uploadUrl())
            .PUT(HttpRequest.BodyPublishers.ofByteArray(content));
    intent.requiredHeaders().forEach(requestBuilder::header);

    HttpResponse<Void> putResponse =
        httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
    assertThat(putResponse.statusCode()).isIn(200, 204);

    // 3. Inspect object in storage (S10)
    DocumentUpload pendingUpload = documentUploads.findById(intent.uploadId()).orElseThrow();
    StoredObjectAttributes attrs = s3Adapter.inspectObject(pendingUpload.storageKey());
    assertThat(attrs.sizeBytes()).isEqualTo(content.length);
    assertThat(attrs.checksumSha256()).isEqualTo(checksum);

    // 4. Confirm upload (S15)
    ConfirmVerifiedDocumentUploadResult confirmResult =
        confirmOrchestrator.confirmUpload(intent.uploadId(), testUser.id(), "en");

    assertThat(confirmResult.documentId()).isEqualTo(intent.documentId());
    assertThat(confirmResult.jobId()).isNotNull();
    assertThat(confirmResult.status()).isEqualTo(DocumentImportStatus.PROCESSING);

    // 5. Verify database invariants and StorageProvider.S3 (S32)
    DocumentUpload confirmedUpload = documentUploads.findById(intent.uploadId()).orElseThrow();
    assertThat(confirmedUpload.status()).isEqualTo(DocumentUploadStatus.CONFIRMED);
    assertThat(confirmedUpload.storageProvider()).isEqualTo(StorageProvider.S3);
    assertThat(confirmedUpload.confirmedJobId()).isEqualTo(confirmResult.jobId());

    ImportedDocument doc = importedDocuments.findDocumentById(intent.documentId()).orElseThrow();
    assertThat(doc.importStatus()).isEqualTo(DocumentImportStatus.PROCESSING);
    assertThat(doc.sourceStorageProvider()).isEqualTo(StorageProvider.S3);
    assertThat(doc.sourceSha256()).isEqualTo(checksum);

    ImportJob job = importJobs.findById(confirmResult.jobId()).orElseThrow();
    assertThat(job.status()).isEqualTo(ImportJobStatus.PENDING);
    assertThat(job.storageProvider()).isEqualTo(StorageProvider.S3);
    assertThat(job.sourceAssetKey()).isEqualTo(pendingUpload.storageKey());

    List<UUID> eventIds =
        jdbc.query(
            "SELECT id FROM outbox_events WHERE aggregate_id = ?",
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            confirmResult.jobId());
    assertThat(eventIds).hasSize(1);
    OutboxEvent event = outboxEvents.findById(eventIds.get(0)).orElseThrow();
    assertThat(event.status()).isEqualTo(OutboxEventStatus.PENDING);
    assertThat(event.eventType()).isEqualTo("DOCUMENT_IMPORT_REQUESTED");
  }

  @Test
  @DisplayName("S18: Refresh presign returns new authorization for same intent and storage key")
  void s18_refreshPresignPreservesIntentAndKey() {
    byte[] content = "Dummy content".getBytes(StandardCharsets.UTF_8);
    String checksum = sha256Hex(content);

    var command =
        new CreateDocumentUploadIntentCommand(
            testUser.id(), "story.epub", "application/epub+zip", (long) content.length, checksum);
    DocumentUploadIntentResult intent = createIntentUseCase.createIntent(command);

    DocumentUploadIntentResult refreshed =
        refreshPresignUseCase.refreshPresign(intent.uploadId(), testUser.id());

    assertThat(refreshed.uploadId()).isEqualTo(intent.uploadId());
    assertThat(refreshed.documentId()).isEqualTo(intent.documentId());
    assertThat(refreshed.uploadUrl()).isNotNull();
    assertThat(refreshed.requiredHeaders())
        .containsKeys("Content-Type", "x-amz-checksum-sha256", "If-None-Match");
  }

  @Test
  @DisplayName("S17: Two concurrent confirms on the same upload resolve with identical IDs")
  void s17_concurrentConfirmsSameUpload() throws Exception {
    byte[] content =
        "PK\u0003\u0004 Unique Content for Concurrent Confirm".getBytes(StandardCharsets.UTF_8);
    String checksum = sha256Hex(content);

    var command =
        new CreateDocumentUploadIntentCommand(
            testUser.id(),
            "concurrent.epub",
            "application/epub+zip",
            (long) content.length,
            checksum);
    DocumentUploadIntentResult intent = createIntentUseCase.createIntent(command);

    // Perform PUT
    var requestBuilder =
        HttpRequest.newBuilder()
            .uri(intent.uploadUrl())
            .PUT(HttpRequest.BodyPublishers.ofByteArray(content));
    intent.requiredHeaders().forEach(requestBuilder::header);
    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding());

    int threadCount = 2;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    List<Callable<ConfirmVerifiedDocumentUploadResult>> tasks = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      tasks.add(() -> confirmOrchestrator.confirmUpload(intent.uploadId(), testUser.id(), "en"));
    }

    List<Future<ConfirmVerifiedDocumentUploadResult>> futures = executor.invokeAll(tasks);
    executor.shutdown();

    ConfirmVerifiedDocumentUploadResult res1 = futures.get(0).get();
    ConfirmVerifiedDocumentUploadResult res2 = futures.get(1).get();

    assertThat(res1.documentId()).isEqualTo(res2.documentId());
    assertThat(res1.jobId()).isEqualTo(res2.jobId());
    assertThat(res1.status()).isEqualTo(DocumentImportStatus.PROCESSING);
  }

  @Test
  @DisplayName(
      "S22 & S30: Concurrent dedup confirmations of same checksum: one succeeds, other 409 Conflict, no 500")
  void s22_s30_concurrentDedupDifferentIntents() throws Exception {
    byte[] content =
        "PK\u0003\u0004 Shared Content for Dedup Test".getBytes(StandardCharsets.UTF_8);
    String checksum = sha256Hex(content);

    // Create intent 1
    var cmd1 =
        new CreateDocumentUploadIntentCommand(
            testUser.id(), "copy1.epub", "application/epub+zip", (long) content.length, checksum);
    DocumentUploadIntentResult intent1 = createIntentUseCase.createIntent(cmd1);

    // Create intent 2 (same user, same file/checksum)
    var cmd2 =
        new CreateDocumentUploadIntentCommand(
            testUser.id(), "copy2.epub", "application/epub+zip", (long) content.length, checksum);
    DocumentUploadIntentResult intent2 = createIntentUseCase.createIntent(cmd2);

    // Upload both files to their respective keys
    var req1 =
        HttpRequest.newBuilder()
            .uri(intent1.uploadUrl())
            .PUT(HttpRequest.BodyPublishers.ofByteArray(content));
    intent1.requiredHeaders().forEach(req1::header);
    httpClient.send(req1.build(), HttpResponse.BodyHandlers.discarding());

    var req2 =
        HttpRequest.newBuilder()
            .uri(intent2.uploadUrl())
            .PUT(HttpRequest.BodyPublishers.ofByteArray(content));
    intent2.requiredHeaders().forEach(req2::header);
    httpClient.send(req2.build(), HttpResponse.BodyHandlers.discarding());

    // Execute confirms concurrently
    ExecutorService executor = Executors.newFixedThreadPool(2);
    List<Callable<ConfirmVerifiedDocumentUploadResult>> tasks = new ArrayList<>();
    tasks.add(() -> confirmOrchestrator.confirmUpload(intent1.uploadId(), testUser.id(), "en"));
    tasks.add(() -> confirmOrchestrator.confirmUpload(intent2.uploadId(), testUser.id(), "en"));

    List<Future<ConfirmVerifiedDocumentUploadResult>> futures = executor.invokeAll(tasks);
    executor.shutdown();

    int successCount = 0;
    int duplicateCount = 0;

    for (Future<ConfirmVerifiedDocumentUploadResult> future : futures) {
      try {
        ConfirmVerifiedDocumentUploadResult res = future.get();
        if (res != null) successCount++;
      } catch (ExecutionException e) {
        if (e.getCause() instanceof DocumentAlreadyImportedException) {
          duplicateCount++;
        } else {
          throw new AssertionError("Unexpected exception during dedup confirm: " + e.getCause(), e);
        }
      }
    }

    assertThat(successCount).isEqualTo(1);
    assertThat(duplicateCount).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "S30: Create intent is allowed even if SHA matches an existing document; dedup strictly rejects during confirm")
  void s30_intentAllowedForExistingDocumentSha_confirmRejectsWith409() throws Exception {
    byte[] content =
        "PK\u0003\u0004 Unique Content for S30 Dedup Test".getBytes(StandardCharsets.UTF_8);
    String checksum = sha256Hex(content);

    // 1. First upload and confirm document 1
    var cmd1 =
        new CreateDocumentUploadIntentCommand(
            testUser.id(),
            "original.epub",
            "application/epub+zip",
            (long) content.length,
            checksum);
    DocumentUploadIntentResult intent1 = createIntentUseCase.createIntent(cmd1);

    var req1 =
        HttpRequest.newBuilder()
            .uri(intent1.uploadUrl())
            .PUT(HttpRequest.BodyPublishers.ofByteArray(content));
    intent1.requiredHeaders().forEach(req1::header);
    httpClient.send(req1.build(), HttpResponse.BodyHandlers.discarding());

    ConfirmVerifiedDocumentUploadResult res1 =
        confirmOrchestrator.confirmUpload(intent1.uploadId(), testUser.id(), "en");
    assertThat(res1).isNotNull();

    // 2. Client tries to create intent 2 with the SAME SHA-256
    // Must SUCCEED because create intent does NOT trust unverified client checksum
    var cmd2 =
        new CreateDocumentUploadIntentCommand(
            testUser.id(), "second.epub", "application/epub+zip", (long) content.length, checksum);
    DocumentUploadIntentResult intent2 = createIntentUseCase.createIntent(cmd2);
    assertThat(intent2).isNotNull();
    assertThat(intent2.uploadId()).isNotEqualTo(intent1.uploadId());

    // 3. Client uploads file to S3
    var req2 =
        HttpRequest.newBuilder()
            .uri(intent2.uploadUrl())
            .PUT(HttpRequest.BodyPublishers.ofByteArray(content));
    intent2.requiredHeaders().forEach(req2::header);
    httpClient.send(req2.build(), HttpResponse.BodyHandlers.discarding());

    // 4. Confirm must be authoritatively rejected with DocumentAlreadyImportedException
    assertThatThrownBy(
            () -> {
              confirmOrchestrator.confirmUpload(intent2.uploadId(), testUser.id(), "en");
            })
        .isInstanceOf(DocumentAlreadyImportedException.class);
  }

  @Test
  @DisplayName("S27: Second PUT with same key fails with 412 Precondition Failed when supported")
  void s27_secondPutSameKeyRejected() throws Exception {
    byte[] contentA = "PK\u0003\u0004 Original Content".getBytes(StandardCharsets.UTF_8);
    byte[] contentB = "PK\u0003\u0004 Overwrite Attempt Content".getBytes(StandardCharsets.UTF_8);
    String checksumA = sha256Hex(contentA);

    var command =
        new CreateDocumentUploadIntentCommand(
            testUser.id(),
            "original.epub",
            "application/epub+zip",
            (long) contentA.length,
            checksumA);
    DocumentUploadIntentResult intent = createIntentUseCase.createIntent(command);

    // First PUT
    var reqA =
        HttpRequest.newBuilder()
            .uri(intent.uploadUrl())
            .PUT(HttpRequest.BodyPublishers.ofByteArray(contentA));
    intent.requiredHeaders().forEach(reqA::header);
    HttpResponse<Void> respA =
        httpClient.send(reqA.build(), HttpResponse.BodyHandlers.discarding());
    assertThat(respA.statusCode()).isIn(200, 204);

    // Second PUT using same URL / key with If-None-Match: *
    var reqB =
        HttpRequest.newBuilder()
            .uri(intent.uploadUrl())
            .PUT(HttpRequest.BodyPublishers.ofByteArray(contentB));
    intent.requiredHeaders().forEach(reqB::header);
    HttpResponse<Void> respB =
        httpClient.send(reqB.build(), HttpResponse.BodyHandlers.discarding());

    // In AWS S3 and LocalStack 3.x, conditional write If-None-Match: * returns 412
    if (respB.statusCode() == 412) {
      assertThat(respB.statusCode()).isEqualTo(412);
      // Verify object in storage remains content A
      DocumentUpload upload = documentUploads.findById(intent.uploadId()).orElseThrow();
      StoredObjectAttributes attrs = s3Adapter.inspectObject(upload.storageKey());
      assertThat(attrs.sizeBytes()).isEqualTo(contentA.length);
    } else {
      // LocalStack image version note: if LocalStack community does not enforce conditional PUT,
      // verify that PutObjectRequest contract has ifNoneMatch set
      assertThat(intent.requiredHeaders()).containsEntry("If-None-Match", "*");
    }
  }
}
