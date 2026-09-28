package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.model.DocumentImportQueueMessage;
import com.soap.soap.application.port.out.ImportQueuePublisher;
import com.soap.soap.application.port.out.OutboxEventRepositoryPort;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportRequestedEvent;
import com.soap.soap.domain.model.OutboxEvent;
import com.soap.soap.domain.model.OutboxEventStatus;
import com.soap.soap.infrastructure.persistence.mapper.OutboxEventSerializer;
import com.soap.soap.infrastructure.queue.DocumentImportOutboxProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.SqsException;

class OutboxDispatcherTest {

  private final OutboxEventRepositoryPort outboxEvents = mock(OutboxEventRepositoryPort.class);
  private final ImportQueuePublisher queuePublisher = mock(ImportQueuePublisher.class);
  private final OutboxEventSerializer serializer = new OutboxEventSerializer();
  private final OutboxDeliveryFailureService deliveryFailureService =
      mock(OutboxDeliveryFailureService.class);
  private final MeterRegistry meterRegistry = mock(MeterRegistry.class);
  private final Counter counter = mock(Counter.class);

  private final DocumentImportOutboxProperties properties =
      new DocumentImportOutboxProperties(
          true,
          10,
          Duration.ofMinutes(5),
          Duration.ofSeconds(30),
          "test-dispatcher",
          new DocumentImportOutboxProperties.ScheduleProperties(
              Duration.ofSeconds(2), Duration.ofSeconds(5)));

