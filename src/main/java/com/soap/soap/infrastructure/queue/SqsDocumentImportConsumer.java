package com.soap.soap.infrastructure.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soap.soap.application.model.DocumentImportQueueMessage;
import com.soap.soap.application.service.DocumentImportProcessor;
import com.soap.soap.application.service.DocumentImportProcessor.ImportExecutionOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

@Component
@ConditionalOnProperty(
    name = "app.document-import.worker.enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnExpression(
    "'${APP_ROLE:${app.role:api}}'.equalsIgnoreCase('worker') or '${APP_ROLE:${app.role:api}}'.equalsIgnoreCase('all')")
public class SqsDocumentImportConsumer implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(SqsDocumentImportConsumer.class);

  private final SqsClient sqsClient;
  private final DocumentImportQueueProperties queueProperties;
  private final DocumentImportWorkerProperties workerProperties;
  private final DocumentImportProcessor processor;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;
  private final Path documentStorageRoot;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private ExecutorService pollerExecutor;

  record ParsedMessageEnvelope(UUID eventId, UUID importJobId, boolean isValid) {}

  @Autowired
  public SqsDocumentImportConsumer(
      @Autowired(required = false) SqsClient sqsClient,
      DocumentImportQueueProperties queueProperties,
      DocumentImportWorkerProperties workerProperties,
      DocumentImportProcessor processor,
      @Autowired(required = false) ObjectMapper objectMapper,
      @Autowired(required = false) MeterRegistry meterRegistry,
      @Autowired(required = false) @Qualifier("documentStorageRoot") Path documentStorageRoot) {
    this.queueProperties =
        Objects.requireNonNull(queueProperties, "queueProperties must not be null");
    if (queueProperties.url() == null || queueProperties.url().isBlank()) {
      throw new IllegalStateException(
          "SQS queue URL (app.document-import.queue.url) must be configured when document import worker is enabled");
    }
    this.sqsClient = Objects.requireNonNull(sqsClient, "sqsClient must not be null");
    this.workerProperties =
        Objects.requireNonNull(workerProperties, "workerProperties must not be null");
    this.processor = Objects.requireNonNull(processor, "processor must not be null");
    this.objectMapper = objectMapper != null ? objectMapper.copy() : new ObjectMapper();
    this.meterRegistry = meterRegistry;
    this.documentStorageRoot =
        documentStorageRoot != null
            ? documentStorageRoot
            : Path.of(".private-assets").toAbsolutePath().normalize();
  }

  public SqsDocumentImportConsumer(
      SqsClient sqsClient,
      DocumentImportQueueProperties queueProperties,
      DocumentImportWorkerProperties workerProperties,
      DocumentImportProcessor processor,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry) {
    this(
        sqsClient, queueProperties, workerProperties, processor, objectMapper, meterRegistry, null);
  }

  @Override
  public synchronized void start() {
    if (running.get()) {
      return;
    }
    cleanStaleStagingFiles();
    running.set(true);
    pollerExecutor =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "sqs-import-worker-poller");
              t.setDaemon(true);
              return t;
            });
    pollerExecutor.submit(this::pollingLoop);
    LOG.info(
        "SqsDocumentImportConsumer started. queueUrl={} workerId={} waitTime={}s",
        queueProperties.url(),
        workerProperties.workerId(),
        workerProperties.waitTimeSeconds());
  }

  @Override
  public synchronized void stop() {
    if (!running.get()) {
      return;
    }
    running.set(false);
    if (pollerExecutor != null) {
      pollerExecutor.shutdownNow();
      try {
        if (!pollerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
          LOG.warn("SqsDocumentImportConsumer poller did not terminate within 5 seconds");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      pollerExecutor = null;
    }
    LOG.info("SqsDocumentImportConsumer stopped");
  }

  @Override
  public void stop(Runnable callback) {
    stop();
    callback.run();
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  @Override
  public boolean isAutoStartup() {
    return workerProperties.isAutoStartup();
  }

  @Override
  public int getPhase() {
    return Integer.MAX_VALUE - 100;
  }

  public int cleanStaleStagingFiles() {
    if (documentStorageRoot == null) {
      return 0;
    }
    Path stagingDir = documentStorageRoot.resolve("staging").normalize();
    if (!Files.exists(stagingDir) || !Files.isDirectory(stagingDir)) {
      return 0;
    }
    Instant cutoff = Instant.now().minus(workerProperties.stagingCleanupAge());
    int deletedCount = 0;
    try (Stream<Path> stream = Files.list(stagingDir)) {
      for (Path entry : (Iterable<Path>) stream::iterator) {
        try {
          Path normalized = entry.normalize();
          if (Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
              && normalized.startsWith(stagingDir)
              && !normalized.equals(stagingDir)) {
            BasicFileAttributes attrs =
                Files.readAttributes(
                    normalized, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attrs.lastModifiedTime().toInstant().isBefore(cutoff)) {
              Files.deleteIfExists(normalized);
              deletedCount++;
              LOG.info("Cleaned up stale staging file: {}", normalized.getFileName());
            }
          }
        } catch (IOException e) {
          LOG.warn(
              "Failed to clean up stale staging file {}: {}", entry.getFileName(), e.getMessage());
        }
      }
    } catch (Exception e) {
      LOG.warn("Error scanning staging directory for stale files: {}", e.getMessage());
    }
    return deletedCount;
  }

  private void pollingLoop() {
    while (running.get() && !Thread.currentThread().isInterrupted()) {
      try {
        pollOnce();
        if (workerProperties.pollDelay() != null && !workerProperties.pollDelay().isZero()) {
          Thread.sleep(workerProperties.pollDelay().toMillis());
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        LOG.error("Unexpected error in SQS polling loop: {}", e.getMessage(), e);
        try {
          Thread.sleep(workerProperties.errorBackoff().toMillis());
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
  }

  public int pollOnce() {
    ReceiveMessageRequest receiveRequest =
        ReceiveMessageRequest.builder()
            .queueUrl(queueProperties.url())
            .waitTimeSeconds(workerProperties.waitTimeSeconds())
            .visibilityTimeout(workerProperties.visibilityTimeoutSeconds())
            .maxNumberOfMessages(workerProperties.maxMessages())
            .build();

    ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
    int count = 0;
    if (response != null && response.hasMessages()) {
      for (Message message : response.messages()) {
        if (!running.get()) {
          break;
        }
        count++;
        processSingleMessage(message);
      }
    }
    return count;
  }

  public ImportExecutionOutcome processSingleMessage(Message message) {
    if (message == null) {
      return null;
    }
    incrementMetric("document.import.worker.received");
    LOG.debug(
        "Received SQS message: id={} receiptHandle={}",
        message.messageId(),
        message.receiptHandle());

    ParsedMessageEnvelope envelope = parseEnvelope(message);
    if (envelope == null || !envelope.isValid()) {
      // Malformed or unsupported envelope: do NOT delete, leave for SQS DLQ redrive
      LOG.warn(
          "SQS message {} failed contract validation or is malformed. Leaving for SQS DLQ redrive.",
          message.messageId());
      return null;
    }

    String correlationId =
        envelope.eventId() != null
            ? envelope.eventId().toString()
            : envelope.importJobId().toString();

    MDC.put("correlationId", correlationId);
    if (envelope.eventId() != null) {
      MDC.put("eventId", envelope.eventId().toString());
    }
    if (envelope.importJobId() != null) {
      MDC.put("importJobId", envelope.importJobId().toString());
    }

    try {
      return processor.processImport(envelope.importJobId(), message.receiptHandle());
    } finally {
      MDC.remove("correlationId");
      MDC.remove("eventId");
      MDC.remove("importJobId");
    }
  }

  private ParsedMessageEnvelope parseEnvelope(Message message) {
    String body = message.body();
    if (body == null || body.isBlank()) {
      LOG.warn("SQS message {} has empty body", message.messageId());
      return new ParsedMessageEnvelope(null, null, false);
    }

    try {
      JsonNode root = objectMapper.readTree(body);

      // Validate schemaVersion == 1
      JsonNode schemaVersionNode = root.get("schemaVersion");
      if (schemaVersionNode == null
          || schemaVersionNode.asInt(-1) != DocumentImportQueueMessage.CURRENT_SCHEMA_VERSION) {
        LOG.warn(
            "SQS message {} rejected: unsupported schemaVersion '{}'",
            message.messageId(),
            schemaVersionNode != null ? schemaVersionNode.asText() : "null");
        return new ParsedMessageEnvelope(null, null, false);
      }

      // Validate eventType == DOCUMENT_IMPORT_REQUESTED
      JsonNode eventTypeNode = root.get("eventType");
      if (eventTypeNode == null
          || !DocumentImportQueueMessage.DOCUMENT_IMPORT_REQUESTED_TYPE.equals(
              eventTypeNode.asText())) {
        LOG.warn(
            "SQS message {} rejected: unsupported eventType '{}'",
            message.messageId(),
            eventTypeNode != null ? eventTypeNode.asText() : "null");
        return new ParsedMessageEnvelope(null, null, false);
      }

      // Validate eventId is valid UUID
      JsonNode eventIdNode = root.get("eventId");
      if (eventIdNode == null || eventIdNode.asText().isBlank()) {
        LOG.warn("SQS message {} rejected: missing eventId", message.messageId());
        return new ParsedMessageEnvelope(null, null, false);
      }
      UUID eventId = UUID.fromString(eventIdNode.asText());

      // Validate importJobId is valid UUID
      JsonNode jobIdNode = root.get("importJobId");
      if (jobIdNode == null || jobIdNode.asText().isBlank()) {
        LOG.warn("SQS message {} rejected: missing importJobId", message.messageId());
        return new ParsedMessageEnvelope(eventId, null, false);
      }
      UUID importJobId = UUID.fromString(jobIdNode.asText());

      return new ParsedMessageEnvelope(eventId, importJobId, true);

    } catch (Exception e) {
      LOG.warn(
          "SQS message {} failed JSON parsing or UUID extraction: {}",
          message.messageId(),
          e.getMessage());
      return new ParsedMessageEnvelope(null, null, false);
    }
  }

  private void incrementMetric(String metricName) {
    if (meterRegistry != null) {
      try {
        meterRegistry.counter(metricName).increment();
      } catch (Exception ignored) {
      }
    }
  }
}
