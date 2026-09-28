package com.soap.soap.application.usecase;

import com.soap.soap.application.port.out.ImportJobRepositoryPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.ImportJob;
import com.soap.soap.domain.model.ImportedDocument;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FailDocumentImportUseCase {

  private final ImportJobRepositoryPort importJobs;
  private final ImportedDocumentRepositoryPort importedDocuments;

  @Transactional
  public boolean failFinal(
      UUID jobId, UUID leaseToken, String workerId, String errorCode, String errorMessage) {
    Objects.requireNonNull(jobId, "jobId must not be null");
    Objects.requireNonNull(leaseToken, "leaseToken must not be null");
    Objects.requireNonNull(workerId, "workerId must not be null");

    ImportJob job =
        importJobs
            .findByIdAndLock(jobId)
            .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));

    boolean jobFailed = importJobs.failFinal(jobId, leaseToken, workerId, errorCode, errorMessage);
    if (!jobFailed) {
      return false;
    }

    importedDocuments
        .findDocumentById(job.documentId())
        .ifPresent(
            doc -> {
              LocalDateTime now = LocalDateTime.now();
              String failureReason = sanitizeDocumentFailureReason(errorCode);
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
                      failureReason,
                      doc.chunkingVersion(),
                      doc.createdAt(),
                      now);
              importedDocuments.saveDocument(failedDoc);
            });

    return true;
  }

  @Transactional
  public boolean failExceededRetries(UUID jobId, String errorCode, String errorMessage) {
    Objects.requireNonNull(jobId, "jobId must not be null");

    ImportJob job =
        importJobs
            .findByIdAndLock(jobId)
            .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));

    if (job.status() == com.soap.soap.domain.model.ImportJobStatus.FAILED
        || job.status() == com.soap.soap.domain.model.ImportJobStatus.ABORTED) {
      return true;
    }

    boolean jobFailed = importJobs.failExceededRetries(jobId, errorCode, errorMessage);
    if (!jobFailed && job.status() != com.soap.soap.domain.model.ImportJobStatus.FAILED) {
      return false;
    }

    importedDocuments
        .findDocumentById(job.documentId())
        .ifPresent(
            doc -> {
              LocalDateTime now = LocalDateTime.now();
              String failureReason = sanitizeDocumentFailureReason(errorCode);
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
                      failureReason,
                      doc.chunkingVersion(),
                      doc.createdAt(),
                      now);
              importedDocuments.saveDocument(failedDoc);
            });

    return true;
  }

  private String sanitizeDocumentFailureReason(String errorCode) {
    if (errorCode == null || errorCode.isBlank()) {
      return "IMPORT_FAILURE";
    }
    try {
      return com.soap.soap.application.exception.DocumentImportException.Reason.valueOf(errorCode)
          .name();
    } catch (IllegalArgumentException e) {
      return "IMPORT_FAILURE";
    }
  }
}
