package com.soap.soap.application.service;

import com.soap.soap.application.port.out.ImportJobRepositoryPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.OutboxEventRepositoryPort;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.ImportedDocument;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxDeliveryFailureService {

  private static final Logger log = LoggerFactory.getLogger(OutboxDeliveryFailureService.class);
  public static final String DEFAULT_FAILURE_CODE = "IMPORT_QUEUE_PUBLISH_FAILED";

  private final OutboxEventRepositoryPort outboxEvents;
  private final ImportJobRepositoryPort importJobs;
  private final ImportedDocumentRepositoryPort importedDocuments;

  public OutboxDeliveryFailureService(
      OutboxEventRepositoryPort outboxEvents,
      ImportJobRepositoryPort importJobs,
      ImportedDocumentRepositoryPort importedDocuments) {
    this.outboxEvents = Objects.requireNonNull(outboxEvents, "outboxEvents must not be null");
    this.importJobs = Objects.requireNonNull(importJobs, "importJobs must not be null");
    this.importedDocuments =
        Objects.requireNonNull(importedDocuments, "importedDocuments must not be null");
  }

  @Transactional
  public boolean handleTerminalFailure(
      UUID eventId, String dispatcherId, UUID importJobId, String sanitizedError) {
    if (eventId == null || dispatcherId == null) {
      return false;
    }

    // 1. Mark outbox event FAILED conditionally on valid lease
    boolean outboxMarked = outboxEvents.markFailedTerminal(eventId, dispatcherId, sanitizedError);
    if (!outboxMarked) {
      log.warn(
          "Could not mark outbox event [eventId={}] as FAILED; lease expired or reclaimed by another dispatcher",
          eventId);
      return false;
    }

    // 2. Mark import_job as FAILED in same transaction if in PENDING status
    if (importJobId != null) {
      boolean jobMarked = importJobs.failPending(importJobId, DEFAULT_FAILURE_CODE, sanitizedError);
      if (jobMarked) {
        importJobs
            .findById(importJobId)
            .ifPresent(
                job -> {
                  importedDocuments
                      .findDocumentById(job.documentId())
                      .filter(doc -> doc.importStatus() == DocumentImportStatus.PROCESSING)
                      .ifPresent(
                          doc -> {
                            LocalDateTime now = LocalDateTime.now();
                            ImportedDocument failedDoc =
                                new ImportedDocument(
                                    doc.id(),
                                    doc.ownerId(),
                                    doc.title(),
                                    doc.author(),
                                    doc.language(),
                                    doc.format(),
                                    doc.coverAssetKey(),
                                    doc.sourceAssetKey(),
                                    doc.originalFilename(),
                                    doc.sourceSha256(),
                                    DocumentImportStatus.FAILED,
                                    "IMPORT_FAILURE",
                                    doc.chunkingVersion(),
                                    doc.createdAt(),
                                    now);
                            importedDocuments.saveDocument(failedDoc);
                          });
                });
      } else {
        log.info(
            "Outbox event [eventId={}] marked FAILED, but import_job [jobId={}] was not in PENDING status "
                + "(already advanced/processed by worker). Preserving advanced job and document state.",
            eventId,
            importJobId);
      }
    }

    return true;
  }
}
