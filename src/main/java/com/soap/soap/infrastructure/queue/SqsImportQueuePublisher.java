package com.soap.soap.infrastructure.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soap.soap.application.model.DocumentImportQueueMessage;
import com.soap.soap.application.port.out.ImportQueuePublisher;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
@ConditionalOnProperty(name = "app.document-import.outbox.enabled", havingValue = "true")
@ConditionalOnExpression(
    "'${APP_ROLE:${app.role:api}}'.equalsIgnoreCase('api') or '${APP_ROLE:${app.role:api}}'.equalsIgnoreCase('all')")
public class SqsImportQueuePublisher implements ImportQueuePublisher {

  private static final Logger log = LoggerFactory.getLogger(SqsImportQueuePublisher.class);

  private final SqsClient sqsClient;
  private final DocumentImportQueueProperties properties;
  private final ObjectMapper objectMapper;

  @Autowired
  public SqsImportQueuePublisher(
      SqsClient sqsClient,
      DocumentImportQueueProperties properties,
      @Autowired(required = false) ObjectMapper objectMapper) {
    this.sqsClient = Objects.requireNonNull(sqsClient, "sqsClient must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    if (properties.url() == null || properties.url().isBlank()) {
      throw new IllegalStateException(
          "SQS queue URL (app.document-import.queue.url) must be configured when document import outbox is enabled");
    }
    this.objectMapper = objectMapper != null ? objectMapper.copy() : new ObjectMapper();
  }

  @Override
  public void publish(DocumentImportQueueMessage message) {
    Objects.requireNonNull(message, "message must not be null");

    if (properties.url() == null || properties.url().isBlank()) {
      throw new IllegalStateException(
          "SQS queue URL is not configured (app.document-import.queue.url)");
    }

    String jsonBody;
    try {
      jsonBody = objectMapper.writeValueAsString(message);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize DocumentImportQueueMessage", e);
    }

    SendMessageRequest request =
        SendMessageRequest.builder().queueUrl(properties.url()).messageBody(jsonBody).build();

    sqsClient.sendMessage(request);
    log.debug(
        "Sent message [eventId={}, importJobId={}] to SQS queue {}",
        message.eventId(),
        message.importJobId(),
        properties.url());
  }
}
