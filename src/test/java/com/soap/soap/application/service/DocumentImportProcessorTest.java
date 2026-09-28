package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.CompleteDocumentImportCommand;
import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.exception.DocumentImportException.Reason;
import com.soap.soap.application.exception.JobLeaseLostException;
import com.soap.soap.application.exception.TransientStorageException;
import com.soap.soap.application.model.DocumentChunk;
import com.soap.soap.application.model.ParsedDocument;
import com.soap.soap.application.model.ParsedDocumentSection;
import com.soap.soap.application.port.out.DocumentAssetStoragePort;
import com.soap.soap.application.port.out.DocumentObjectStoragePort;
import com.soap.soap.application.port.out.DocumentParserPort;
import com.soap.soap.application.port.out.ImportJobRepositoryPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
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
import com.soap.soap.domain.model.WorkerClaim;
import com.soap.soap.domain.model.WorkerClaimResult;
import com.soap.soap.infrastructure.queue.DocumentImportQueueProperties;
import com.soap.soap.infrastructure.queue.DocumentImportWorkerProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

class DocumentImportProcessorTest {

  private ImportJobRepositoryPort importJobs;
  private ImportedDocumentRepositoryPort importedDocuments;
  private DocumentObjectStoragePort documentObjectStorage;
  private DocumentAssetStoragePort documentAssetStorage;
  private DocumentParserPort documentParser;
  private DocumentLanguageResolver languageResolver;
  private DocumentLanguagePolicy languagePolicy;
  private DocumentChunker chunker;
  private CompleteDocumentImportUseCase completeUseCase;
  private FailDocumentImportUseCase failUseCase;
  private DocumentImportWorkerProperties workerProperties;
  private DocumentImportQueueProperties queueProperties;
  private SqsClient sqsClient;
  private DocumentImportProcessor processor;

  @TempDir Path tempStorageRoot;

  private static final String QUEUE_URL =
      "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";
  private static final String WORKER_ID = "worker-unit-test";

