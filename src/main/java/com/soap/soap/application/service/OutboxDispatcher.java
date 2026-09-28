package com.soap.soap.application.service;

import com.soap.soap.application.model.DocumentImportQueueMessage;
import com.soap.soap.application.port.out.ImportQueuePublisher;
import com.soap.soap.application.port.out.OutboxEventRepositoryPort;
import com.soap.soap.domain.model.DocumentImportRequestedEvent;
import com.soap.soap.domain.model.OutboxEvent;
import com.soap.soap.infrastructure.persistence.mapper.OutboxEventSerializer;
import com.soap.soap.infrastructure.queue.DocumentImportOutboxProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.services.sqs.model.InvalidAddressException;
import software.amazon.awssdk.services.sqs.model.InvalidAttributeNameException;
import software.amazon.awssdk.services.sqs.model.InvalidAttributeValueException;
import software.amazon.awssdk.services.sqs.model.InvalidMessageContentsException;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ResourceNotFoundException;
import software.amazon.awssdk.services.sqs.model.SqsException;

@Service
@ConditionalOnProperty(name = "app.document-import.outbox.enabled", havingValue = "true")
@ConditionalOnExpression(
    "'${APP_ROLE:${app.role:api}}'.equalsIgnoreCase('api') or '${APP_ROLE:${app.role:api}}'.equalsIgnoreCase('all')")
public class OutboxDispatcher {

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
  private static final int MAX_ERROR_LENGTH = 500;
  private static final Pattern SENSITIVE_PATTERN =
      Pattern.compile("(?i)(password|secret|key|token|accessKey|signature)=[^&\\s]+");

  private final OutboxEventRepositoryPort outboxEvents;
  private final ImportQueuePublisher queuePublisher;
  private final OutboxEventSerializer outboxEventSerializer;
  private final DocumentImportOutboxProperties properties;
  private final OutboxDeliveryFailureService deliveryFailureService;
  private final MeterRegistry meterRegistry;

