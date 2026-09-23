package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.port.out.OutboxEventRepositoryPort;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportRequestedEvent;
import com.soap.soap.domain.model.OutboxEvent;
import com.soap.soap.domain.model.OutboxEventStatus;
import com.soap.soap.infrastructure.persistence.mapper.OutboxEventSerializer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
class OutboxEventClaimIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private OutboxEventRepositoryPort outboxEvents;
  @Autowired private OutboxEventSerializer serializer;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void cleanUp() {
    jdbc.update("DELETE FROM outbox_events");
  }

  private OutboxEvent createEvent(OutboxEventStatus status, int attemptCount, int maxAttempts) {
    LocalDateTime now = LocalDateTime.now();
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    DocumentImportRequestedEvent payload =
        new DocumentImportRequestedEvent(
            eventId, 1, jobId, docId, userId, DocumentFormat.EPUB, "s3/key.epub", "es", now);
    String json = serializer.serializeDocumentImportRequested(payload);

    return outboxEvents.save(
        new OutboxEvent(
            eventId,
            "IMPORT_JOB",
            jobId,
            "DOCUMENT_IMPORT_REQUESTED",
            json,
            status,
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

  @Test
  @DisplayName("T19: claimBatch acquires PENDING events and transitions to SENDING with lock")
  void claimBatchAcquiresPendingEvents() {
    createEvent(OutboxEventStatus.PENDING, 0, 5);
    createEvent(OutboxEventStatus.PENDING, 0, 5);

    List<OutboxEvent> claimed = outboxEvents.claimBatch("dispatcher-1", 10, Duration.ofMinutes(5));

    assertThat(claimed).hasSize(2);
    for (var event : claimed) {
      assertThat(event.status()).isEqualTo(OutboxEventStatus.SENDING);
      assertThat(event.lockedBy()).isEqualTo("dispatcher-1");
      assertThat(event.lockedUntil()).isNotNull();
      assertThat(event.attemptCount()).isEqualTo(1);
    }
  }

  @Test
  @DisplayName("T20: Concurrent dispatchers partition events without overlap using SKIP LOCKED")
  void concurrentDispatchersDoNotOverlap() throws InterruptedException, ExecutionException {
    int totalEvents = 10;
    for (int i = 0; i < totalEvents; i++) {
      createEvent(OutboxEventStatus.PENDING, 0, 5);
    }

    int threadCount = 2;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    List<Callable<List<OutboxEvent>>> tasks =
        List.of(
            () -> outboxEvents.claimBatch("disp-A", 5, Duration.ofMinutes(2)),
            () -> outboxEvents.claimBatch("disp-B", 5, Duration.ofMinutes(2)));

    List<Future<List<OutboxEvent>>> futures = executor.invokeAll(tasks);
    executor.shutdown();

    List<OutboxEvent> batchA = futures.get(0).get();
    List<OutboxEvent> batchB = futures.get(1).get();

    Set<UUID> idsA = new HashSet<>(batchA.stream().map(OutboxEvent::id).toList());
    Set<UUID> idsB = new HashSet<>(batchB.stream().map(OutboxEvent::id).toList());

    // Disjoint sets: no duplicate claims
    Set<UUID> intersection = new HashSet<>(idsA);
    intersection.retainAll(idsB);
    assertThat(intersection).isEmpty();
    assertThat(idsA.size() + idsB.size()).isEqualTo(totalEvents);
  }

  @Test
  @DisplayName("T21: Locked events with active lease are not claimed by other dispatchers")
  void activeLeasePreventsDoubleClaim() {
    createEvent(OutboxEventStatus.PENDING, 0, 5);
    List<OutboxEvent> batch1 = outboxEvents.claimBatch("disp-1", 10, Duration.ofMinutes(5));
    assertThat(batch1).hasSize(1);

    List<OutboxEvent> batch2 = outboxEvents.claimBatch("disp-2", 10, Duration.ofMinutes(5));
    assertThat(batch2).isEmpty();
  }

  @Test
  @DisplayName("T22: Expired locked events are reclaimed after lock timeout")
  void expiredLockCanBeReclaimed() {
    createEvent(OutboxEventStatus.PENDING, 0, 5);
    List<OutboxEvent> batch1 = outboxEvents.claimBatch("disp-1", 10, Duration.ofSeconds(1));
    assertThat(batch1).hasSize(1);
    UUID eventId = batch1.getFirst().id();

    // Expire lock in database
    jdbc.update(
        "UPDATE outbox_events SET locked_until = ? WHERE id = ?",
        LocalDateTime.now().minusSeconds(10),
        eventId);

    List<OutboxEvent> batch2 = outboxEvents.claimBatch("disp-2", 10, Duration.ofMinutes(5));
    assertThat(batch2).hasSize(1);
    assertThat(batch2.getFirst().id()).isEqualTo(eventId);
    assertThat(batch2.getFirst().lockedBy()).isEqualTo("disp-2");
    assertThat(batch2.getFirst().attemptCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("T23: markPublished marks event PUBLISHED, sets published_at and clears lock")
  void markPublishedSucceeds() {
    createEvent(OutboxEventStatus.PENDING, 0, 5);
    List<OutboxEvent> batch = outboxEvents.claimBatch("disp-1", 1, Duration.ofMinutes(5));
    UUID eventId = batch.getFirst().id();

    boolean published = outboxEvents.markPublished(eventId, "disp-1");
    assertThat(published).isTrue();

    OutboxEvent updated = outboxEvents.findById(eventId).orElseThrow();
    assertThat(updated.status()).isEqualTo(OutboxEventStatus.PUBLISHED);
    assertThat(updated.publishedAt()).isNotNull();
    assertThat(updated.lockedBy()).isNull();
    assertThat(updated.lockedUntil()).isNull();
  }

  @Test
  @DisplayName(
      "T24 & T37: markFailedAttempt reschedules PENDING with backoff and skips before expiry")
  void markFailedAttemptWithBackoff() {
    createEvent(OutboxEventStatus.PENDING, 0, 5);
    List<OutboxEvent> batch = outboxEvents.claimBatch("disp-1", 1, Duration.ofMinutes(5));
    UUID eventId = batch.getFirst().id();

    boolean retried =
        outboxEvents.markFailedAttempt(eventId, "disp-1", "SQS timeout", Duration.ofMinutes(10));
    assertThat(retried).isTrue();

    OutboxEvent updated = outboxEvents.findById(eventId).orElseThrow();
    assertThat(updated.status()).isEqualTo(OutboxEventStatus.PENDING);
    assertThat(updated.lockedBy()).isNull();
    assertThat(updated.nextAttemptAt()).isAfter(LocalDateTime.now().plusMinutes(9));
    assertThat(updated.lastError()).isEqualTo("SQS timeout");

    // T37: Cannot claim while next_attempt_at is in future
    List<OutboxEvent> immediate = outboxEvents.claimBatch("disp-2", 10, Duration.ofMinutes(5));
    assertThat(immediate).isEmpty();

    // Fast-forward next_attempt_at into past
    jdbc.update(
        "UPDATE outbox_events SET next_attempt_at = ? WHERE id = ?",
        LocalDateTime.now().minusSeconds(1),
        eventId);

    List<OutboxEvent> afterExpiry = outboxEvents.claimBatch("disp-2", 10, Duration.ofMinutes(5));
    assertThat(afterExpiry).hasSize(1);
    assertThat(afterExpiry.getFirst().id()).isEqualTo(eventId);
  }

  @Test
  @DisplayName("T38: markFailedAttempt marks FAILED when max_attempts reached")
  void markFailedFinalWhenAttemptsExceeded() {
    createEvent(OutboxEventStatus.PENDING, 4, 5); // 4 attempts already
    List<OutboxEvent> batch =
        outboxEvents.claimBatch("disp-1", 1, Duration.ofMinutes(5)); // now 5 attempts
    UUID eventId = batch.getFirst().id();

    boolean failed =
        outboxEvents.markFailedAttempt(
            eventId, "disp-1", "Max retries reached: fatal SQS rejection", Duration.ofMinutes(5));
    assertThat(failed).isTrue();

    OutboxEvent updated = outboxEvents.findById(eventId).orElseThrow();
    assertThat(updated.status()).isEqualTo(OutboxEventStatus.FAILED);
    assertThat(updated.lastError()).contains("Max retries reached");
  }

  @Test
  @DisplayName("T39: Event payload serialization and deserialization preserves all fields")
  void payloadRoundTripSerialization() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    UUID docId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.now();

    DocumentImportRequestedEvent original =
        new DocumentImportRequestedEvent(
            eventId, 1, jobId, docId, userId, DocumentFormat.PDF, "storage/path.pdf", "de", now);

    String json = serializer.serializeDocumentImportRequested(original);
    assertThat(json).contains("storage/path.pdf", "PDF");

    DocumentImportRequestedEvent deserialized = serializer.deserializeDocumentImportRequested(json);
    assertThat(deserialized.eventId()).isEqualTo(original.eventId());
    assertThat(deserialized.eventVersion()).isEqualTo(1);
    assertThat(deserialized.jobId()).isEqualTo(original.jobId());
    assertThat(deserialized.documentId()).isEqualTo(original.documentId());
    assertThat(deserialized.userId()).isEqualTo(original.userId());
    assertThat(deserialized.format()).isEqualTo(DocumentFormat.PDF);
    assertThat(deserialized.sourceAssetKey()).isEqualTo(original.sourceAssetKey());
    assertThat(deserialized.languageOverride()).isEqualTo(original.languageOverride());
  }

  @Test
  @DisplayName(
      "T23_C: Stale dispatcher with expired lease cannot mark event as published after reclaim")
  void staleDispatcherCannotMarkPublishedAfterReclaim() {
    createEvent(OutboxEventStatus.PENDING, 0, 5);
    List<OutboxEvent> batch1 = outboxEvents.claimBatch("disp-1", 1, Duration.ofSeconds(1));
    assertThat(batch1).hasSize(1);
    UUID eventId = batch1.getFirst().id();

    // Expire lock in database
    jdbc.update(
        "UPDATE outbox_events SET locked_until = ? WHERE id = ?",
        LocalDateTime.now().minusSeconds(10),
        eventId);

    // Dispatcher 2 reclaims event
    List<OutboxEvent> batch2 = outboxEvents.claimBatch("disp-2", 1, Duration.ofMinutes(5));
    assertThat(batch2).hasSize(1);
    assertThat(batch2.getFirst().id()).isEqualTo(eventId);
    assertThat(batch2.getFirst().lockedBy()).isEqualTo("disp-2");

    // Stale Dispatcher 1 attempts markPublished
    boolean publishedByStale = outboxEvents.markPublished(eventId, "disp-1");
    assertThat(publishedByStale).isFalse();

    // VERIFY POST-FAILURE INVARIANTS:
    OutboxEvent current = outboxEvents.findById(eventId).orElseThrow();
    assertThat(current.status()).isEqualTo(OutboxEventStatus.SENDING);
    assertThat(current.lockedBy()).isEqualTo("disp-2");
    assertThat(current.publishedAt()).isNull();
  }

  @Test
  @DisplayName(
      "T24_D: Expired dispatcher cannot markFailedAttempt even before any other dispatcher reclaims")
  void expiredDispatcherCannotMarkFailedAttemptWithoutReclaim() {
    createEvent(OutboxEventStatus.PENDING, 0, 5);
    List<OutboxEvent> batch1 = outboxEvents.claimBatch("disp-1", 1, Duration.ofSeconds(1));
    assertThat(batch1).hasSize(1);
    UUID eventId = batch1.getFirst().id();
    OutboxEvent claimed = batch1.getFirst();
    int originalAttemptCount = claimed.attemptCount();
    LocalDateTime originalNextAttempt = claimed.nextAttemptAt();

    // Expire lock in database - NO OTHER DISPATCHER HAS RECLAIMED YET
    jdbc.update(
        "UPDATE outbox_events SET locked_until = ? WHERE id = ?",
        LocalDateTime.now().minusSeconds(10),
        eventId);

    // Stale Dispatcher 1 attempts markFailedAttempt after its lease expired
    boolean failedAttemptApplied =
        outboxEvents.markFailedAttempt(
            eventId, "disp-1", "SQS timeout error", Duration.ofMinutes(15));
    assertThat(failedAttemptApplied).isFalse();

    // VERIFY POST-FAILURE INVARIANTS:
    OutboxEvent current = outboxEvents.findById(eventId).orElseThrow();
    assertThat(current.status()).isEqualTo(OutboxEventStatus.SENDING);
    assertThat(current.lockedBy()).isEqualTo("disp-1");
    assertThat(current.lockedUntil()).isBefore(LocalDateTime.now());
    assertThat(current.attemptCount()).isEqualTo(originalAttemptCount);
    assertThat(current.nextAttemptAt()).isEqualTo(originalNextAttempt);

    // Afterward, Dispatcher 2 can reclaim normally
    List<OutboxEvent> batch2 = outboxEvents.claimBatch("disp-2", 1, Duration.ofMinutes(5));
    assertThat(batch2).hasSize(1);
    assertThat(batch2.getFirst().id()).isEqualTo(eventId);
    assertThat(batch2.getFirst().lockedBy()).isEqualTo("disp-2");
    assertThat(batch2.getFirst().attemptCount()).isEqualTo(originalAttemptCount + 1);
  }
}