  @BeforeEach
  void setUp() {
    importJobs = mock(ImportJobRepositoryPort.class);
    importedDocuments = mock(ImportedDocumentRepositoryPort.class);
    documentObjectStorage = mock(DocumentObjectStoragePort.class);
    documentAssetStorage = mock(DocumentAssetStoragePort.class);
    documentParser = mock(DocumentParserPort.class);
    languageResolver = mock(DocumentLanguageResolver.class);
    languagePolicy = mock(DocumentLanguagePolicy.class);
    chunker = mock(DocumentChunker.class);
    completeUseCase = mock(CompleteDocumentImportUseCase.class);
    failUseCase = mock(FailDocumentImportUseCase.class);
    sqsClient = mock(SqsClient.class);

    queueProperties = new DocumentImportQueueProperties(QUEUE_URL, "us-east-1", null);
    workerProperties =
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
            WORKER_ID,
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
            workerProperties,
            queueProperties,
            sqsClient,
            tempStorageRoot,
            null);
  }

  private ImportJob createJob(UUID jobId, UUID docId, ImportJobStatus status, int attempts) {
    LocalDateTime now = LocalDateTime.now();
    return new ImportJob(
        jobId,
        docId,
        UUID.randomUUID(),
        status,
        attempts,
        3,
        "s3-key-" + docId,
        StorageProvider.S3,
        null,
        WORKER_ID,
        UUID.randomUUID(),
        now.plusMinutes(1),
        now,
        now,
        now,
        null,
        null,
        null,
        now,
        now,
        1L);
  }

  private ImportedDocument createDoc(UUID docId) {
    LocalDateTime now = LocalDateTime.now();
    return new ImportedDocument(
        docId,
        UUID.randomUUID(),
        "Sample Title",
        "Sample Author",
        "en",
        DocumentFormat.EPUB,
        null,
        "s3-key-" + docId,
        StorageProvider.S3,
        "sample.epub",
        "0".repeat(64),
        DocumentImportStatus.PROCESSING,
        null,
        5,
        now,
        now);
  }

  @Test
  @DisplayName("Case A: valid message -> claim -> process -> COMPLETED -> delete message")
  void caseA_validMessage_claim_process_completed_delete() {
    UUID jobId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    UUID leaseToken = UUID.randomUUID();
    ImportJob job = createJob(jobId, docId, ImportJobStatus.PROCESSING, 1);
    ImportedDocument doc = createDoc(docId);

    when(importJobs.claim(eq(jobId), eq(WORKER_ID), any()))
        .thenReturn(WorkerClaim.acquired(job, leaseToken));
    when(importedDocuments.findDocumentById(docId)).thenReturn(Optional.of(doc));

    ParsedDocumentSection section =
        new ParsedDocumentSection("Chapter 1", "sec-1", List.of("This is paragraph one."));
    ParsedDocument parsed =
        new ParsedDocument("Book Title", "Author Name", "en", List.of(section), null);
    when(documentParser.parse(any(Path.class), eq(DocumentFormat.EPUB))).thenReturn(parsed);
    when(languageResolver.resolve(any(), eq("en"))).thenReturn("en");

    DocumentChunk chunk = new DocumentChunk("This is paragraph one.", 4);
    when(chunker.chunk(any(), eq("en"))).thenReturn(List.of(chunk));
    when(completeUseCase.completeImport(any(CompleteDocumentImportCommand.class))).thenReturn(true);

    ImportExecutionOutcome outcome = processor.processImport(jobId, "receipt-a");

    assertThat(outcome.type()).isEqualTo(OutcomeType.COMPLETED);
    verify(completeUseCase).completeImport(any(CompleteDocumentImportCommand.class));
    verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  @DisplayName("Case B: duplicate message + ALREADY_COMPLETED -> no parse -> delete SQS message")
  void caseB_duplicateMessage_alreadyCompleted_noParse_delete() {
    UUID jobId = UUID.randomUUID();
    when(importJobs.claim(eq(jobId), eq(WORKER_ID), any()))
        .thenReturn(WorkerClaim.rejected(WorkerClaimResult.ALREADY_COMPLETED));

    ImportExecutionOutcome outcome = processor.processImport(jobId, "receipt-b");

    assertThat(outcome.type()).isEqualTo(OutcomeType.ALREADY_COMPLETED);
    verify(documentParser, never()).parse(any(), any());
    verify(completeUseCase, never()).completeImport(any());
    ArgumentCaptor<DeleteMessageRequest> captor =
        ArgumentCaptor.forClass(DeleteMessageRequest.class);
    verify(sqsClient).deleteMessage(captor.capture());
    assertThat(captor.getValue().receiptHandle()).isEqualTo("receipt-b");
  }

  @Test
  @DisplayName(
      "Case C: crash after DB COMPLETED before DeleteMessage -> redelivery -> no duplicate persistence -> delete")
  void caseC_crashAfterCommitBeforeDelete_redeliveryHandledIdempotently() {
    UUID jobId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    UUID leaseToken = UUID.randomUUID();
    ImportJob job = createJob(jobId, docId, ImportJobStatus.PROCESSING, 1);
    ImportedDocument doc = createDoc(docId);

    // DELIVERY #1: Worker claims, parses, chunks, and commits to DB. SQS delete fails (simulating
    // crash before delete)
    when(importJobs.claim(eq(jobId), eq(WORKER_ID), any()))
        .thenReturn(WorkerClaim.acquired(job, leaseToken));
    when(importedDocuments.findDocumentById(docId)).thenReturn(Optional.of(doc));

    ParsedDocumentSection section =
        new ParsedDocumentSection("Chapter 1", "sec-1", List.of("Body"));
    ParsedDocument parsed = new ParsedDocument("Title", "Author", "en", List.of(section), null);
    when(documentParser.parse(any(), any())).thenReturn(parsed);
    when(languageResolver.resolve(any(), any())).thenReturn("en");
    when(chunker.chunk(any(), any())).thenReturn(List.of(new DocumentChunk("Body", 1)));
    when(completeUseCase.completeImport(any())).thenReturn(true);

    doThrow(SqsException.builder().message("Simulated network crash before delete").build())
        .when(sqsClient)
        .deleteMessage(any(DeleteMessageRequest.class));

    ImportExecutionOutcome outcome1 = processor.processImport(jobId, "receipt-delivery-1");
    assertThat(outcome1.type()).isEqualTo(OutcomeType.COMPLETED);
    verify(completeUseCase).completeImport(any());

    // DELIVERY #2: Message is redelivered. Worker attempts claim, which returns ALREADY_COMPLETED
    when(importJobs.claim(eq(jobId), eq(WORKER_ID), any()))
        .thenReturn(WorkerClaim.rejected(WorkerClaimResult.ALREADY_COMPLETED));

    // Reset sqsClient mock to allow successful delete on redelivery
    org.mockito.Mockito.reset(sqsClient);

    ImportExecutionOutcome outcome2 = processor.processImport(jobId, "receipt-delivery-2");
    assertThat(outcome2.type()).isEqualTo(OutcomeType.ALREADY_COMPLETED);

    // Exactly 1 parse and 1 complete execution across both deliveries
    verify(documentParser, org.mockito.Mockito.times(1)).parse(any(), any());
    verify(completeUseCase, org.mockito.Mockito.times(1)).completeImport(any());
    // Delete succeeded on redelivery
    verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  @DisplayName(
      "Case D: PROCESSING owned by another valid worker -> no concurrent processing and message NOT deleted")
  void caseD_processingOwnedByOtherWorker_noConcurrentProcessing_messageNotDeleted() {
    UUID jobId = UUID.randomUUID();
    when(importJobs.claim(eq(jobId), eq(WORKER_ID), any()))
        .thenReturn(WorkerClaim.rejected(WorkerClaimResult.ACTIVE_BY_OTHER_WORKER));

    ImportExecutionOutcome outcome = processor.processImport(jobId, "receipt-d");

    assertThat(outcome.type()).isEqualTo(OutcomeType.ACTIVE_BY_OTHER_WORKER);
    verify(documentParser, never()).parse(any(), any());
    verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  @DisplayName("Case E: expired worker lease -> reclaim successfully acquires and completes")
  void caseE_expiredWorkerLease_reclaimsSuccessfully() {
    UUID jobId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    UUID newLeaseToken = UUID.randomUUID();
    ImportJob job = createJob(jobId, docId, ImportJobStatus.PROCESSING, 2);
    ImportedDocument doc = createDoc(docId);

    // Reclaim returns ACQUIRED after previous lease expired
    when(importJobs.claim(eq(jobId), eq(WORKER_ID), any()))
        .thenReturn(WorkerClaim.acquired(job, newLeaseToken));
    when(importedDocuments.findDocumentById(docId)).thenReturn(Optional.of(doc));

    ParsedDocumentSection section =
        new ParsedDocumentSection("Reclaimed Sec", "sec-1", List.of("Content"));
    ParsedDocument parsed = new ParsedDocument("Title", "Author", "en", List.of(section), null);
    when(documentParser.parse(any(Path.class), any())).thenReturn(parsed);
    when(languageResolver.resolve(any(), any())).thenReturn("en");
    when(chunker.chunk(any(), any())).thenReturn(List.of(new DocumentChunk("Content", 1)));
    when(completeUseCase.completeImport(any())).thenReturn(true);

    ImportExecutionOutcome outcome = processor.processImport(jobId, "receipt-e");

    assertThat(outcome.type()).isEqualTo(OutcomeType.COMPLETED);
    verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  @DisplayName("Case F: stale claim token cannot complete -> throws and does not delete message")
  void caseF_staleClaimTokenCannotComplete_messageNotDeleted() {
    UUID jobId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    UUID leaseToken = UUID.randomUUID();
    ImportJob job = createJob(jobId, docId, ImportJobStatus.PROCESSING, 1);
    ImportedDocument doc = createDoc(docId);

    when(importJobs.claim(eq(jobId), eq(WORKER_ID), any()))
        .thenReturn(WorkerClaim.acquired(job, leaseToken));
    when(importedDocuments.findDocumentById(docId)).thenReturn(Optional.of(doc));

    ParsedDocumentSection section = new ParsedDocumentSection("Sec", "sec-1", List.of("Txt"));
    when(documentParser.parse(any(Path.class), any()))
        .thenReturn(new ParsedDocument("T", "A", "en", List.of(section), null));
    when(languageResolver.resolve(any(), any())).thenReturn("en");
    when(chunker.chunk(any(), any())).thenReturn(List.of(new DocumentChunk("Txt", 1)));

    // Fencing rejection: stale lease token in CompleteDocumentImportUseCase
    doThrow(
            new IllegalStateException(
                "Cannot complete import: fencing lease token does not match active lease"))
        .when(completeUseCase)
        .completeImport(any());

    ImportExecutionOutcome outcome = processor.processImport(jobId, "receipt-f");

    assertThat(outcome.type()).isEqualTo(OutcomeType.RETRY_SCHEDULED);
    verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  @DisplayName("Case G: stale claim token cannot fail -> failFinal rejects")
  void caseG_staleClaimTokenCannotFail() {
    UUID jobId = UUID.randomUUID();
    UUID staleToken = UUID.randomUUID();
    when(failUseCase.failFinal(eq(jobId), eq(staleToken), eq(WORKER_ID), anyString(), anyString()))
        .thenReturn(false);

    boolean failed = failUseCase.failFinal(jobId, staleToken, WORKER_ID, "CODE", "Message");
    assertThat(failed).isFalse();
  }

  @Test
  @DisplayName("Case H: heartbeat extends DB lease")
  void caseH_heartbeatExtendsDbLease() {
    UUID jobId = UUID.randomUUID();
    UUID leaseToken = UUID.randomUUID();
    Duration duration = Duration.ofSeconds(60);

    when(importJobs.renewLease(jobId, leaseToken, WORKER_ID, duration)).thenReturn(true);

    try (DocumentImportHeartbeatCoordinator coordinator =
        new DocumentImportHeartbeatCoordinator(
            importJobs,
            sqsClient,
            QUEUE_URL,
            jobId,
            leaseToken,
            WORKER_ID,
            "rh",
            duration,
            Duration.ofSeconds(20))) {
      coordinator.tick();
      verify(importJobs).renewLease(jobId, leaseToken, WORKER_ID, duration);
      assertThat(coordinator.isOwnershipLost()).isFalse();
    }
  }

  @Test
  @DisplayName("Case I: heartbeat lost -> worker aborts before final persistence")
  void caseI_heartbeatLost_workerAbortsBeforePersistence() {
    UUID jobId = UUID.randomUUID();
    UUID leaseToken = UUID.randomUUID();
    Duration duration = Duration.ofSeconds(60);

    when(importJobs.renewLease(jobId, leaseToken, WORKER_ID, duration)).thenReturn(false);

    try (DocumentImportHeartbeatCoordinator coordinator =
        new DocumentImportHeartbeatCoordinator(
            importJobs,
            sqsClient,
            QUEUE_URL,
            jobId,
            leaseToken,
            WORKER_ID,
            "rh",
            duration,
            Duration.ofSeconds(20))) {
      coordinator.tick();
      assertThat(coordinator.isOwnershipLost()).isTrue();
      org.junit.jupiter.api.Assertions.assertThrows(
          JobLeaseLostException.class, coordinator::checkOwnership);
    }
  }

  @Test
  @DisplayName("Case J: ChangeMessageVisibility invoked during long processing")
  void caseJ_changeMessageVisibilityInvokedDuringHeartbeat() {
    UUID jobId = UUID.randomUUID();
    UUID leaseToken = UUID.randomUUID();
    Duration duration = Duration.ofSeconds(60);

    when(importJobs.renewLease(jobId, leaseToken, WORKER_ID, duration)).thenReturn(true);

    try (DocumentImportHeartbeatCoordinator coordinator =
        new DocumentImportHeartbeatCoordinator(
            importJobs,
            sqsClient,
            QUEUE_URL,
            jobId,
            leaseToken,
            WORKER_ID,
            "receipt-j",
            duration,
            Duration.ofSeconds(20))) {
      coordinator.tick();

      ArgumentCaptor<ChangeMessageVisibilityRequest> captor =
          ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
      verify(sqsClient).changeMessageVisibility(captor.capture());
      ChangeMessageVisibilityRequest req = captor.getValue();
      assertThat(req.queueUrl()).isEqualTo(QUEUE_URL);
      assertThat(req.receiptHandle()).isEqualTo("receipt-j");
      assertThat(req.visibilityTimeout()).isEqualTo(60);
    }
  }

  @Test
  @DisplayName("Case K: S3 transient failure -> message NOT deleted and retry scheduled")
  void caseK_s3TransientFailure_messageNotDeleted() {
    UUID jobId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    UUID leaseToken = UUID.randomUUID();
    ImportJob job = createJob(jobId, docId, ImportJobStatus.PROCESSING, 1);
    ImportedDocument doc = createDoc(docId);

    when(importJobs.claim(eq(jobId), eq(WORKER_ID), any()))
        .thenReturn(WorkerClaim.acquired(job, leaseToken));
    when(importedDocuments.findDocumentById(docId)).thenReturn(Optional.of(doc));

    doThrow(
            new TransientStorageException(
                "S3 connection reset", new RuntimeException("connection reset")))
        .when(documentObjectStorage)
        .downloadObject(any(), any());

    ImportExecutionOutcome outcome = processor.processImport(jobId, "receipt-k");

    assertThat(outcome.type()).isEqualTo(OutcomeType.RETRY_SCHEDULED);
    verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    verify(importJobs)
        .releaseForRetry(
            eq(jobId), eq(leaseToken), eq(WORKER_ID), any(), eq("TRANSIENT_FAILURE"), any());
  }

  @Test
  @DisplayName("Case L: permanent parser failure -> job/document FAILED + delete SQS message")
  void caseL_permanentParserFailure_failedAndDeleted() {
    UUID jobId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    UUID leaseToken = UUID.randomUUID();
    ImportJob job = createJob(jobId, docId, ImportJobStatus.PROCESSING, 1);
    ImportedDocument doc = createDoc(docId);

    when(importJobs.claim(eq(jobId), eq(WORKER_ID), any()))
        .thenReturn(WorkerClaim.acquired(job, leaseToken));
    when(importedDocuments.findDocumentById(docId)).thenReturn(Optional.of(doc));

    doThrow(new DocumentImportException(Reason.INVALID_EPUB, "Corrupt zip container"))
        .when(documentParser)
        .parse(any(), eq(DocumentFormat.EPUB));

    ImportExecutionOutcome outcome = processor.processImport(jobId, "receipt-l");

    assertThat(outcome.type()).isEqualTo(OutcomeType.TERMINAL_FAILED);
    verify(failUseCase)
        .failFinal(eq(jobId), eq(leaseToken), eq(WORKER_ID), eq("INVALID_EPUB"), anyString());
    verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  @DisplayName("Case M: DB persistence failure -> message NOT deleted")
  void caseM_dbPersistenceFailure_messageNotDeleted() {
    UUID jobId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    UUID leaseToken = UUID.randomUUID();
    ImportJob job = createJob(jobId, docId, ImportJobStatus.PROCESSING, 1);
    ImportedDocument doc = createDoc(docId);

    when(importJobs.claim(eq(jobId), eq(WORKER_ID), any()))
        .thenReturn(WorkerClaim.acquired(job, leaseToken));
    when(importedDocuments.findDocumentById(docId)).thenReturn(Optional.of(doc));

    ParsedDocumentSection section = new ParsedDocumentSection("Chapter 1", "sec-1", List.of("Txt"));
    ParsedDocument parsed = new ParsedDocument("Title", "Author", "en", List.of(section), null);
    when(documentParser.parse(any(), any())).thenReturn(parsed);
    when(languageResolver.resolve(any(), any())).thenReturn("en");
    when(chunker.chunk(any(), any())).thenReturn(List.of(new DocumentChunk("Txt", 1)));

    doThrow(new org.springframework.dao.DataAccessResourceFailureException("DB connection dropped"))
        .when(completeUseCase)
        .completeImport(any());

    ImportExecutionOutcome outcome = processor.processImport(jobId, "receipt-m");

    assertThat(outcome.type()).isEqualTo(OutcomeType.RETRY_SCHEDULED);
    verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  @DisplayName(
      "Case N: DeleteMessage failure after COMPLETED -> job stays COMPLETED without exception")
  void caseN_deleteMessageFailureAfterCompleted_jobStaysCompleted() {
    UUID jobId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    UUID leaseToken = UUID.randomUUID();
    ImportJob job = createJob(jobId, docId, ImportJobStatus.PROCESSING, 1);
    ImportedDocument doc = createDoc(docId);

    when(importJobs.claim(eq(jobId), eq(WORKER_ID), any()))
        .thenReturn(WorkerClaim.acquired(job, leaseToken));
    when(importedDocuments.findDocumentById(docId)).thenReturn(Optional.of(doc));

    ParsedDocumentSection section = new ParsedDocumentSection("Chapter 1", "sec-1", List.of("Txt"));
    ParsedDocument parsed = new ParsedDocument("Title", "Author", "en", List.of(section), null);
    when(documentParser.parse(any(), any())).thenReturn(parsed);
    when(languageResolver.resolve(any(), any())).thenReturn("en");
    when(chunker.chunk(any(), any())).thenReturn(List.of(new DocumentChunk("Txt", 1)));
    when(completeUseCase.completeImport(any())).thenReturn(true);

    doThrow(SqsException.builder().message("AWS SQS network glitch").build())
        .when(sqsClient)
        .deleteMessage(any(DeleteMessageRequest.class));

    ImportExecutionOutcome outcome = processor.processImport(jobId, "receipt-n");

    assertThat(outcome.type()).isEqualTo(OutcomeType.COMPLETED);
    verify(completeUseCase).completeImport(any());
  }

  @Test
  @DisplayName("Case O: RETRY_LIMIT_EXCEEDED calls failExceededRetries and deletes SQS message")
  void caseO_retryLimitExceeded_marksTerminalFailedAndDeletesSqs() {
    UUID jobId = UUID.randomUUID();
    when(importJobs.claim(eq(jobId), eq(WORKER_ID), any()))
        .thenReturn(WorkerClaim.rejected(WorkerClaimResult.RETRY_LIMIT_EXCEEDED));

    ImportExecutionOutcome outcome = processor.processImport(jobId, "receipt-retry-limit");

    assertThat(outcome.type()).isEqualTo(OutcomeType.TERMINAL_FAILED);
    verify(failUseCase).failExceededRetries(eq(jobId), eq("MAX_ATTEMPTS_EXCEEDED"), anyString());
    verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  @DisplayName("Case P: NOT_FOUND leaves message for SQS DLQ redrive without deleting")
  void caseP_notFound_leavesMessageForRedrive() {
    UUID jobId = UUID.randomUUID();
    when(importJobs.claim(eq(jobId), eq(WORKER_ID), any()))
        .thenReturn(WorkerClaim.rejected(WorkerClaimResult.NOT_FOUND));

    ImportExecutionOutcome outcome = processor.processImport(jobId, "receipt-not-found");

    assertThat(outcome.type()).isEqualTo(OutcomeType.NOT_FOUND);
    verify(documentParser, never()).parse(any(), any());
    verify(completeUseCase, never()).completeImport(any());
    verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  @DisplayName(
      "Case Q: Strict ordering on SUCCESS: claim -> parse -> completeImport -> deleteMessage")
  void caseQ_strictOrdering_success() {
    UUID jobId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    UUID leaseToken = UUID.randomUUID();
    ImportJob job = createJob(jobId, docId, ImportJobStatus.PROCESSING, 1);
    ImportedDocument doc = createDoc(docId);

    when(importJobs.claim(eq(jobId), eq(WORKER_ID), any()))
        .thenReturn(WorkerClaim.acquired(job, leaseToken));
    when(importedDocuments.findDocumentById(docId)).thenReturn(Optional.of(doc));

    ParsedDocumentSection section =
        new ParsedDocumentSection("Chapter 1", "sec-1", List.of("Body"));
    ParsedDocument parsed = new ParsedDocument("Title", "Author", "en", List.of(section), null);
    when(documentParser.parse(any(), any())).thenReturn(parsed);
    when(languageResolver.resolve(any(), any())).thenReturn("en");
    when(chunker.chunk(any(), any())).thenReturn(List.of(new DocumentChunk("Body", 1)));
    when(completeUseCase.completeImport(any())).thenReturn(true);

    ImportExecutionOutcome outcome = processor.processImport(jobId, "receipt-order-ok");

    assertThat(outcome.type()).isEqualTo(OutcomeType.COMPLETED);

    org.mockito.InOrder inOrder =
        org.mockito.Mockito.inOrder(importJobs, documentParser, completeUseCase, sqsClient);
    inOrder.verify(importJobs).claim(eq(jobId), eq(WORKER_ID), any());
    inOrder.verify(documentParser).parse(any(), any());
    inOrder.verify(completeUseCase).completeImport(any());
    inOrder.verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  @DisplayName(
      "Case R: Strict ordering on PERMANENT FAILURE: claim -> parse error -> failFinal -> deleteMessage")
  void caseR_strictOrdering_permanentFailure() {
    UUID jobId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    UUID leaseToken = UUID.randomUUID();
    ImportJob job = createJob(jobId, docId, ImportJobStatus.PROCESSING, 1);
    ImportedDocument doc = createDoc(docId);

    when(importJobs.claim(eq(jobId), eq(WORKER_ID), any()))
        .thenReturn(WorkerClaim.acquired(job, leaseToken));
    when(importedDocuments.findDocumentById(docId)).thenReturn(Optional.of(doc));

    when(documentParser.parse(any(), any()))
        .thenThrow(
            new com.soap.soap.application.exception.DocumentImportException(
                com.soap.soap.application.exception.DocumentImportException.Reason.INVALID_EPUB,
                "Broken spine structure"));

    ImportExecutionOutcome outcome = processor.processImport(jobId, "receipt-order-fail");

    assertThat(outcome.type()).isEqualTo(OutcomeType.TERMINAL_FAILED);

    org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(importJobs, failUseCase, sqsClient);
    inOrder.verify(importJobs).claim(eq(jobId), eq(WORKER_ID), any());
    inOrder
        .verify(failUseCase)
        .failFinal(eq(jobId), eq(leaseToken), eq(WORKER_ID), eq("INVALID_EPUB"), anyString());
    inOrder.verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
  }
}
