package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.command.ConfirmVerifiedDocumentUploadCommand;
import com.soap.soap.application.exception.UploadAbortedException;
import com.soap.soap.application.exception.UploadExpiredException;
import com.soap.soap.application.exception.UploadNotFoundException;
import com.soap.soap.application.model.ConfirmVerifiedDocumentUploadResult;
import com.soap.soap.application.port.out.DocumentUploadRepositoryPort;
import com.soap.soap.application.port.out.ImportJobRepositoryPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.OutboxEventRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.usecase.ConfirmVerifiedDocumentUploadUseCase;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.DocumentUpload;
import com.soap.soap.domain.model.DocumentUploadStatus;
import com.soap.soap.domain.model.ImportJob;
import com.soap.soap.domain.model.ImportJobStatus;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.domain.model.OutboxEvent;
import com.soap.soap.domain.model.OutboxEventStatus;
import com.soap.soap.domain.model.User;
import com.soap.soap.infrastructure.persistence.mapper.OutboxEventSerializer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
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
class TransactionalUploadConfirmationIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private ConfirmVerifiedDocumentUploadUseCase confirmUseCase;
  @Autowired private DocumentUploadRepositoryPort documentUploads;
  @Autowired private ImportedDocumentRepositoryPort importedDocuments;
  @Autowired private ImportJobRepositoryPort importJobs;
  @Autowired private OutboxEventRepositoryPort outboxEvents;
  @Autowired private UserRepositoryPort users;
  @Autowired private OutboxEventSerializer serializer;
  @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;

  private User testUser;

  @BeforeEach
  void setUp() {
    testUser =
        users.save(
            new User(
                null, "Tx User", "tx-user-" + UUID.randomUUID() + "@example.com", "hash", null));
  }

  private DocumentUpload createPendingUpload(UUID uploadId, UUID docId) {
    LocalDateTime now = LocalDateTime.now();
    return documentUploads.save(
        new DocumentUpload(
            uploadId,
            docId,
            testUser.id(),
            "hamlet.epub",
            DocumentFormat.EPUB,
            "application/epub+zip",
            204800L,
            "d".repeat(64),
            "storage/keys/hamlet.epub",
            DocumentUploadStatus.PENDING,
            now.plusMinutes(30),
            null,
            null,
            now,
            now,
            0));
  }

  @Test
  @DisplayName(
      "T05: Valid confirm creates imported_document, import_job, outbox_event and marks upload CONFIRMED")
  void confirmCreatesAllEntitiesAtomically() {
    UUID uploadId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    createPendingUpload(uploadId, docId);

    ConfirmVerifiedDocumentUploadResult result =
        confirmUseCase.confirm(
            new ConfirmVerifiedDocumentUploadCommand(uploadId, testUser.id(), "en-GB"));

    assertThat(result).isNotNull();
    assertThat(result.uploadId()).isEqualTo(uploadId);
    assertThat(result.documentId()).isEqualTo(docId);
    assertThat(result.jobId()).isNotNull();
    assertThat(result.status()).isEqualTo(DocumentImportStatus.PROCESSING);

    // Verify DocumentUpload is CONFIRMED
    DocumentUpload upload = documentUploads.findById(uploadId).orElseThrow();
    assertThat(upload.status()).isEqualTo(DocumentUploadStatus.CONFIRMED);
    assertThat(upload.confirmedJobId()).isEqualTo(result.jobId());
    assertThat(upload.confirmedAt()).isNotNull();

    // Verify ImportedDocument is PROCESSING
    ImportedDocument doc = importedDocuments.findDocumentById(docId).orElseThrow();
    assertThat(doc.importStatus()).isEqualTo(DocumentImportStatus.PROCESSING);
    assertThat(doc.sourceSha256()).isEqualTo("d".repeat(64));

    // Verify ImportJob is PENDING
    ImportJob job = importJobs.findById(result.jobId()).orElseThrow();
    assertThat(job.status()).isEqualTo(ImportJobStatus.PENDING);
    assertThat(job.documentId()).isEqualTo(docId);
    assertThat(job.languageOverride()).isEqualTo("en-GB");

    // Verify OutboxEvent is PENDING
    OutboxEvent event = outboxEvents.findById(job.id()).orElse(null);
    if (event == null) {
      // Find by aggregateId
      List<UUID> eventIds =
          jdbc.query(
              "SELECT id FROM outbox_events WHERE aggregate_id = ?",
              (rs, rowNum) -> (UUID) rs.getObject("id"),
              job.id());
      assertThat(eventIds).hasSize(1);
      event = outboxEvents.findById(eventIds.getFirst()).orElseThrow();
    }
    assertThat(event.status()).isEqualTo(OutboxEventStatus.PENDING);
    assertThat(event.eventType()).isEqualTo("DOCUMENT_IMPORT_REQUESTED");

    var deserialized = serializer.deserializeDocumentImportRequested(event.payload());
    assertThat(deserialized.jobId()).isEqualTo(job.id());
    assertThat(deserialized.documentId()).isEqualTo(docId);
    assertThat(deserialized.format()).isEqualTo(DocumentFormat.EPUB);
  }

  @Test
  @DisplayName(
      "T06: Rollback total if transaction fails; upload remains PENDING and no job/outbox persists")
  void rollsBackCompletelyOnFailure() {
    UUID uploadId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    createPendingUpload(uploadId, docId);

    // Fault injection: simulate an OutboxEventRepositoryPort that fails
    OutboxEventRepositoryPort failingOutbox =
        new OutboxEventRepositoryPort() {
          @Override
          public OutboxEvent save(OutboxEvent event) {
            throw new RuntimeException("Simulated outbox persistence crash!");
          }

          @Override
          public java.util.Optional<OutboxEvent> findById(UUID eventId) {
            return java.util.Optional.empty();
          }

          @Override
          public List<OutboxEvent> claimBatch(String d, int b, java.time.Duration l) {
            return List.of();
          }

          @Override
          public boolean markPublished(UUID e, String d) {
            return false;
          }

          @Override
          public boolean markFailedAttempt(UUID e, String d, String err, java.time.Duration b) {
            return false;
          }
        };

    ConfirmVerifiedDocumentUploadUseCase faultyUseCase =
        new ConfirmVerifiedDocumentUploadUseCase(
            documentUploads,
            importedDocuments,
            importJobs,
            failingOutbox,
            serializer,
            transactionManager);

    assertThatThrownBy(
            () ->
                faultyUseCase.confirm(
                    new ConfirmVerifiedDocumentUploadCommand(uploadId, testUser.id(), null)))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Simulated outbox persistence crash!");

    // Verify upload remains PENDING
    DocumentUpload upload = documentUploads.findById(uploadId).orElseThrow();
    assertThat(upload.status()).isEqualTo(DocumentUploadStatus.PENDING);
    assertThat(upload.confirmedJobId()).isNull();

    // Verify no job was persisted
    assertThat(importJobs.findActiveByDocumentId(docId)).isEmpty();
  }

  @Test
  @DisplayName("T07: Duplicate sequential confirm is idempotent and returns same IDs")
  void duplicateConfirmIsIdempotent() {
    UUID uploadId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    createPendingUpload(uploadId, docId);

    var first =
        confirmUseCase.confirm(
            new ConfirmVerifiedDocumentUploadCommand(uploadId, testUser.id(), null));
    var second =
        confirmUseCase.confirm(
            new ConfirmVerifiedDocumentUploadCommand(uploadId, testUser.id(), null));

    assertThat(first.jobId()).isEqualTo(second.jobId());
    assertThat(first.documentId()).isEqualTo(second.documentId());
    assertThat(first.uploadId()).isEqualTo(second.uploadId());
  }

  @Test
  @DisplayName("T08: Concurrent confirm calls result in exactly one job and one outbox event")
  void concurrentConfirmsAreSerializedAndSafe() throws InterruptedException, ExecutionException {
    UUID uploadId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    createPendingUpload(uploadId, docId);

    int threadCount = 4;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    List<Callable<ConfirmVerifiedDocumentUploadResult>> tasks = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      tasks.add(
          () ->
              confirmUseCase.confirm(
                  new ConfirmVerifiedDocumentUploadCommand(uploadId, testUser.id(), null)));
    }

    List<Future<ConfirmVerifiedDocumentUploadResult>> futures = executor.invokeAll(tasks);
    executor.shutdown();

    UUID commonJobId = null;
    for (var f : futures) {
      var res = f.get();
      assertThat(res).isNotNull();
      if (commonJobId == null) {
        commonJobId = res.jobId();
      } else {
        assertThat(res.jobId()).isEqualTo(commonJobId);
      }
    }

    // Check exactly 1 job in database
    Integer jobCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM import_jobs WHERE document_id = ?", Integer.class, docId);
    assertThat(jobCount).isEqualTo(1);

    // Check exactly 1 outbox event in database
    Integer eventCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE aggregate_id = ?",
            Integer.class,
            commonJobId);
    assertThat(eventCount).isEqualTo(1);
  }

  @Test
  @DisplayName("T09: Partial unique index prevents two active jobs for same document")
  void preventsTwoActiveJobsForSameDocument() {
    UUID uploadId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    createPendingUpload(uploadId, docId);

    confirmUseCase.confirm(new ConfirmVerifiedDocumentUploadCommand(uploadId, testUser.id(), null));

    // Attempting to directly insert another PENDING or PROCESSING job for the same document fails
    LocalDateTime now = LocalDateTime.now();
    ImportJob duplicateActiveJob =
        new ImportJob(
            UUID.randomUUID(),
            docId,
            testUser.id(),
            ImportJobStatus.PROCESSING,
            0,
            3,
            "storage/duplicate.epub",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            now,
            now,
            0);

    assertThatThrownBy(() -> importJobs.save(duplicateActiveJob))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("T31: Confirming expired or aborted upload throws appropriate exception")
  void rejectsExpiredOrAbortedUploadConfirmation() {
    LocalDateTime now = LocalDateTime.now();
    UUID uploadId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();

    // Expired
    DocumentUpload expiredUpload =
        new DocumentUpload(
            uploadId,
            docId,
            testUser.id(),
            "expired.epub",
            DocumentFormat.EPUB,
            "application/epub+zip",
            100L,
            null,
            "key/expired",
            DocumentUploadStatus.PENDING,
            now.minusMinutes(10),
            null,
            null,
            now.minusHours(1),
            now.minusMinutes(10),
            0);
    documentUploads.save(expiredUpload);

    assertThatThrownBy(
            () ->
                confirmUseCase.confirm(
                    new ConfirmVerifiedDocumentUploadCommand(uploadId, testUser.id(), null)))
        .isInstanceOf(UploadExpiredException.class);

    // Aborted
    UUID abortedUploadId = UUID.randomUUID();
    DocumentUpload abortedUpload =
        new DocumentUpload(
            abortedUploadId,
            UUID.randomUUID(),
            testUser.id(),
            "aborted.epub",
            DocumentFormat.EPUB,
            "application/epub+zip",
            100L,
            null,
            "key/aborted",
            DocumentUploadStatus.ABORTED,
            now.plusMinutes(20),
            null,
            null,
            now,
            now,
            0);
    documentUploads.save(abortedUpload);

    assertThatThrownBy(
            () ->
                confirmUseCase.confirm(
                    new ConfirmVerifiedDocumentUploadCommand(abortedUploadId, testUser.id(), null)))
        .isInstanceOf(UploadAbortedException.class);
  }

  @Test
  @DisplayName("T40: Confirm with wrong userId throws UploadNotFoundException")
  void rejectsConfirmationByUnauthorizedUser() {
    UUID uploadId = UUID.randomUUID();
    createPendingUpload(uploadId, UUID.randomUUID());

    assertThatThrownBy(
            () ->
                confirmUseCase.confirm(
                    new ConfirmVerifiedDocumentUploadCommand(uploadId, UUID.randomUUID(), null)))
        .isInstanceOf(UploadNotFoundException.class);
  }
}
