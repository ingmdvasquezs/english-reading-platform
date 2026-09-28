package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.exception.DocumentImportException.Reason;
import com.soap.soap.application.model.ParsedDocument;
import com.soap.soap.application.model.ParsedDocumentSection;
import com.soap.soap.application.port.out.DocumentAssetStoragePort;
import com.soap.soap.application.port.out.DocumentObjectStoragePort;
import com.soap.soap.application.port.out.DocumentParserPort;
import com.soap.soap.application.port.out.ImportJobRepositoryPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.service.DocumentImportProcessor.ImportExecutionOutcome;
import com.soap.soap.application.service.DocumentImportProcessor.OutcomeType;
import com.soap.soap.application.usecase.CompleteDocumentImportUseCase;
import com.soap.soap.application.usecase.FailDocumentImportUseCase;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.ImportJob;
import com.soap.soap.domain.model.ImportJobStatus;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.domain.model.StorageProvider;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.WorkerClaim;
import com.soap.soap.domain.model.WorkerClaimResult;
import com.soap.soap.infrastructure.queue.DocumentImportQueueProperties;
import com.soap.soap.infrastructure.queue.DocumentImportWorkerProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

@SpringBootTest(
    properties = {
      "security.jwt.secret=test-only-secret-with-at-least-32-bytes",
      "app.document-import.worker.auto-startup=false"
    })
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("local")
class DocumentImportWorkerIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private ImportJobRepositoryPort importJobs;
  @Autowired private ImportedDocumentRepositoryPort importedDocuments;
  @Autowired private UserRepositoryPort users;
  @Autowired private DocumentLanguageResolver languageResolver;
  @Autowired private DocumentLanguagePolicy languagePolicy;
  @Autowired private DocumentChunker chunker;
  @Autowired private CompleteDocumentImportUseCase completeUseCase;
  @Autowired private FailDocumentImportUseCase failUseCase;

  private DocumentObjectStoragePort documentObjectStorage;
  private DocumentAssetStoragePort documentAssetStorage;
  private DocumentParserPort documentParser;
  private SqsClient sqsClient;
  private DocumentImportProcessor processor;

  @TempDir Path tempStagingRoot;

  private User testUser;
  private ImportedDocument testDoc;
  private static final String QUEUE_URL =
      "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";

  @BeforeEach
  void setUp() {
    documentObjectStorage = mock(DocumentObjectStoragePort.class);
    documentAssetStorage = mock(DocumentAssetStoragePort.class);
    documentParser = mock(DocumentParserPort.class);
    sqsClient = mock(SqsClient.class);

    DocumentImportQueueProperties queueProps =
        new DocumentImportQueueProperties(QUEUE_URL, "us-east-1", null);
    DocumentImportWorkerProperties workerProps =
        new DocumentImportWorkerProperties(
            true,
            false,
            20,
            60,
            1,
            Duration.ofMillis(100),
            Duration.ofSeconds(1),
            Duration.ofSeconds(60),
            Duration.ofSeconds(20),
            "integration-worker-1",
            Duration.ofHours(24));

    processor =
        new DocumentImportProcessor(
            importJobs,
            importedDocuments,
            documentObjectStorage,
            documentAssetStorage,
            documentParser,
            languageResolver,
            languagePolicy,
            chunker,
            completeUseCase,
            failUseCase,
            workerProps,
            queueProps,
            sqsClient,
            tempStagingRoot,
            null);

    testUser =
        users.save(
            new User(
                null,
                "Worker User",
                "worker-test-" + UUID.randomUUID() + "@example.com",
                "password-hash",
                null));

    LocalDateTime now = LocalDateTime.now();
    testDoc =
        importedDocuments.saveDocument(
            new ImportedDocument(
                UUID.randomUUID(),
                testUser.id(),
                "Integration Test Book",
                null,
                "en",
                DocumentFormat.EPUB,
                null,
                "sources/test-book.epub",
                StorageProvider.S3,
                "test-book.epub",
                "a".repeat(64),
                DocumentImportStatus.PROCESSING,
                null,
                5,
                now,
                now));
  }

  @Test
  @DisplayName(
      "Full End-to-End: claim PENDING job -> parse -> Chunker V5 -> persist READY & COMPLETED -> delete SQS")
  void fullEndToEnd_happyPath() throws IOException {
    LocalDateTime now = LocalDateTime.now();
    UUID jobId = UUID.randomUUID();
    ImportJob job =
        importJobs.save(
            new ImportJob(
                jobId,
                testDoc.id(),
                testUser.id(),
                ImportJobStatus.PENDING,
                0,
                3,
                "sources/test-book.epub",
                StorageProvider.S3,
                "en",
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
                0));

    ParsedDocumentSection section =
        new ParsedDocumentSection(
            "Chapter 1: The Beginning",
            "ch1.xhtml",
            List.of(
                "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.",
                "However little known the feelings or views of such a man may be on his first entering a neighbourhood, this truth is so well fixed in the minds of the surrounding families, that he is considered the rightful property of some one or other of their daughters."));
    ParsedDocument parsed =
        new ParsedDocument("Pride and Prejudice", "Jane Austen", "en", List.of(section), null);

    when(documentParser.parse(any(Path.class), eq(DocumentFormat.EPUB))).thenReturn(parsed);

    // Execute processor
    ImportExecutionOutcome outcome = processor.processImport(jobId, "receipt-e2e-1");

    assertThat(outcome.type()).isEqualTo(OutcomeType.COMPLETED);

    // Verify DB state: Document is now READY
    ImportedDocument updatedDoc = importedDocuments.findDocumentById(testDoc.id()).orElseThrow();
    assertThat(updatedDoc.importStatus()).isEqualTo(DocumentImportStatus.READY);
    assertThat(updatedDoc.title()).isEqualTo("Pride and Prejudice");
    assertThat(updatedDoc.author()).isEqualTo("Jane Austen");
    assertThat(updatedDoc.language()).isEqualTo("en");

    // Verify DB state: Sections and units were atomically persisted
    List<com.soap.soap.domain.model.DocumentSection> sections =
        importedDocuments.findSections(testDoc.id());
    assertThat(sections).hasSize(1);
    assertThat(sections.get(0).title()).isEqualTo("Chapter 1: The Beginning");

    long unitCount = importedDocuments.countUnits(testDoc.id());
    assertThat(unitCount).isGreaterThan(0);

    // Verify DB state: ImportJob is now COMPLETED
    ImportJob updatedJob = importJobs.findById(jobId).orElseThrow();
    assertThat(updatedJob.status()).isEqualTo(ImportJobStatus.COMPLETED);
    assertThat(updatedJob.finishedAt()).isNotNull();

    // Verify SQS message deletion was called
    verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));

    // Verify duplicate delivery is an idempotent no-op that deletes SQS message
    ImportExecutionOutcome duplicateOutcome = processor.processImport(jobId, "receipt-duplicate");
    assertThat(duplicateOutcome.type()).isEqualTo(OutcomeType.ALREADY_COMPLETED);
  }

  @Test
  @DisplayName(
      "Permanent failure: invalid document format marks job and document FAILED in DB and deletes SQS message")
  void permanentFailure_marksFailedInDb_deletesSqsMessage() {
    LocalDateTime now = LocalDateTime.now();
    UUID jobId = UUID.randomUUID();
    ImportJob job =
        importJobs.save(
            new ImportJob(
                jobId,
                testDoc.id(),
                testUser.id(),
                ImportJobStatus.PENDING,
                0,
                3,
                "sources/corrupt.epub",
                StorageProvider.S3,
                "en",
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
                0));

    when(documentParser.parse(any(Path.class), eq(DocumentFormat.EPUB)))
        .thenThrow(new DocumentImportException(Reason.INVALID_EPUB, "Bad zip header"));

    ImportExecutionOutcome outcome = processor.processImport(jobId, "receipt-corrupt");

    assertThat(outcome.type()).isEqualTo(OutcomeType.TERMINAL_FAILED);

    // Verify DB state: Document is now FAILED
    ImportedDocument updatedDoc = importedDocuments.findDocumentById(testDoc.id()).orElseThrow();
    assertThat(updatedDoc.importStatus()).isEqualTo(DocumentImportStatus.FAILED);
    assertThat(updatedDoc.failureReason()).isEqualTo("INVALID_EPUB");

    // Verify DB state: Job is now FAILED
    ImportJob updatedJob = importJobs.findById(jobId).orElseThrow();
    assertThat(updatedJob.status()).isEqualTo(ImportJobStatus.FAILED);
    assertThat(updatedJob.lastErrorCode()).isEqualTo("INVALID_EPUB");

    // Verify SQS message deleted
    verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  @DisplayName(
      "Crash Window: DB COMMIT succeeds -> delete throws SQS exception -> redelivery detects ALREADY_COMPLETED and deletes without re-parsing")
  void crashWindow_commitSucceeds_deleteThrows_redeliveryHandledIdempotently() throws IOException {
    LocalDateTime now = LocalDateTime.now();
    UUID jobId = UUID.randomUUID();
    ImportJob job =
        importJobs.save(
            new ImportJob(
                jobId,
                testDoc.id(),
                testUser.id(),
                ImportJobStatus.PENDING,
                0,
                3,
                "sources/test-book.epub",
                StorageProvider.S3,
                "en",
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
                0));

    ParsedDocumentSection section =
        new ParsedDocumentSection(
            "Chapter 1", "ch1.xhtml", List.of("Line one of test", "Line two of test"));
    ParsedDocument parsed =
        new ParsedDocument("Book Title", "Author Name", "en", List.of(section), null);
    when(documentParser.parse(any(Path.class), eq(DocumentFormat.EPUB))).thenReturn(parsed);

    // DELIVERY #1: SQS delete fails (simulating network error or crash right before deletion)
    org.mockito.Mockito.doThrow(SqsException.builder().message("AWS SQS network glitch").build())
        .when(sqsClient)
        .deleteMessage(any(DeleteMessageRequest.class));

    ImportExecutionOutcome outcome1 = processor.processImport(jobId, "receipt-crash-window-1");
    assertThat(outcome1.type()).isEqualTo(OutcomeType.COMPLETED);

    // Verify DB committed state
    ImportJob jobAfterDelivery1 = importJobs.findById(jobId).orElseThrow();
    assertThat(jobAfterDelivery1.status()).isEqualTo(ImportJobStatus.COMPLETED);
    ImportedDocument docAfterDelivery1 =
        importedDocuments.findDocumentById(testDoc.id()).orElseThrow();
    assertThat(docAfterDelivery1.importStatus()).isEqualTo(DocumentImportStatus.READY);

    // DELIVERY #2: Redelivery of same message. SQS delete succeeds now.
    when(sqsClient.deleteMessage(any(DeleteMessageRequest.class)))
        .thenReturn(
            software.amazon.awssdk.services.sqs.model.DeleteMessageResponse.builder().build());

    ImportExecutionOutcome outcome2 = processor.processImport(jobId, "receipt-crash-window-2");
    assertThat(outcome2.type()).isEqualTo(OutcomeType.ALREADY_COMPLETED);

    // Exactly 1 parse invocation across both deliveries
    verify(documentParser, org.mockito.Mockito.times(1))
        .parse(any(Path.class), eq(DocumentFormat.EPUB));

    // Verify SQS message deletion succeeded on redelivery
    verify(sqsClient, org.mockito.Mockito.times(2)).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  @DisplayName("Lease Reclaim & Stale Token Fencing in PostgreSQL")
  void leaseReclaim_and_staleTokenFencing_realPostgres() {
    LocalDateTime now = LocalDateTime.now();
    UUID jobId = UUID.randomUUID();
    // Insert a job that was in PROCESSING but whose lease expired in the past
    ImportJob job =
        importJobs.save(
            new ImportJob(
                jobId,
                testDoc.id(),
                testUser.id(),
                ImportJobStatus.PROCESSING,
                1,
                3,
                "sources/test-book.epub",
                StorageProvider.S3,
                "en",
                "worker-alpha",
                UUID.randomUUID(),
                now.minusMinutes(5), // expired 5 minutes ago
                now.minusMinutes(5),
                now.minusMinutes(10),
                null,
                null,
                null,
                null,
                now.minusMinutes(10),
                now.minusMinutes(5),
                0));

    UUID staleTokenAlpha = job.leaseToken();

    // 1. Worker Beta reclaims the expired job
    WorkerClaim claimBeta = importJobs.claim(jobId, "worker-beta", Duration.ofSeconds(60));
    assertThat(claimBeta.result()).isEqualTo(WorkerClaimResult.ACQUIRED);
    UUID tokenBeta = claimBeta.leaseToken();
    assertThat(tokenBeta).isNotNull().isNotEqualTo(staleTokenAlpha);

    // 2. Worker Alpha (stale token) attempts complete -> BLOCKED in DB
    boolean alphaComplete = importJobs.complete(jobId, staleTokenAlpha, "worker-alpha");
    assertThat(alphaComplete).isFalse();

    // 3. Worker Alpha (stale token) attempts failFinal -> BLOCKED in DB
    boolean alphaFail =
        importJobs.failFinal(jobId, staleTokenAlpha, "worker-alpha", "ERR", "message");
    assertThat(alphaFail).isFalse();

    // 4. Worker Alpha (stale token) attempts renewLease -> BLOCKED in DB
    boolean alphaRenew =
        importJobs.renewLease(jobId, staleTokenAlpha, "worker-alpha", Duration.ofSeconds(60));
    assertThat(alphaRenew).isFalse();

    // Verify job in DB is STILL owned by Worker Beta in PROCESSING status
    ImportJob jobUnderBeta = importJobs.findById(jobId).orElseThrow();
    assertThat(jobUnderBeta.status()).isEqualTo(ImportJobStatus.PROCESSING);
    assertThat(jobUnderBeta.workerId()).isEqualTo("worker-beta");
    assertThat(jobUnderBeta.leaseToken()).isEqualTo(tokenBeta);

    // 5. Worker Beta (valid token) completes job -> SUCCESS in DB
    boolean betaComplete = importJobs.complete(jobId, tokenBeta, "worker-beta");
    assertThat(betaComplete).isTrue();

    ImportJob finalJob = importJobs.findById(jobId).orElseThrow();
    assertThat(finalJob.status()).isEqualTo(ImportJobStatus.COMPLETED);
  }

  @Test
  @DisplayName("Heartbeat DB Lease: renewLease extends locked_until in PostgreSQL")
  void heartbeatDbLease_extendsLockedUntilInPostgres() {
    LocalDateTime now = LocalDateTime.now();
    UUID jobId = UUID.randomUUID();
    ImportJob job =
        importJobs.save(
            new ImportJob(
                jobId,
                testDoc.id(),
                testUser.id(),
                ImportJobStatus.PENDING,
                0,
                3,
                "sources/test-book.epub",
                StorageProvider.S3,
                "en",
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
                0));

    // Claim with 10 second lease
    WorkerClaim claim = importJobs.claim(jobId, "worker-hb", Duration.ofSeconds(10));
    assertThat(claim.result()).isEqualTo(WorkerClaimResult.ACQUIRED);
    UUID leaseToken = claim.leaseToken();

    ImportJob claimedJob = importJobs.findById(jobId).orElseThrow();
    LocalDateTime initialLeaseUntil = claimedJob.leaseUntil();
    assertThat(initialLeaseUntil).isNotNull();

    // Renew lease by 300 seconds
    boolean renewed =
        importJobs.renewLease(jobId, leaseToken, "worker-hb", Duration.ofSeconds(300));
    assertThat(renewed).isTrue();

    ImportJob renewedJob = importJobs.findById(jobId).orElseThrow();
    assertThat(renewedJob.leaseUntil()).isAfter(initialLeaseUntil);
    assertThat(renewedJob.leaseUntil()).isAfter(LocalDateTime.now().plusSeconds(250));
  }

  @Test
  @DisplayName(
      "RETRY_LIMIT_EXCEEDED: leaves both ImportJob and ImportedDocument in FAILED terminal state before SQS delete")
  void retryLimitExceeded_terminalStateCoherence() {
    LocalDateTime now = LocalDateTime.now();
    UUID jobId = UUID.randomUUID();
    // Job with attemptCount == maxAttempts (3/3) with expired lease
    importJobs.save(
        new ImportJob(
            jobId,
            testDoc.id(),
            testUser.id(),
            ImportJobStatus.PROCESSING,
            3,
            3,
            "sources/exhausted.epub",
            StorageProvider.S3,
            "en",
            "old-worker",
            UUID.randomUUID(),
            now.minusMinutes(1),
            now.minusMinutes(1),
            now.minusMinutes(5),
            null,
            null,
            null,
            null,
            now.minusMinutes(5),
            now.minusMinutes(1),
            0));

    ImportExecutionOutcome outcome = processor.processImport(jobId, "receipt-retry-exceeded");
    assertThat(outcome.type()).isEqualTo(OutcomeType.TERMINAL_FAILED);

    // Verify terminal coherent state in PostgreSQL:
    ImportJob updatedJob = importJobs.findById(jobId).orElseThrow();
    assertThat(updatedJob.status()).isEqualTo(ImportJobStatus.FAILED);
    assertThat(updatedJob.lastErrorCode()).isEqualTo("MAX_ATTEMPTS_EXCEEDED");

    ImportedDocument updatedDoc = importedDocuments.findDocumentById(testDoc.id()).orElseThrow();
    assertThat(updatedDoc.importStatus()).isEqualTo(DocumentImportStatus.FAILED);
    assertThat(updatedDoc.failureReason()).isEqualTo("IMPORT_FAILURE");

    // Verify SQS message was deleted
    verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
  }
}
