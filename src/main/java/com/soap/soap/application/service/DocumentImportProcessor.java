package com.soap.soap.application.service;

import com.soap.soap.application.command.CompleteDocumentImportCommand;
import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.exception.JobLeaseLostException;
import com.soap.soap.application.exception.StorageObjectNotFoundException;
import com.soap.soap.application.exception.TransientStorageException;
import com.soap.soap.application.model.DocumentChunk;
import com.soap.soap.application.model.ParsedDocument;
import com.soap.soap.application.port.out.DocumentAssetStoragePort;
import com.soap.soap.application.port.out.DocumentObjectStoragePort;
import com.soap.soap.application.port.out.DocumentParserPort;
import com.soap.soap.application.port.out.ImportJobRepositoryPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.usecase.CompleteDocumentImportUseCase;
import com.soap.soap.application.usecase.FailDocumentImportUseCase;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentSection;
import com.soap.soap.domain.model.DocumentUnit;
import com.soap.soap.domain.model.DocumentUnitKind;
import com.soap.soap.domain.model.ImportJob;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.domain.model.StorageProvider;
import com.soap.soap.domain.model.WorkerClaim;
import com.soap.soap.infrastructure.queue.DocumentImportQueueProperties;
import com.soap.soap.infrastructure.queue.DocumentImportWorkerProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;