  private OutboxDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    when(meterRegistry.counter(anyString())).thenReturn(counter);
    dispatcher =
        new OutboxDispatcher(
            outboxEvents,
            queuePublisher,
            serializer,
            properties,
            deliveryFailureService,
            meterRegistry);
  }

  private OutboxEvent createSampleEvent(
      UUID eventId, UUID jobId, String eventType, String jsonPayload) {
    return createSampleEvent(eventId, jobId, eventType, jsonPayload, 1, 5);
  }

  private OutboxEvent createSampleEvent(
      UUID eventId,
      UUID jobId,
      String eventType,
      String jsonPayload,
      int attempt,
      int maxAttempts) {
    LocalDateTime now = LocalDateTime.now();
    return new OutboxEvent(
        eventId,
        "IMPORT_JOB",
        jobId,
        eventType,
        jsonPayload,
        OutboxEventStatus.SENDING,
        attempt,
        maxAttempts,
        null,
        "test-dispatcher",
        now.plusMinutes(5),
        null,
        null,
        now,
        now,
        0);
  }

  private String createValidPayload(UUID eventId, UUID jobId) {
    return createValidPayload(eventId, jobId, 1);
  }

  private String createValidPayload(UUID eventId, UUID jobId, int version) {
    DocumentImportRequestedEvent event =
        new DocumentImportRequestedEvent(
            eventId,
            version,
            jobId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            DocumentFormat.EPUB,
            "s3/key.epub",
            "en",
            LocalDateTime.now());
    return serializer.serializeDocumentImportRequested(event);
  }

  @Test
  @DisplayName(
      "A & B: Outbox event successfully claimed with unique token and published, markPublished called only after SQS success")
  void successfulClaimAndPublishFlow() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    OutboxEvent event =
        createSampleEvent(
            eventId, jobId, "DOCUMENT_IMPORT_REQUESTED", createValidPayload(eventId, jobId));

    when(outboxEvents.claimBatch(startsWith("test-dispatcher:"), eq(10), eq(Duration.ofMinutes(5))))
        .thenReturn(List.of(event));
    when(outboxEvents.markPublished(eq(eventId), startsWith("test-dispatcher:"))).thenReturn(true);

    int published = dispatcher.dispatchOnce();

    assertThat(published).isEqualTo(1);

    InOrder inOrder = inOrder(outboxEvents, queuePublisher);
    inOrder
        .verify(outboxEvents)
        .claimBatch(startsWith("test-dispatcher:"), eq(10), eq(Duration.ofMinutes(5)));
    inOrder.verify(queuePublisher).publish(any(DocumentImportQueueMessage.class));
    inOrder.verify(outboxEvents).markPublished(eq(eventId), startsWith("test-dispatcher:"));

    verify(outboxEvents, never()).markFailedAttempt(any(), any(), any(), any());
    verify(deliveryFailureService, never()).handleTerminalFailure(any(), any(), any(), any());
    verify(meterRegistry).counter("document.import.outbox.claimed");
    verify(meterRegistry).counter("document.import.outbox.published");
  }

  @Test
  @DisplayName(
      "C: Transient SQS failure leads to durable failure/retry state and does NOT call markPublished")
  void transientSqsFailureTriggersMarkFailedAttempt() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    OutboxEvent event =
        createSampleEvent(
            eventId, jobId, "DOCUMENT_IMPORT_REQUESTED", createValidPayload(eventId, jobId), 1, 5);

    when(outboxEvents.claimBatch(anyString(), anyInt(), any())).thenReturn(List.of(event));
    org.mockito.Mockito.doThrow(new RuntimeException(new IOException("Connection reset by peer")))
        .when(queuePublisher)
        .publish(any());
    when(outboxEvents.markFailedAttempt(
            eq(eventId), anyString(), anyString(), eq(Duration.ofSeconds(30))))
        .thenReturn(true);

    int published = dispatcher.dispatchOnce();

    assertThat(published).isEqualTo(0);
    verify(queuePublisher).publish(any());
    verify(outboxEvents, never()).markPublished(any(), any());
    verify(deliveryFailureService, never()).handleTerminalFailure(any(), any(), any(), any());
    verify(outboxEvents)
        .markFailedAttempt(
            eq(eventId),
            startsWith("test-dispatcher:"),
            contains("Connection reset"),
            eq(Duration.ofSeconds(30)));
  }

  @Test
  @DisplayName(
      "D: Expired/stale lease after SQS publish is handled gracefully without failure overwrite")
  void staleLeaseAfterPublishHandledSafely() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    OutboxEvent event =
        createSampleEvent(
            eventId, jobId, "DOCUMENT_IMPORT_REQUESTED", createValidPayload(eventId, jobId));

    when(outboxEvents.claimBatch(anyString(), anyInt(), any())).thenReturn(List.of(event));
    when(outboxEvents.markPublished(eq(eventId), anyString())).thenReturn(false);

    int published = dispatcher.dispatchOnce();

    assertThat(published).isEqualTo(0);
    verify(queuePublisher).publish(any());
    verify(outboxEvents).markPublished(eq(eventId), startsWith("test-dispatcher:"));
    verify(outboxEvents, never()).markFailedAttempt(any(), any(), any(), any());
    verify(deliveryFailureService, never()).handleTerminalFailure(any(), any(), any(), any());
  }

  @Test
  @DisplayName("E: Stale lease when recording failure does not throw or corrupt state")
  void staleLeaseWhenRecordingFailureHandled() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    OutboxEvent event =
        createSampleEvent(
            eventId, jobId, "DOCUMENT_IMPORT_REQUESTED", createValidPayload(eventId, jobId), 1, 5);

    when(outboxEvents.claimBatch(anyString(), anyInt(), any())).thenReturn(List.of(event));
    SqsException throttlingException =
        (SqsException)
            SqsException.builder().message("ThrottlingException").statusCode(429).build();
    org.mockito.Mockito.doThrow(throttlingException).when(queuePublisher).publish(any());
    when(outboxEvents.markFailedAttempt(any(), any(), any(), any())).thenReturn(false);

    int published = dispatcher.dispatchOnce();

    assertThat(published).isEqualTo(0);
    verify(outboxEvents).markFailedAttempt(any(), any(), any(), any());
  }

  @Test
  @DisplayName("Permanent SQS error triggers terminal failure directly without retry")
  void permanentSqsErrorTriggersTerminalFailure() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    OutboxEvent event =
        createSampleEvent(
            eventId, jobId, "DOCUMENT_IMPORT_REQUESTED", createValidPayload(eventId, jobId), 1, 5);

    when(outboxEvents.claimBatch(anyString(), anyInt(), any())).thenReturn(List.of(event));
    QueueDoesNotExistException notFound =
        (QueueDoesNotExistException)
            QueueDoesNotExistException.builder()
                .message("The specified queue does not exist")
                .statusCode(404)
                .build();
    org.mockito.Mockito.doThrow(notFound).when(queuePublisher).publish(any());

    int published = dispatcher.dispatchOnce();

    assertThat(published).isEqualTo(0);
    verify(deliveryFailureService)
        .handleTerminalFailure(
            eq(eventId),
            startsWith("test-dispatcher:"),
            eq(jobId),
            contains("specified queue does not exist"));
    verify(outboxEvents, never()).markFailedAttempt(any(), any(), any(), any());
  }

  @Test
  @DisplayName("Transient SQS error at max attempts triggers terminal failure")
  void transientSqsErrorAtMaxAttemptsTriggersTerminalFailure() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    // attempt 5 of 5
    OutboxEvent event =
        createSampleEvent(
            eventId, jobId, "DOCUMENT_IMPORT_REQUESTED", createValidPayload(eventId, jobId), 5, 5);

    when(outboxEvents.claimBatch(anyString(), anyInt(), any())).thenReturn(List.of(event));
    SqsException serverError =
        (SqsException)
            SqsException.builder().message("InternalServerError").statusCode(500).build();
    org.mockito.Mockito.doThrow(serverError).when(queuePublisher).publish(any());

    int published = dispatcher.dispatchOnce();

    assertThat(published).isEqualTo(0);
    verify(deliveryFailureService)
        .handleTerminalFailure(
            eq(eventId),
            startsWith("test-dispatcher:"),
            eq(jobId),
            contains("InternalServerError"));
    verify(outboxEvents, never()).markFailedAttempt(any(), any(), any(), any());
  }

  @Test
  @DisplayName("I: Batch size and lock duration from properties are respected on claim")
  void batchSizeRespected() {
    when(outboxEvents.claimBatch(startsWith("test-dispatcher:"), eq(10), eq(Duration.ofMinutes(5))))
        .thenReturn(List.of());

    dispatcher.dispatchOnce();

    verify(outboxEvents)
        .claimBatch(startsWith("test-dispatcher:"), eq(10), eq(Duration.ofMinutes(5)));
  }

  @Test
  @DisplayName("J: Empty outbox produces no publish attempts and returns 0")
  void emptyOutboxProducesNoPublish() {
    when(outboxEvents.claimBatch(anyString(), anyInt(), any())).thenReturn(List.of());

    int count = dispatcher.dispatchOnce();

    assertThat(count).isEqualTo(0);
    verify(queuePublisher, never()).publish(any());
    verify(outboxEvents, never()).markPublished(any(), any());
  }

  @Test
  @DisplayName(
      "K: Malformed JSON payload is handled deterministically as terminal failure without retry")
  void malformedPayloadHandledDeterministically() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    OutboxEvent event =
        createSampleEvent(eventId, jobId, "DOCUMENT_IMPORT_REQUESTED", "{not valid json}");

    when(outboxEvents.claimBatch(anyString(), anyInt(), any())).thenReturn(List.of(event));

    int published = dispatcher.dispatchOnce();

    assertThat(published).isEqualTo(0);
    verify(queuePublisher, never()).publish(any());
    verify(deliveryFailureService)
        .handleTerminalFailure(
            eq(eventId), startsWith("test-dispatcher:"), eq(jobId), contains("Malformed"));
    verify(outboxEvents, never()).markFailedAttempt(any(), any(), any(), any());
  }

  @Test
  @DisplayName(
      "K: Unsupported eventType is handled deterministically as terminal failure without retry")
  void unsupportedEventTypeHandledDeterministically() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    OutboxEvent event =
        createSampleEvent(eventId, jobId, "UNSUPPORTED_TYPE", createValidPayload(eventId, jobId));

    when(outboxEvents.claimBatch(anyString(), anyInt(), any())).thenReturn(List.of(event));

    int published = dispatcher.dispatchOnce();

    assertThat(published).isEqualTo(0);
    verify(queuePublisher, never()).publish(any());
    verify(deliveryFailureService)
        .handleTerminalFailure(
            eq(eventId),
            startsWith("test-dispatcher:"),
            eq(jobId),
            contains("Unsupported eventType"));
    verify(outboxEvents, never()).markFailedAttempt(any(), any(), any(), any());
  }

  @Test
  @DisplayName("K: Unsupported schemaVersion is handled deterministically as terminal failure")
  void unsupportedSchemaVersionHandledDeterministically() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    OutboxEvent event =
        createSampleEvent(
            eventId, jobId, "DOCUMENT_IMPORT_REQUESTED", createValidPayload(eventId, jobId, 2));

    when(outboxEvents.claimBatch(anyString(), anyInt(), any())).thenReturn(List.of(event));

    int published = dispatcher.dispatchOnce();

    assertThat(published).isEqualTo(0);
    verify(queuePublisher, never()).publish(any());
    verify(deliveryFailureService)
        .handleTerminalFailure(
            eq(eventId),
            startsWith("test-dispatcher:"),
            eq(jobId),
            contains("Unsupported schemaVersion: 2"));
    verify(outboxEvents, never()).markFailedAttempt(any(), any(), any(), any());
  }

  @Test
  @DisplayName("Section 14: SQS publish is executed outside of any active database transaction")
  void sqsPublishExecutedOutsideDbTransaction() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    OutboxEvent event =
        createSampleEvent(
            eventId, jobId, "DOCUMENT_IMPORT_REQUESTED", createValidPayload(eventId, jobId));

    when(outboxEvents.claimBatch(anyString(), anyInt(), any())).thenReturn(List.of(event));
    when(outboxEvents.markPublished(any(), any())).thenReturn(true);

    AtomicBoolean txActiveDuringPublish = new AtomicBoolean(true);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              txActiveDuringPublish.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return null;
            })
        .when(queuePublisher)
        .publish(any());

    int published = dispatcher.dispatchOnce();

    assertThat(published).isEqualTo(1);
    assertThat(txActiveDuringPublish.get()).isFalse();
  }

  @Test
  @DisplayName("Error sanitization strips sensitive tokens and truncates oversized messages")
  void errorSanitization() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    OutboxEvent event =
        createSampleEvent(
            eventId, jobId, "DOCUMENT_IMPORT_REQUESTED", createValidPayload(eventId, jobId), 1, 5);

    when(outboxEvents.claimBatch(anyString(), anyInt(), any())).thenReturn(List.of(event));

    String sensitiveError =
        "Failed with secret=SuperSecretKey123&password=P@ssw0rd! and a huge error: "
            + "X".repeat(600);
    org.mockito.Mockito.doThrow(new RuntimeException(new IOException(sensitiveError)))
        .when(queuePublisher)
        .publish(any());

    dispatcher.dispatchOnce();

    ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
    verify(outboxEvents)
        .markFailedAttempt(
            eq(eventId), startsWith("test-dispatcher:"), errorCaptor.capture(), any());

    String savedError = errorCaptor.getValue();
    assertThat(savedError).doesNotContain("SuperSecretKey123");
    assertThat(savedError).doesNotContain("P@ssw0rd!");
    assertThat(savedError).contains("secret=***");
    assertThat(savedError).contains("password=***");
    assertThat(savedError.length()).isLessThanOrEqualTo(500);
  }
}
