package com.soap.soap.infrastructure.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soap.soap.application.service.DocumentImportProcessor;
import com.soap.soap.application.service.DocumentImportProcessor.ImportExecutionOutcome;
import com.soap.soap.application.service.DocumentImportProcessor.OutcomeType;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

class SqsDocumentImportConsumerTest {

  private SqsClient sqsClient;
  private DocumentImportProcessor processor;
  private DocumentImportQueueProperties queueProperties;
  private DocumentImportWorkerProperties workerProperties;
  private ObjectMapper objectMapper;
  private SqsDocumentImportConsumer consumer;

  private static final String QUEUE_URL =
      "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";

  @BeforeEach
  void setUp() {
    sqsClient = mock(SqsClient.class);
    processor = mock(DocumentImportProcessor.class);
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
            "test-worker",
            Duration.ofHours(24));
    objectMapper = new ObjectMapper();
    consumer =
        new SqsDocumentImportConsumer(
            sqsClient, queueProperties, workerProperties, processor, objectMapper, null);
  }

  @Test
  @DisplayName("Case O: malformed message (invalid JSON) is ignored and NOT deleted (DLQ policy)")
  void caseO_malformedMessage_ignoredAndNotDeleted() {
    Message message =
        Message.builder()
            .messageId("msg-1")
            .receiptHandle("receipt-1")
            .body("{ not valid json at all }")
            .build();

    ImportExecutionOutcome outcome = consumer.processSingleMessage(message);

    assertThat(outcome).isNull();
    verify(processor, never()).processImport(any(), any());
    verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  @DisplayName("Case P: unsupported schemaVersion is rejected and NOT deleted (DLQ policy)")
  void caseP_unsupportedSchemaVersion_rejectedAndNotDeleted() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    String body =
        """
        {
          "schemaVersion": 99,
          "eventId": "%s",
          "eventType": "DOCUMENT_IMPORT_REQUESTED",
          "importJobId": "%s"
        }
        """
            .formatted(eventId, jobId);

    Message message =
        Message.builder().messageId("msg-2").receiptHandle("receipt-2").body(body).build();

    ImportExecutionOutcome outcome = consumer.processSingleMessage(message);

    assertThat(outcome).isNull();
    verify(processor, never()).processImport(any(), any());
    verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  @DisplayName("Case Q: unsupported eventType is rejected and NOT deleted (DLQ policy)")
  void caseQ_unsupportedEventType_rejectedAndNotDeleted() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    String body =
        """
        {
          "schemaVersion": 1,
          "eventId": "%s",
          "eventType": "UNKNOWN_EVENT_TYPE",
          "importJobId": "%s"
        }
        """
            .formatted(eventId, jobId);

    Message message =
        Message.builder().messageId("msg-3").receiptHandle("receipt-3").body(body).build();

    ImportExecutionOutcome outcome = consumer.processSingleMessage(message);

    assertThat(outcome).isNull();
    verify(processor, never()).processImport(any(), any());
    verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  @DisplayName("Case R: poll configuration parameters respected in ReceiveMessageRequest")
  void caseR_pollConfigParametersRespected() {
    DocumentImportWorkerProperties customProps =
        new DocumentImportWorkerProperties(
            true,
            false,
            15,
            45,
            1,
            Duration.ofMillis(100),
            Duration.ofSeconds(1),
            Duration.ofSeconds(60),
            Duration.ofSeconds(20),
            "custom-worker",
            Duration.ofHours(24));
    SqsDocumentImportConsumer customConsumer =
        new SqsDocumentImportConsumer(
            sqsClient, queueProperties, customProps, processor, objectMapper, null);

    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(ReceiveMessageResponse.builder().messages(List.of()).build());

    customConsumer.pollOnce();

    ArgumentCaptor<ReceiveMessageRequest> captor =
        ArgumentCaptor.forClass(ReceiveMessageRequest.class);
    verify(sqsClient).receiveMessage(captor.capture());
    ReceiveMessageRequest request = captor.getValue();

    assertThat(request.queueUrl()).isEqualTo(QUEUE_URL);
    assertThat(request.maxNumberOfMessages()).isEqualTo(1);
    assertThat(request.waitTimeSeconds()).isEqualTo(15);
    assertThat(request.visibilityTimeout()).isEqualTo(45);
  }

  @Test
  @DisplayName(
      "MDC correlationId, eventId, importJobId set during processing and cleared afterwards")
  void mdcContextSetDuringProcessingAndClearedAfterwards() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    String body =
        """
        {
          "schemaVersion": 1,
          "eventId": "%s",
          "eventType": "DOCUMENT_IMPORT_REQUESTED",
          "importJobId": "%s"
        }
        """
            .formatted(eventId, jobId);

    Message message =
        Message.builder().messageId("msg-mdc").receiptHandle("receipt-mdc").body(body).build();

    when(processor.processImport(eq(jobId), eq("receipt-mdc")))
        .thenAnswer(
            invocation -> {
              assertThat(org.slf4j.MDC.get("correlationId")).isEqualTo(eventId.toString());
              assertThat(org.slf4j.MDC.get("eventId")).isEqualTo(eventId.toString());
              assertThat(org.slf4j.MDC.get("importJobId")).isEqualTo(jobId.toString());
              return new ImportExecutionOutcome(OutcomeType.COMPLETED, "Done", jobId);
            });

    ImportExecutionOutcome outcome = consumer.processSingleMessage(message);

    assertThat(outcome).isNotNull();
    assertThat(org.slf4j.MDC.get("correlationId")).isNull();
    assertThat(org.slf4j.MDC.get("eventId")).isNull();
    assertThat(org.slf4j.MDC.get("importJobId")).isNull();
  }

  @Test
  @DisplayName("MDC context cleared even if processor throws exception")
  void mdcContextClearedOnProcessorException() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    String body =
        """
        {
          "schemaVersion": 1,
          "eventId": "%s",
          "eventType": "DOCUMENT_IMPORT_REQUESTED",
          "importJobId": "%s"
        }
        """
            .formatted(eventId, jobId);

    Message message =
        Message.builder().messageId("msg-err").receiptHandle("receipt-err").body(body).build();

    when(processor.processImport(eq(jobId), eq("receipt-err")))
        .thenAnswer(
            invocation -> {
              assertThat(org.slf4j.MDC.get("correlationId")).isEqualTo(eventId.toString());
              throw new RuntimeException("Simulated processing crash");
            });

    org.junit.jupiter.api.Assertions.assertThrows(
        RuntimeException.class, () -> consumer.processSingleMessage(message));

    assertThat(org.slf4j.MDC.get("correlationId")).isNull();
    assertThat(org.slf4j.MDC.get("eventId")).isNull();
    assertThat(org.slf4j.MDC.get("importJobId")).isNull();
  }

  @Test
  @DisplayName("MDC context not leaked on malformed message")
  void mdcContextNotLeakedOnMalformedMessage() {
    Message message =
        Message.builder()
            .messageId("msg-bad")
            .receiptHandle("receipt-bad")
            .body("bad json")
            .build();

    consumer.processSingleMessage(message);

    assertThat(org.slf4j.MDC.get("correlationId")).isNull();
    assertThat(org.slf4j.MDC.get("eventId")).isNull();
    assertThat(org.slf4j.MDC.get("importJobId")).isNull();
  }

  @Test
  @DisplayName("Stale staging files cleanup: deletes old files, keeps fresh files and directories")
  void cleanStaleStagingFiles_deletesOldKeepsFreshAndDirectories(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path tempRoot) throws java.io.IOException {
    java.nio.file.Path staging = tempRoot.resolve("staging");
    java.nio.file.Files.createDirectories(staging);

    // Old file: 25 hours old
    java.nio.file.Path oldFile = java.nio.file.Files.createTempFile(staging, "old-source-", ".tmp");
    java.nio.file.attribute.FileTime oldTime =
        java.nio.file.attribute.FileTime.from(
            java.time.Instant.now().minus(java.time.Duration.ofHours(25)));
    java.nio.file.Files.setLastModifiedTime(oldFile, oldTime);

    // Fresh file: 1 hour old
    java.nio.file.Path freshFile =
        java.nio.file.Files.createTempFile(staging, "fresh-source-", ".tmp");
    java.nio.file.attribute.FileTime freshTime =
        java.nio.file.attribute.FileTime.from(
            java.time.Instant.now().minus(java.time.Duration.ofHours(1)));
    java.nio.file.Files.setLastModifiedTime(freshFile, freshTime);

    // Subdirectory in staging
    java.nio.file.Path subDir = staging.resolve("sub-dir");
    java.nio.file.Files.createDirectories(subDir);

    // Outside file in storage root but outside staging directory: 25 hours old
    java.nio.file.Path outsideFile = tempRoot.resolve("outside-doc.pdf");
    java.nio.file.Files.writeString(outsideFile, "outside document content");
    java.nio.file.Files.setLastModifiedTime(outsideFile, oldTime);

    SqsDocumentImportConsumer stagingConsumer =
        new SqsDocumentImportConsumer(
            sqsClient, queueProperties, workerProperties, processor, objectMapper, null, tempRoot);

    int deleted = stagingConsumer.cleanStaleStagingFiles();

    assertThat(deleted).isEqualTo(1);
    assertThat(java.nio.file.Files.exists(oldFile)).isFalse();
    assertThat(java.nio.file.Files.exists(freshFile)).isTrue();
    assertThat(java.nio.file.Files.exists(subDir)).isTrue();
    assertThat(java.nio.file.Files.exists(outsideFile)).isTrue();
  }

  @Test
  @DisplayName("Valid envelope delegates to processor with extracted jobId and receiptHandle")
  void validEnvelope_delegatesToProcessor() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    String body =
        """
        {
          "schemaVersion": 1,
          "eventId": "%s",
          "eventType": "DOCUMENT_IMPORT_REQUESTED",
          "importJobId": "%s"
        }
        """
            .formatted(eventId, jobId);

    Message message =
        Message.builder().messageId("msg-4").receiptHandle("receipt-4").body(body).build();

    when(processor.processImport(eq(jobId), eq("receipt-4")))
        .thenReturn(new ImportExecutionOutcome(OutcomeType.COMPLETED, "Done", jobId));

    ImportExecutionOutcome outcome = consumer.processSingleMessage(message);

    assertThat(outcome).isNotNull();
    assertThat(outcome.type()).isEqualTo(OutcomeType.COMPLETED);
    assertThat(outcome.jobId()).isEqualTo(jobId);
    verify(processor).processImport(jobId, "receipt-4");
  }

  @Test
  @DisplayName("Poison message with invalid UUID importJobId is ignored and NOT deleted")
  void poisonMessage_invalidUuid_ignoredAndNotDeleted() {
    String body =
        """
        {
          "schemaVersion": 1,
          "eventId": "%s",
          "eventType": "DOCUMENT_IMPORT_REQUESTED",
          "importJobId": "not-a-valid-uuid"
        }
        """
            .formatted(UUID.randomUUID());

    Message message =
        Message.builder().messageId("msg-5").receiptHandle("receipt-5").body(body).build();

    ImportExecutionOutcome outcome = consumer.processSingleMessage(message);

    assertThat(outcome).isNull();
    verify(processor, never()).processImport(any(), any());
    verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  @DisplayName(
      "Default configuration polling respects waitTime=20, visibilityTimeout=60, maxMessages=1")
  void defaultConfiguration_pollingRequestContract() {
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(ReceiveMessageResponse.builder().messages(List.of()).build());

    consumer.pollOnce();

    ArgumentCaptor<ReceiveMessageRequest> captor =
        ArgumentCaptor.forClass(ReceiveMessageRequest.class);
    verify(sqsClient).receiveMessage(captor.capture());
    ReceiveMessageRequest request = captor.getValue();

    assertThat(request.queueUrl()).isEqualTo(QUEUE_URL);
    assertThat(request.maxNumberOfMessages()).isEqualTo(1);
    assertThat(request.waitTimeSeconds()).isEqualTo(20);
    assertThat(request.visibilityTimeout()).isEqualTo(60);
  }
}