@Service
public class DocumentImportProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(DocumentImportProcessor.class);

  public enum OutcomeType {
    COMPLETED,
    ALREADY_COMPLETED,
    TERMINAL_FAILED,
    ACTIVE_BY_OTHER_WORKER,
    NOT_FOUND,
    RETRY_SCHEDULED,
    LEASE_LOST
  }

  public record ImportExecutionOutcome(OutcomeType type, String message, UUID jobId) {}

  private final ImportJobRepositoryPort importJobs;
  private final ImportedDocumentRepositoryPort importedDocuments;
  private final DocumentObjectStoragePort documentObjectStorage;
  private final DocumentAssetStoragePort documentAssetStorage;
  private final DocumentParserPort documentParser;
  private final DocumentLanguageResolver languageResolver;
  private final DocumentLanguagePolicy languagePolicy;
  private final DocumentChunker chunker;
  private final CompleteDocumentImportUseCase completeUseCase;
  private final FailDocumentImportUseCase failUseCase;
  private final DocumentImportWorkerProperties workerProperties;
  private final DocumentImportQueueProperties queueProperties;
  private final SqsClient sqsClient;
  private final Path documentStorageRoot;
  private final MeterRegistry meterRegistry;

  public DocumentImportProcessor(
      ImportJobRepositoryPort importJobs,
      ImportedDocumentRepositoryPort importedDocuments,
      DocumentObjectStoragePort documentObjectStorage,
      DocumentAssetStoragePort documentAssetStorage,
      DocumentParserPort documentParser,
      DocumentLanguageResolver languageResolver,
      DocumentLanguagePolicy languagePolicy,
      DocumentChunker chunker,
      CompleteDocumentImportUseCase completeUseCase,
      FailDocumentImportUseCase failUseCase,
      DocumentImportWorkerProperties workerProperties,
      DocumentImportQueueProperties queueProperties,
      @Autowired(required = false) SqsClient sqsClient,
      @Qualifier("documentStorageRoot") Path documentStorageRoot,
      @Autowired(required = false) MeterRegistry meterRegistry) {
    this.importJobs = Objects.requireNonNull(importJobs, "importJobs must not be null");
    this.importedDocuments =
        Objects.requireNonNull(importedDocuments, "importedDocuments must not be null");
    this.documentObjectStorage =
        Objects.requireNonNull(documentObjectStorage, "documentObjectStorage must not be null");
    this.documentAssetStorage =
        Objects.requireNonNull(documentAssetStorage, "documentAssetStorage must not be null");
    this.documentParser = Objects.requireNonNull(documentParser, "documentParser must not be null");
    this.languageResolver =
        Objects.requireNonNull(languageResolver, "languageResolver must not be null");
    this.languagePolicy = Objects.requireNonNull(languagePolicy, "languagePolicy must not be null");
    this.chunker = Objects.requireNonNull(chunker, "chunker must not be null");
    this.completeUseCase =
        Objects.requireNonNull(completeUseCase, "completeUseCase must not be null");
    this.failUseCase = Objects.requireNonNull(failUseCase, "failUseCase must not be null");
    this.workerProperties =
        Objects.requireNonNull(workerProperties, "workerProperties must not be null");
    this.queueProperties =
        Objects.requireNonNull(queueProperties, "queueProperties must not be null");
    this.sqsClient = sqsClient;
    this.documentStorageRoot =
        Objects.requireNonNull(documentStorageRoot, "documentStorageRoot must not be null");
    this.meterRegistry = meterRegistry;
  }

  public ImportExecutionOutcome processImport(UUID importJobId, String receiptHandle) {
    Objects.requireNonNull(importJobId, "importJobId must not be null");

    String workerId = workerProperties.workerId();
    Duration leaseDuration = workerProperties.leaseDuration();

    // 1. Claim job atomically in DB with fencing
    WorkerClaim claim = importJobs.claim(importJobId, workerId, leaseDuration);
    LOG.debug("Claim result for job {}: {}", importJobId, claim.result());

    switch (claim.result()) {
      case ALREADY_COMPLETED -> {
        LOG.info("Job {} already COMPLETED. Deleting duplicate SQS message.", importJobId);
        incrementMetric("document.import.worker.duplicate");
        deleteSqsMessageQuietly(receiptHandle);
        return new ImportExecutionOutcome(
            OutcomeType.ALREADY_COMPLETED, "Job already completed", importJobId);
      }
      case FINAL_FAILED, ABORTED -> {
        LOG.info(
            "Job {} in terminal state {}. Deleting stale SQS message.",
            importJobId,
            claim.result());
        incrementMetric("document.import.worker.duplicate");
        deleteSqsMessageQuietly(receiptHandle);
        return new ImportExecutionOutcome(
            OutcomeType.TERMINAL_FAILED, "Job in terminal state " + claim.result(), importJobId);
      }
      case ACTIVE_BY_OTHER_WORKER -> {
        LOG.warn(
            "Job {} is currently actively leased by another worker. Leaving message for visibility timeout redelivery.",
            importJobId);
        return new ImportExecutionOutcome(
            OutcomeType.ACTIVE_BY_OTHER_WORKER, "Leased by other worker", importJobId);
      }
      case RETRY_LIMIT_EXCEEDED -> {
        LOG.error("Job {} has exceeded max retry attempts. Marking terminal FAILED.", importJobId);
        incrementMetric("document.import.worker.failed");
        failUseCase.failExceededRetries(
            importJobId, "MAX_ATTEMPTS_EXCEEDED", "Job has exceeded max retry attempts");
        deleteSqsMessageQuietly(receiptHandle);
        return new ImportExecutionOutcome(
            OutcomeType.TERMINAL_FAILED, "Max retry attempts exceeded", importJobId);
      }
      case NOT_FOUND -> {
        LOG.error(
            "Job {} not found in database. Leaving message for SQS DLQ redrive.", importJobId);
        return new ImportExecutionOutcome(
            OutcomeType.NOT_FOUND, "Job not found in DB", importJobId);
      }
      case ACQUIRED -> {
        // Proceed with import execution
      }
    }

    ImportJob job =
        claim
            .job()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Claim acquired but job was missing for " + importJobId));
    UUID leaseToken = claim.leaseToken();
    UUID documentId = job.documentId();

    ImportedDocument document =
        importedDocuments
            .findDocumentById(documentId)
            .orElseThrow(
                () ->
                    new IllegalStateException("Document not found for import job: " + documentId));

    // 2. Start periodic Heartbeat coordinator (DB renew + SQS change visibility)
    try (DocumentImportHeartbeatCoordinator heartbeat =
        createHeartbeatCoordinator(job.id(), leaseToken, workerId, receiptHandle, leaseDuration)) {
      heartbeat.start();

      return executeDurableImport(job, document, leaseToken, workerId, receiptHandle, heartbeat);
    }
  }

  private ImportExecutionOutcome executeDurableImport(
      ImportJob job,
      ImportedDocument document,
      UUID leaseToken,
      String workerId,
      String receiptHandle,
      DocumentImportHeartbeatCoordinator heartbeat) {
    long startTime = System.currentTimeMillis();
    DocumentFormat format = document.format();
    String extension = format == DocumentFormat.PDF ? ".pdf" : ".epub";
    Path stagingDir = documentStorageRoot.resolve("staging");
    Path tempFile = null;

    try {
      Files.createDirectories(stagingDir);
      tempFile = Files.createTempFile(stagingDir, "worker-source-" + job.id() + "-", extension);

      // 3. Download / copy source OUTSIDE DB transaction
      downloadSource(job, tempFile);

      // 4. Parse source OUTSIDE DB transaction
      ParsedDocument parsed = documentParser.parse(tempFile, format);

      // 5. Heartbeat ownership check
      heartbeat.checkOwnership();

      // 6. Language resolution and policy check OUTSIDE DB transaction
      String effectiveLanguage =
          languageResolver.resolve(job.languageOverride(), parsed.declaredLanguage());
      languagePolicy.requireSupported(effectiveLanguage);

      // 7. Store cover if present
      String coverAssetKey = null;
      if (parsed.cover() != null) {
        coverAssetKey =
            documentAssetStorage.storeCover(
                document.ownerId(),
                document.id(),
                parsed.cover().mediaType(),
                parsed.cover().bytes());
      }

      // 8. Chunk sections with DocumentChunker V5 OUTSIDE DB transaction
      List<DocumentSection> sections = new ArrayList<>();
      List<DocumentUnit> units = new ArrayList<>();
      int globalOrdinal = 1;

      for (int i = 0; i < parsed.sections().size(); i++) {
        var parsedSection = parsed.sections().get(i);
        UUID sectionId = UUID.randomUUID();
        DocumentSection section =
            new DocumentSection(
                sectionId,
                document.id(),
                i + 1,
                parsedSection.title(),
                parsedSection.sourceLocator());
        sections.add(section);

        List<DocumentChunk> chunks = chunker.chunk(parsedSection.blocks(), effectiveLanguage);
        for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
          DocumentChunk chunk = chunks.get(chunkIndex);
          byte[] contentBytes = chunk.content().getBytes(StandardCharsets.UTF_8);
          String contentHash = sha256Hex(contentBytes);
          units.add(
              new DocumentUnit(
                  UUID.randomUUID(),
                  document.id(),
                  sectionId,
                  globalOrdinal++,
                  chunkIndex + 1,
                  DocumentUnitKind.LOGICAL_CHUNK,
                  chunk.content(),
                  chunk.wordCount(),
                  parsedSection.sourceLocator(),
                  contentHash));
        }
      }

      if (units.isEmpty()) {
        DocumentImportException.Reason emptyReason =
            format == DocumentFormat.PDF
                ? DocumentImportException.Reason.PDF_SCANNED_NOT_SUPPORTED
                : DocumentImportException.Reason.INVALID_EPUB;
        throw new DocumentImportException(emptyReason, "Document contains no readable content");
      }

      // 9. Heartbeat check before atomic persistence
      heartbeat.checkOwnership();

      // 10. Atomic persistence in TX (sections + units + ready document + completed job)
      String title =
          (parsed.title() != null && !parsed.title().isBlank()) ? parsed.title() : document.title();
      String author = parsed.author() != null ? parsed.author() : document.author();

      CompleteDocumentImportCommand command =
          new CompleteDocumentImportCommand(
              job.id(),
              leaseToken,
              workerId,
              sections,
              units,
              title,
              author,
              effectiveLanguage,
              coverAssetKey);

      boolean completed = completeUseCase.completeImport(command);
      if (!completed) {
        throw new IllegalStateException(
            "Failed to complete document import in database for job " + job.id());
      }

      long duration = System.currentTimeMillis() - startTime;
      String eventId = org.slf4j.MDC.get("eventId");
      LOG.info(
          "document_import_worker_completed eventId={} jobId={} documentId={} workerId={} attempt={} durationMs={}",
          eventId != null ? eventId : "null",
          job.id(),
          document.id(),
          workerId,
          job.attemptCount(),
          duration);

      incrementMetric("document.import.worker.completed");

      // 11. Delete SQS message ONLY AFTER DB commit
      deleteSqsMessageQuietly(receiptHandle);

      return new ImportExecutionOutcome(
          OutcomeType.COMPLETED, "Import completed successfully", job.id());

    } catch (JobLeaseLostException e) {
      LOG.warn(
          "Worker {} lost ownership lease during processing for job {}: {}",
          workerId,
          job.id(),
          e.getMessage());
      incrementMetric("document.import.worker.heartbeat.failed");
      return new ImportExecutionOutcome(OutcomeType.LEASE_LOST, e.getMessage(), job.id());

    } catch (DocumentImportException e) {
      LOG.warn(
          "Permanent domain failure importing job {}: [{}] {}",
          job.id(),
          e.reason(),
          e.getMessage());
      handlePermanentFailure(
          job, leaseToken, workerId, receiptHandle, e.reason().name(), e.getMessage());
      return new ImportExecutionOutcome(OutcomeType.TERMINAL_FAILED, e.getMessage(), job.id());

    } catch (StorageObjectNotFoundException e) {
      LOG.warn("Source object not found in storage for job {}: {}", job.id(), e.getMessage());
      handlePermanentFailure(
          job, leaseToken, workerId, receiptHandle, "SOURCE_NOT_FOUND", e.getMessage());
      return new ImportExecutionOutcome(OutcomeType.TERMINAL_FAILED, e.getMessage(), job.id());

    } catch (TransientStorageException | IOException | SdkClientException e) {
      LOG.warn("Transient failure importing job {}: {}", job.id(), e.getMessage());
      return handleTransientFailure(job, leaseToken, workerId, e);

    } catch (Exception e) {
      LOG.error("Unexpected error importing job {}: {}", job.id(), e.getMessage(), e);
      if (job.attemptCount() >= job.maxAttempts()) {
        handlePermanentFailure(
            job, leaseToken, workerId, receiptHandle, "UNEXPECTED_FAILURE", e.getMessage());
        return new ImportExecutionOutcome(OutcomeType.TERMINAL_FAILED, e.getMessage(), job.id());
      }
      return handleTransientFailure(job, leaseToken, workerId, e);

    } finally {
      if (tempFile != null) {
        try {
          Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
          // Best effort staging cleanup
        }
      }
    }
  }

  private void downloadSource(ImportJob job, Path destination) throws IOException {
    if (job.storageProvider() == StorageProvider.S3) {
      documentObjectStorage.downloadObject(job.sourceAssetKey(), destination);
    } else {
      Path localSource = documentAssetStorage.locate(job.sourceAssetKey());
      Files.copy(localSource, destination, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void handlePermanentFailure(
      ImportJob job,
      UUID leaseToken,
      String workerId,
      String receiptHandle,
      String errorCode,
      String errorMessage) {
    incrementMetric("document.import.worker.failed");
    try {
      failUseCase.failFinal(job.id(), leaseToken, workerId, errorCode, errorMessage);
    } catch (Exception e) {
      LOG.warn("Failed to mark job {} as permanently failed: {}", job.id(), e.getMessage());
    }
    deleteSqsMessageQuietly(receiptHandle);
  }

  private ImportExecutionOutcome handleTransientFailure(
      ImportJob job, UUID leaseToken, String workerId, Exception exception) {
    if (job.attemptCount() >= job.maxAttempts()) {
      LOG.error(
          "Job {} reached max attempts ({}/{}) after transient failure. Marking terminal FAILED.",
          job.id(),
          job.attemptCount(),
          job.maxAttempts());
      incrementMetric("document.import.worker.failed");
      failUseCase.failFinal(
          job.id(),
          leaseToken,
          workerId,
          "MAX_ATTEMPTS_EXCEEDED",
          "Max attempts reached: " + exception.getMessage());
      return new ImportExecutionOutcome(
          OutcomeType.TERMINAL_FAILED, exception.getMessage(), job.id());
    }

    try {
      importJobs.releaseForRetry(
          job.id(),
          leaseToken,
          workerId,
          workerProperties.errorBackoff(),
          "TRANSIENT_FAILURE",
          exception.getMessage());
    } catch (Exception e) {
      LOG.warn("Failed to release job {} for retry: {}", job.id(), e.getMessage());
    }

    return new ImportExecutionOutcome(
        OutcomeType.RETRY_SCHEDULED, "Scheduled for retry: " + exception.getMessage(), job.id());
  }

  public void deleteSqsMessageQuietly(String receiptHandle) {
    if (sqsClient == null
        || queueProperties.url() == null
        || queueProperties.url().isBlank()
        || receiptHandle == null) {
      return;
    }
    try {
      sqsClient.deleteMessage(
          DeleteMessageRequest.builder()
              .queueUrl(queueProperties.url())
              .receiptHandle(receiptHandle)
              .build());
      LOG.debug("Deleted SQS message with receiptHandle {}", receiptHandle);
    } catch (Exception e) {
      LOG.warn(
          "Failed to delete SQS message (receiptHandle={}): {}. DB is already committed; redelivery will be handled idempotently.",
          receiptHandle,
          e.getMessage());
    }
  }

  protected DocumentImportHeartbeatCoordinator createHeartbeatCoordinator(
      UUID jobId, UUID leaseToken, String workerId, String receiptHandle, Duration leaseDuration) {
    return new DocumentImportHeartbeatCoordinator(
        importJobs,
        sqsClient,
        queueProperties.url(),
        jobId,
        leaseToken,
        workerId,
        receiptHandle,
        leaseDuration,
        workerProperties.heartbeatInterval());
  }

  private void incrementMetric(String metricName) {
    if (meterRegistry != null) {
      try {
        meterRegistry.counter(metricName).increment();
      } catch (Exception ignored) {
      }
    }
  }

  private String sha256Hex(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes)).toLowerCase(Locale.ROOT);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
