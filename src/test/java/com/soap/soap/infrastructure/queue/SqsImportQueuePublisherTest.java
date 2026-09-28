package com.soap.soap.infrastructure.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soap.soap.application.model.DocumentImportQueueMessage;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

class SqsImportQueuePublisherTest {

  private final SqsClient sqsClient = mock(SqsClient.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName(
      "A & H: SqsImportQueuePublisher publishes formatted JSON message to configured SQS queue")
  void publishesFormattedJsonMessage() throws Exception {
    String queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";
    DocumentImportQueueProperties properties =
        new DocumentImportQueueProperties(queueUrl, "us-east-1", null);
    SqsImportQueuePublisher publisher =
        new SqsImportQueuePublisher(sqsClient, properties, objectMapper);

    when(sqsClient.sendMessage(any(SendMessageRequest.class)))
        .thenReturn(SendMessageResponse.builder().messageId("msg-123").build());

    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    DocumentImportQueueMessage message = DocumentImportQueueMessage.forImportJob(eventId, jobId);

    publisher.publish(message);

    ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
    verify(sqsClient).sendMessage(captor.capture());

    SendMessageRequest captured = captor.getValue();
    assertThat(captured.queueUrl()).isEqualTo(queueUrl);

    JsonNode json = objectMapper.readTree(captured.messageBody());
    assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
    assertThat(json.get("eventId").asText()).isEqualTo(eventId.toString());
    assertThat(json.get("eventType").asText()).isEqualTo("DOCUMENT_IMPORT_REQUESTED");
    assertThat(json.get("importJobId").asText()).isEqualTo(jobId.toString());
  }

  @Test
  @DisplayName("L: Missing or blank queue URL fails fast with useful configuration error")
  void missingQueueUrlFailsFast() {
    DocumentImportQueueProperties emptyProps =
        new DocumentImportQueueProperties("", "us-east-1", null);
    assertThatThrownBy(() -> new SqsImportQueuePublisher(sqsClient, emptyProps, objectMapper))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SQS queue URL (app.document-import.queue.url) must be configured");

    DocumentImportQueueProperties nullProps =
        new DocumentImportQueueProperties(null, "us-east-1", null);
    assertThatThrownBy(() -> new SqsImportQueuePublisher(sqsClient, nullProps, objectMapper))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SQS queue URL (app.document-import.queue.url) must be configured");
  }

  @Test
  @DisplayName("Throws NullPointerException when message is null")
  void nullMessageThrows() {
    DocumentImportQueueProperties properties =
        new DocumentImportQueueProperties("https://sqs.url", "us-east-1", null);
    SqsImportQueuePublisher publisher =
        new SqsImportQueuePublisher(sqsClient, properties, objectMapper);

    assertThatThrownBy(() -> publisher.publish(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("message must not be null");
  }

  @Test
  @DisplayName("Propagates SQS client exceptions directly for dispatcher error handling")
  void propagatesSqsException() {
    DocumentImportQueueProperties properties =
        new DocumentImportQueueProperties("https://sqs.url", "us-east-1", null);
    SqsImportQueuePublisher publisher =
        new SqsImportQueuePublisher(sqsClient, properties, objectMapper);

    when(sqsClient.sendMessage(any(SendMessageRequest.class)))
        .thenThrow(SqsException.builder().message("Service unavailable").build());

    DocumentImportQueueMessage message =
        DocumentImportQueueMessage.forImportJob(UUID.randomUUID(), UUID.randomUUID());

    assertThatThrownBy(() -> publisher.publish(message))
        .isInstanceOf(SqsException.class)
        .hasMessageContaining("Service unavailable");
  }
}