  @Autowired
  public OutboxDispatcher(
      OutboxEventRepositoryPort outboxEvents,
      ImportQueuePublisher queuePublisher,
      OutboxEventSerializer outboxEventSerializer,
      DocumentImportOutboxProperties properties,
      OutboxDeliveryFailureService deliveryFailureService,
      @Autowired(required = false) MeterRegistry meterRegistry) {
    this.outboxEvents = Objects.requireNonNull(outboxEvents, "outboxEvents must not be null");
    this.queuePublisher = Objects.requireNonNull(queuePublisher, "queuePublisher must not be null");
    this.outboxEventSerializer =
        Objects.requireNonNull(outboxEventSerializer, "outboxEventSerializer must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.deliveryFailureService =
        Objects.requireNonNull(deliveryFailureService, "deliveryFailureService must not be null");
    this.meterRegistry = meterRegistry;
  }

  public int dispatchOnce() {
    int batchSize = properties.batchSize();
    Duration lockDuration = properties.lockDuration();
    // Unique claim token per batch/claim operation to guarantee fencing against stale claims
    String claimToken = properties.dispatcherId() + ":" + UUID.randomUUID();

    List<OutboxEvent> batch = outboxEvents.claimBatch(claimToken, batchSize, lockDuration);
    if (batch.isEmpty()) {
      log.trace("No outbox events claimed with token {}", claimToken);
      return 0;
    }

    log.info("Claimed {} outbox events with claim token {}", batch.size(), claimToken);
    recordMetric("document.import.outbox.claimed", batch.size());

    int publishedCount = 0;
    for (OutboxEvent event : batch) {
      if (dispatchEvent(event, claimToken)) {
        publishedCount++;
      }
    }
    return publishedCount;
  }

  private boolean dispatchEvent(OutboxEvent event, String claimToken) {
    UUID eventId = event.id();
    UUID importJobId = event.aggregateId();
    int attempt = event.attemptCount();

    // 1. Validate eventType and payload (Poison pill / unsupported event handling)
    DocumentImportQueueMessage message;
    try {
      if (!DocumentImportQueueMessage.DOCUMENT_IMPORT_REQUESTED_TYPE.equals(event.eventType())) {
        throw new IllegalArgumentException("Unsupported eventType: " + event.eventType());
      }
      DocumentImportRequestedEvent payload =
          outboxEventSerializer.deserializeDocumentImportRequested(event.payload());
      if (payload.eventVersion() != 1) {
        throw new IllegalArgumentException("Unsupported schemaVersion: " + payload.eventVersion());
      }
      if (!eventId.equals(payload.eventId()) || !importJobId.equals(payload.jobId())) {
        throw new IllegalArgumentException(
            "Payload eventId/jobId does not match outbox event identity");
      }
      message = DocumentImportQueueMessage.forImportJob(eventId, importJobId);
    } catch (Exception ex) {
      log.error(
          "Malformed or unsupported outbox event [eventId={}, importJobId={}]: {}",
          eventId,
          importJobId,
          ex.getMessage());
      recordMetric("document.import.outbox.failed", 1);
      String sanitizedError = sanitizeError("Malformed or unsupported event: " + ex.getMessage());
      deliveryFailureService.handleTerminalFailure(
          eventId, claimToken, importJobId, sanitizedError);
      return false;
    }

    // 2. Publish to SQS (network I/O outside DB transaction)
    try {
      queuePublisher.publish(message);
      log.info(
          "Published outbox event [eventId={}, importJobId={}, attempt={}] to SQS",
          eventId,
          importJobId,
          attempt);
    } catch (Exception ex) {
      log.warn(
          "Failed to publish outbox event [eventId={}, importJobId={}, attempt={}]: {}",
          eventId,
          importJobId,
          attempt,
          ex.getMessage());
      recordMetric("document.import.outbox.failed", 1);
      String sanitizedError = sanitizeError("SQS publish error: " + ex.getMessage());

      boolean transientError = isTransient(ex);
      boolean maxAttemptsReached = attempt >= event.maxAttempts();

      if (!transientError || maxAttemptsReached) {
        log.error(
            "Terminal delivery failure for outbox event [eventId={}, importJobId={}, transient={}, attempt={}/{}]",
            eventId,
            importJobId,
            transientError,
            attempt,
            event.maxAttempts());
        deliveryFailureService.handleTerminalFailure(
            eventId, claimToken, importJobId, sanitizedError);
      } else {
        boolean failedMarked =
            outboxEvents.markFailedAttempt(
                eventId, claimToken, sanitizedError, properties.retryBackoff());
        if (!failedMarked) {
          log.warn(
              "Failed to record failure for outbox event [eventId={}]; lease expired or reclaimed",
              eventId);
        }
      }
      return false;
    }

    // 3. Mark published (conditioned on valid lease)
    boolean published = outboxEvents.markPublished(eventId, claimToken);
    if (published) {
      log.info(
          "Marked outbox event [eventId={}, importJobId={}] as PUBLISHED", eventId, importJobId);
      recordMetric("document.import.outbox.published", 1);
      return true;
    } else {
      log.warn(
          "Outbox event [eventId={}, importJobId={}] was published to SQS, but lease expired before markPublished",
          eventId,
          importJobId);
      return false;
    }
  }

  public static boolean isTransient(Throwable ex) {
    if (ex == null) {
      return false;
    }
    if (ex instanceof IllegalArgumentException || ex instanceof IllegalStateException) {
      return false;
    }
    if (ex instanceof QueueDoesNotExistException
        || ex instanceof ResourceNotFoundException
        || ex instanceof InvalidAddressException
        || ex instanceof InvalidAttributeNameException
        || ex instanceof InvalidAttributeValueException
        || ex instanceof InvalidMessageContentsException) {
      return false;
    }
    if (ex instanceof SqsException sqsEx) {
      if (sqsEx.isThrottlingException()) {
        return true;
      }
      int statusCode = sqsEx.statusCode();
      if (statusCode >= 500) {
        return true;
      }
      if (statusCode == 400 || statusCode == 401 || statusCode == 403 || statusCode == 404) {
        return false;
      }
    }
    if (ex instanceof ApiCallTimeoutException || ex instanceof ApiCallAttemptTimeoutException) {
      return true;
    }
    Throwable cause = ex;
    while (cause != null) {
      if (cause instanceof IOException
          || cause instanceof SocketTimeoutException
          || cause instanceof ConnectException
          || cause instanceof HttpTimeoutException
          || cause instanceof TimeoutException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  private void recordMetric(String name, double amount) {
    if (meterRegistry != null) {
      meterRegistry.counter(name).increment(amount);
    }
  }

  private String sanitizeError(String error) {
    if (error == null) {
      return "Unknown error";
    }
    String sanitized = SENSITIVE_PATTERN.matcher(error).replaceAll("$1=***");
    if (sanitized.length() > MAX_ERROR_LENGTH) {
      return sanitized.substring(0, MAX_ERROR_LENGTH);
    }
    return sanitized;
  }
}
