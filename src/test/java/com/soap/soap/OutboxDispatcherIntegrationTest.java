package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.DocumentImportQueueMessage;
import com.soap.soap.application.port.out.ImportJobRepositoryPort;
import com.soap.soap.application.port.out.ImportQueuePublisher;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.OutboxEventRepositoryPort;
import com.soap.soap.application.service.OutboxDeliveryFailureService;
import com.soap.soap.application.service.OutboxDispatcher;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportRequestedEvent;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.ImportJob;
import com.soap.soap.domain.model.ImportJobStatus;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.domain.model.OutboxEvent;
import com.soap.soap.domain.model.OutboxEventStatus;
import com.soap.soap.domain.model.WorkerClaim;
import com.soap.soap.infrastructure.persistence.mapper.OutboxEventSerializer;
import com.soap.soap.infrastructure.queue.DocumentImportOutboxProperties;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
class OutboxDispatcherIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @TestConfiguration
  static class TestConfig {
    @Bean
    @Primary
    TestQueuePublisher testQueuePublisher() {
      return new TestQueuePublisher();
    }
  }

  static class TestQueuePublisher implements ImportQueuePublisher {
    final List<DocumentImportQueueMessage> published =
        Collections.synchronizedList(new ArrayList<>());
    final AtomicBoolean shouldFail = new AtomicBoolean(false);
    RuntimeException failureException =
        new RuntimeException(new IOException("Simulated SQS connection timeout"));
    Runnable onPublishCallback;

    @Override
    public void publish(DocumentImportQueueMessage message) {
      if (onPublishCallback != null) {
        onPublishCallback.run();
      }
      if (shouldFail.get()) {
        throw failureException;
      }
      published.add(message);
    }

    void reset() {
      published.clear();
      shouldFail.set(false);
      failureException = new RuntimeException(new IOException("Simulated SQS connection timeout"));
      onPublishCallback = null;
    }
  }

  @Autowired private OutboxEventRepositoryPort outboxEvents;
  @Autowired private OutboxEventSerializer serializer;
  @Autowired private OutboxDeliveryFailureService deliveryFailureService;
  @Autowired private ImportJobRepositoryPort importJobs;
  @Autowired private ImportedDocumentRepositoryPort importedDocuments;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TestQueuePublisher testQueuePublisher;

  @BeforeEach
  void cleanUp() {
    jdbc.update("DELETE FROM outbox_events");
    jdbc.update("DELETE FROM import_jobs");
    jdbc.update("DELETE FROM imported_documents");
    jdbc.update("DELETE FROM users WHERE email LIKE 'test-%'");
    testQueuePublisher.reset();
  }

  private OutboxEvent createPendingEvent(int attemptCount, int maxAttempts) {
    return createPendingEvent(attemptCount, maxAttempts, UUID.randomUUID(), UUID.randomUUID());
  }

  private OutboxEvent createPendingEvent(
      int attemptCount, int maxAttempts, UUID eventId, UUID jobId) {
    LocalDateTime now = LocalDateTime.now();
    UUID docId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    DocumentImportRequestedEvent payload =
        new DocumentImportRequestedEvent(
            eventId, 1, jobId, docId, userId, DocumentFormat.EPUB, "s3/key.epub", "en", now);
    String json = serializer.serializeDocumentImportRequested(payload);

    return outboxEvents.save(
        new OutboxEvent(
            eventId,
            "IMPORT_JOB",
            jobId,
            "DOCUMENT_IMPORT_REQUESTED",
            json,
            OutboxEventStatus.PENDING,
            attemptCount,
            maxAttempts,
            null,
            null,
            null,
            null,
            null,
            now,
            now,
            0));
  }

  private OutboxDispatcher createDispatcher(
      String dispatcherId, int batchSize, Duration lockDuration) {
    DocumentImportOutboxProperties props =
        new DocumentImportOutboxProperties(
            true,
            batchSize,
            lockDuration,
            Duration.ofSeconds(30),
            dispatcherId,
            new DocumentImportOutboxProperties.ScheduleProperties(
                Duration.ofSeconds(2), Duration.ofSeconds(5)));
    return new OutboxDispatcher(
        outboxEvents, testQueuePublisher, serializer, props, deliveryFailureService, null);
  }

  @Test
  @DisplayName(
      "A & B: End-to-end dispatch: claims PENDING event, publishes to queue, and transitions to PUBLISHED")
  void endToEndSuccessfulDispatch() {
    OutboxEvent event = createPendingEvent(0, 5);
    OutboxDispatcher dispatcher = createDispatcher("disp-int-1", 10, Duration.ofMinutes(5));

    int dispatched = dispatcher.dispatchOnce();

    assertThat(dispatched).isEqualTo(1);
    assertThat(testQueuePublisher.published).hasSize(1);
    DocumentImportQueueMessage msg = testQueuePublisher.published.getFirst();
    assertThat(msg.schemaVersion()).isEqualTo(1);
    assertThat(msg.eventId()).isEqualTo(event.id());
    assertThat(msg.eventType()).isEqualTo("DOCUMENT_IMPORT_REQUESTED");
    assertThat(msg.importJobId()).isEqualTo(event.aggregateId());

    OutboxEvent inDb = outboxEvents.findById(event.id()).orElseThrow();
    assertThat(inDb.status()).isEqualTo(OutboxEventStatus.PUBLISHED);
    assertThat(inDb.publishedAt()).isNotNull();
    assertThat(inDb.lockedBy()).isNull();
    assertThat(inDb.lockedUntil()).isNull();
    assertThat(inDb.attemptCount()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "C: Transient SQS failure transitions event to PENDING with backoff retry and records last_error")
  void sqsFailureReschedulesRetryWithBackoff() {
    OutboxEvent event = createPendingEvent(0, 5);
    testQueuePublisher.shouldFail.set(true);

    OutboxDispatcher dispatcher = createDispatcher("disp-int-1", 10, Duration.ofMinutes(5));
    int dispatched = dispatcher.dispatchOnce();

    assertThat(dispatched).isEqualTo(0);
    assertThat(testQueuePublisher.published).isEmpty();

    OutboxEvent inDb = outboxEvents.findById(event.id()).orElseThrow();
    assertThat(inDb.status()).isEqualTo(OutboxEventStatus.PENDING);
    assertThat(inDb.attemptCount()).isEqualTo(1);
    assertThat(inDb.nextAttemptAt()).isAfter(LocalDateTime.now().plusSeconds(25));
    assertThat(inDb.lastError()).contains("Simulated SQS connection timeout");
    assertThat(inDb.lockedBy()).isNull();
    assertThat(inDb.lockedUntil()).isNull();
  }

  @Test
  @DisplayName("C: Final failure occurs when max attempts reached upon SQS failure")
  void maxAttemptsReachedTransitionsToFailed() {
    // 4 previous attempts, max 5 -> this claim will be attempt 5
    OutboxEvent event = createPendingEvent(4, 5);
    testQueuePublisher.shouldFail.set(true);

    OutboxDispatcher dispatcher = createDispatcher("disp-int-1", 10, Duration.ofMinutes(5));
    int dispatched = dispatcher.dispatchOnce();

    assertThat(dispatched).isEqualTo(0);

    OutboxEvent inDb = outboxEvents.findById(event.id()).orElseThrow();
    assertThat(inDb.status()).isEqualTo(OutboxEventStatus.FAILED);
    assertThat(inDb.attemptCount()).isEqualTo(5);
    assertThat(inDb.lastError()).contains("Simulated SQS connection timeout");
  }

  @Test
  @DisplayName(
      "Section 5: Strict Fencing - claim #1 token A expires, claim #2 token B, token A cannot markPublished or markFailed, token B succeeds")
  void mandatoryFencingVerification() {
    OutboxEvent event = createPendingEvent(0, 5);
    UUID eventId = event.id();

    // 1. Claim #1 with unique token A
    String tokenA = "disp-instance:token-A";
    List<OutboxEvent> claimed1 = outboxEvents.claimBatch(tokenA, 1, Duration.ofSeconds(1));
    assertThat(claimed1).hasSize(1);

    // 2. Force lease expiration in DB
    jdbc.update(
        "UPDATE outbox_events SET locked_until = ? WHERE id = ?",
        LocalDateTime.now().minusSeconds(10),
        eventId);

    // 3. Claim #2 with unique token B (could be same or different instance)
    String tokenB = "disp-instance:token-B";
    List<OutboxEvent> claimed2 = outboxEvents.claimBatch(tokenB, 1, Duration.ofMinutes(5));
    assertThat(claimed2).hasSize(1);

    // 4. Stale thread with token A attempts markPublished -> must return false (0 rows updated)
    boolean publishedOld = outboxEvents.markPublished(eventId, tokenA);
    assertThat(publishedOld).isFalse();

    // 5. Stale thread with token A attempts markFailedAttempt -> must return false (0 rows updated)
    boolean failedOld =
        outboxEvents.markFailedAttempt(eventId, tokenA, "stale error", Duration.ofSeconds(30));
    assertThat(failedOld).isFalse();

    // 6. Stale thread with token A attempts markFailedTerminal -> must return false (0 rows
    // updated)
    boolean terminalOld = outboxEvents.markFailedTerminal(eventId, tokenA, "stale terminal error");
    assertThat(terminalOld).isFalse();

    // 7. Active claim with token B marks published -> succeeds
    boolean publishedCurrent = outboxEvents.markPublished(eventId, tokenB);
    assertThat(publishedCurrent).isTrue();

    OutboxEvent finalEvent = outboxEvents.findById(eventId).orElseThrow();
    assertThat(finalEvent.status()).isEqualTo(OutboxEventStatus.PUBLISHED);
    assertThat(finalEvent.lockedBy()).isNull();
    assertThat(finalEvent.lockedUntil()).isNull();
  }

  @Test
  @DisplayName(
      "Section 6 (Test G): Crash window allows duplicate publish with same eventId and importJobId (AT-LEAST-ONCE)")
  void duplicatePublishOnCrashWindow() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    createPendingEvent(0, 5, eventId, jobId);

    // 1. Dispatcher 1 claims and publishes to SQS
    String token1 = "disp-crash-1:" + UUID.randomUUID();
    List<OutboxEvent> claimed1 = outboxEvents.claimBatch(token1, 1, Duration.ofSeconds(1));
    assertThat(claimed1).hasSize(1);
    testQueuePublisher.publish(DocumentImportQueueMessage.forImportJob(eventId, jobId));

    // Simulate crash before markPublished: lease expires in DB
    jdbc.update(
        "UPDATE outbox_events SET locked_until = ? WHERE id = ?",
        LocalDateTime.now().minusSeconds(10),
        eventId);

    // 2. Dispatcher 2 runs dispatchOnce(), reclaims E and publishes again
    OutboxDispatcher dispatcher2 = createDispatcher("disp-crash-2", 10, Duration.ofMinutes(5));
    int dispatched = dispatcher2.dispatchOnce();
    assertThat(dispatched).isEqualTo(1);

    // Verify SQS publisher received exactly 2 messages
    assertThat(testQueuePublisher.published).hasSize(2);
    DocumentImportQueueMessage msg1 = testQueuePublisher.published.get(0);
    DocumentImportQueueMessage msg2 = testQueuePublisher.published.get(1);

    assertThat(msg1.eventId()).isEqualTo(eventId);
    assertThat(msg2.eventId()).isEqualTo(eventId);
    assertThat(msg1.importJobId()).isEqualTo(jobId);
    assertThat(msg2.importJobId()).isEqualTo(jobId);

    // Verify DB state is now PUBLISHED
    OutboxEvent inDb = outboxEvents.findById(eventId).orElseThrow();
    assertThat(inDb.status()).isEqualTo(OutboxEventStatus.PUBLISHED);
  }

  @Test
  @DisplayName(
      "Section 8, 9, 10: Atomic terminal failure transitions outbox, import_job, and document to FAILED")
  void atomicTerminalFailureUpdatesOutboxJobAndDocument() {
    UUID docId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.now();

    // 0. Insert user
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, created_at) VALUES (?, 'Test User', ?, 'hash', NOW())",
        userId,
        "test-" + userId + "@example.com");

    // 1. Insert document in PROCESSING
    String validSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    jdbc.update(
        "INSERT INTO imported_documents (id, user_id, title, author, language, format, import_status, "
            + "source_asset_key, original_filename, source_sha256, chunking_version, source_storage_provider, created_at, updated_at) "
            + "VALUES (?, ?, 'Test Doc', 'Author', 'en', 'EPUB', 'PROCESSING', 's3/key', 'test.epub', ?, 5, 'FILESYSTEM', ?, ?)",
        docId,
        userId,
        validSha256,
        now,
        now);

    // 2. Insert import_job in PENDING
    jdbc.update(
        "INSERT INTO import_jobs (id, document_id, user_id, status, attempt_count, max_attempts, "
            + "source_asset_key, storage_provider, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, 'PENDING', 0, 5, 's3/key', 'FILESYSTEM', ?, ?, 0)",
        jobId,
        docId,
        userId,
        now,
        now);

    // 3. Insert outbox_event in PENDING with malformed poison pill JSON
    UUID eventId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status, "
            + "attempt_count, max_attempts, created_at, updated_at, version) "
            + "VALUES (?, 'IMPORT_JOB', ?, 'DOCUMENT_IMPORT_REQUESTED', ?::jsonb, 'PENDING', 0, 5, ?, ?, 0)",
        eventId,
        jobId,
        "{\"invalid\": \"payload\"}",
        now,
        now);

    // 4. Run dispatcher -> detects poison pill, handles terminal failure
    OutboxDispatcher dispatcher = createDispatcher("disp-term", 10, Duration.ofMinutes(5));
    int published = dispatcher.dispatchOnce();
    assertThat(published).isEqualTo(0);

    // 5. Verify outbox_events is FAILED
    OutboxEvent outbox = outboxEvents.findById(eventId).orElseThrow();
    assertThat(outbox.status()).isEqualTo(OutboxEventStatus.FAILED);
    assertThat(outbox.lastError()).contains("Malformed or unsupported");

    // 6. Verify import_jobs is FAILED
    String jobStatus =
        jdbc.queryForObject("SELECT status FROM import_jobs WHERE id = ?", String.class, jobId);
    String jobErrorCode =
        jdbc.queryForObject(
            "SELECT last_error_code FROM import_jobs WHERE id = ?", String.class, jobId);
    assertThat(jobStatus).isEqualTo("FAILED");
    assertThat(jobErrorCode).isEqualTo("IMPORT_QUEUE_PUBLISH_FAILED");

    // 7. Verify imported_documents is FAILED
    String docStatus =
        jdbc.queryForObject(
            "SELECT import_status FROM imported_documents WHERE id = ?", String.class, docId);
    String failureReason =
        jdbc.queryForObject(
            "SELECT failure_reason FROM imported_documents WHERE id = ?", String.class, docId);
    assertThat(docStatus).isEqualTo("FAILED");
    assertThat(failureReason).isEqualTo("IMPORT_FAILURE");
  }

  @Test
  @DisplayName("Section 14: SQS publish is executed without an active DB transaction")
  void sqsPublishWithoutActiveTransaction() {
    createPendingEvent(0, 5);
    AtomicBoolean txActive = new AtomicBoolean(true);
    testQueuePublisher.onPublishCallback =
        () -> txActive.set(TransactionSynchronizationManager.isActualTransactionActive());

    OutboxDispatcher dispatcher = createDispatcher("disp-tx", 10, Duration.ofMinutes(5));
    int count = dispatcher.dispatchOnce();

    assertThat(count).isEqualTo(1);
    assertThat(txActive.get()).isFalse();
  }

  @Test
  @DisplayName("F: Concurrent dispatchers partition events without overlap using SKIP LOCKED")
  void concurrentDispatchersPartitionEventsCleanly()
      throws InterruptedException, ExecutionException {
    int totalEvents = 12;
    for (int i = 0; i < totalEvents; i++) {
      createPendingEvent(0, 5);
    }

    OutboxDispatcher dispA = createDispatcher("disp-A", 6, Duration.ofMinutes(2));
    OutboxDispatcher dispB = createDispatcher("disp-B", 6, Duration.ofMinutes(2));

    ExecutorService executor = Executors.newFixedThreadPool(2);
    List<Callable<Integer>> tasks = List.of(dispA::dispatchOnce, dispB::dispatchOnce);

    List<Future<Integer>> futures = executor.invokeAll(tasks);
    executor.shutdown();

    int dispatchedA = futures.get(0).get();
    int dispatchedB = futures.get(1).get();

    assertThat(dispatchedA + dispatchedB).isEqualTo(totalEvents);
    assertThat(testQueuePublisher.published).hasSize(totalEvents);

    Set<UUID> publishedEventIds = new HashSet<>();
    for (DocumentImportQueueMessage msg : testQueuePublisher.published) {
      assertThat(publishedEventIds.add(msg.eventId())).isTrue();
    }
  }

  @Test
  @DisplayName("J: Empty outbox returns 0 and does not call publisher")
  void emptyOutboxReturnsZero() {
    OutboxDispatcher dispatcher = createDispatcher("disp-empty", 10, Duration.ofMinutes(5));
    int dispatched = dispatcher.dispatchOnce();

    assertThat(dispatched).isEqualTo(0);
    assertThat(testQueuePublisher.published).isEmpty();
  }

  @Test
  @DisplayName(
      "Section 5 (Race Condition): Outbox terminal failure does NOT downgrade job in PROCESSING or document")
  void outboxTerminalFailureDoesNotDowngradeJobInProcessingOrDocument() {
    UUID docId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.now();

    // 0. Insert user
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, created_at) VALUES (?, 'Test User', ?, 'hash', NOW())",
        userId,
        "test-" + userId + "@example.com");

    // 1. Insert document in PROCESSING
    String validSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    jdbc.update(
        "INSERT INTO imported_documents (id, user_id, title, author, language, format, import_status, "
            + "source_asset_key, original_filename, source_sha256, chunking_version, source_storage_provider, created_at, updated_at) "
            + "VALUES (?, ?, 'Race Doc', 'Author', 'en', 'EPUB', 'PROCESSING', 's3/key', 'test.epub', ?, 5, 'FILESYSTEM', ?, ?)",
        docId,
        userId,
        validSha256,
        now,
        now);

    // 2. Insert import_job in PENDING
    jdbc.update(
        "INSERT INTO import_jobs (id, document_id, user_id, status, attempt_count, max_attempts, "
            + "source_asset_key, storage_provider, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, 'PENDING', 0, 5, 's3/key', 'FILESYSTEM', ?, ?, 0)",
        jobId,
        docId,
        userId,
        now,
        now);

    // 3. Insert outbox_event in PENDING
    DocumentImportRequestedEvent payload =
        new DocumentImportRequestedEvent(
            eventId, 1, jobId, docId, userId, DocumentFormat.EPUB, "s3/key", "en", now);
    String json = serializer.serializeDocumentImportRequested(payload);
    jdbc.update(
        "INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status, "
            + "attempt_count, max_attempts, created_at, updated_at, version) "
            + "VALUES (?, 'IMPORT_JOB', ?, 'DOCUMENT_IMPORT_REQUESTED', ?::jsonb, 'PENDING', 0, 5, ?, ?, 0)",
        eventId,
        jobId,
        json,
        now,
        now);

    // 4. Claim outbox event with token B
    String claimToken = "disp-worker-race:" + UUID.randomUUID();
    List<OutboxEvent> claimed = outboxEvents.claimBatch(claimToken, 10, Duration.ofMinutes(5));
    assertThat(claimed).hasSize(1);
    assertThat(claimed.get(0).id()).isEqualTo(eventId);

    // 5. Simulate that an earlier delivery arrived at a Worker: Worker moves import_job PENDING ->
    // PROCESSING via real transition
    WorkerClaim workerClaim = importJobs.claim(jobId, "worker-pod-1", Duration.ofMinutes(10));
    assertThat(workerClaim.isAcquired()).isTrue();
    assertThat(workerClaim.job().orElseThrow().status()).isEqualTo(ImportJobStatus.PROCESSING);

    // 6. Second dispatcher attempt suffers terminal failure with valid lease
    boolean terminalResult =
        deliveryFailureService.handleTerminalFailure(
            eventId, claimToken, jobId, "Simulated permanent publish error");
    assertThat(terminalResult).isTrue();

    // 7. Verify outbox is FAILED
    OutboxEvent outboxInDb = outboxEvents.findById(eventId).orElseThrow();
    assertThat(outboxInDb.status()).isEqualTo(OutboxEventStatus.FAILED);
    assertThat(outboxInDb.lastError()).contains("Simulated permanent publish error");

    // 8. Verify import_job is STILL PROCESSING (NOT downgraded to FAILED)
    ImportJob jobInDb = importJobs.findById(jobId).orElseThrow();
    assertThat(jobInDb.status()).isEqualTo(ImportJobStatus.PROCESSING);
    assertThat(jobInDb.workerId()).isEqualTo("worker-pod-1");

    // 9. Verify imported_document is STILL PROCESSING (NOT marked FAILED)
    ImportedDocument docInDb = importedDocuments.findDocumentById(docId).orElseThrow();
    assertThat(docInDb.importStatus()).isEqualTo(DocumentImportStatus.PROCESSING);
    assertThat(docInDb.failureReason()).isNull();
  }

  @Test
  @DisplayName(
      "Section 6 (Variant): Outbox terminal failure does NOT downgrade already COMPLETED job or READY document")
  void outboxTerminalFailureDoesNotDowngradeAlreadyCompletedJobOrReadyDocument() {
    UUID docId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.now();

    // 0. Insert user
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, created_at) VALUES (?, 'Test User', ?, 'hash', NOW())",
        userId,
        "test-" + userId + "@example.com");

    // 1. Insert document in PROCESSING
    String validSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    jdbc.update(
        "INSERT INTO imported_documents (id, user_id, title, author, language, format, import_status, "
            + "source_asset_key, original_filename, source_sha256, chunking_version, source_storage_provider, created_at, updated_at) "
            + "VALUES (?, ?, 'Completed Doc', 'Author', 'en', 'EPUB', 'PROCESSING', 's3/key', 'test.epub', ?, 5, 'FILESYSTEM', ?, ?)",
        docId,
        userId,
        validSha256,
        now,
        now);

    // 2. Insert import_job in PENDING
    jdbc.update(
        "INSERT INTO import_jobs (id, document_id, user_id, status, attempt_count, max_attempts, "
            + "source_asset_key, storage_provider, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, 'PENDING', 0, 5, 's3/key', 'FILESYSTEM', ?, ?, 0)",
        jobId,
        docId,
        userId,
        now,
        now);

    // 3. Insert outbox_event in PENDING
    DocumentImportRequestedEvent payload =
        new DocumentImportRequestedEvent(
            eventId, 1, jobId, docId, userId, DocumentFormat.EPUB, "s3/key", "en", now);
    String json = serializer.serializeDocumentImportRequested(payload);
    jdbc.update(
        "INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status, "
            + "attempt_count, max_attempts, created_at, updated_at, version) "
            + "VALUES (?, 'IMPORT_JOB', ?, 'DOCUMENT_IMPORT_REQUESTED', ?::jsonb, 'PENDING', 0, 5, ?, ?, 0)",
        eventId,
        jobId,
        json,
        now,
        now);

    // 4. Claim outbox event with token B
    String claimToken = "disp-worker-completed:" + UUID.randomUUID();
    List<OutboxEvent> claimed = outboxEvents.claimBatch(claimToken, 10, Duration.ofMinutes(5));
    assertThat(claimed).hasSize(1);

    // 5. Worker claims and completes job
    WorkerClaim workerClaim = importJobs.claim(jobId, "worker-pod-1", Duration.ofMinutes(10));
    boolean completed = importJobs.complete(jobId, workerClaim.leaseToken(), "worker-pod-1");
    assertThat(completed).isTrue();

    // 6. Document is transitioned to READY
    jdbc.update("UPDATE imported_documents SET import_status = 'READY' WHERE id = ?", docId);

    // 7. Dispatcher experiences terminal failure on the outbox event
    boolean terminalResult =
        deliveryFailureService.handleTerminalFailure(
            eventId, claimToken, jobId, "Terminal outbox error after job completion");
    assertThat(terminalResult).isTrue();

    // 8. Verify outbox is FAILED
    OutboxEvent outboxInDb = outboxEvents.findById(eventId).orElseThrow();
    assertThat(outboxInDb.status()).isEqualTo(OutboxEventStatus.FAILED);

    // 9. Verify import_job is STILL COMPLETED (NOT downgraded to FAILED)
    ImportJob jobInDb = importJobs.findById(jobId).orElseThrow();
    assertThat(jobInDb.status()).isEqualTo(ImportJobStatus.COMPLETED);

    // 10. Verify imported_document is STILL READY (NOT downgraded to FAILED)
    ImportedDocument docInDb = importedDocuments.findDocumentById(docId).orElseThrow();
    assertThat(docInDb.importStatus()).isEqualTo(DocumentImportStatus.READY);
    assertThat(docInDb.failureReason()).isNull();
  }
}
