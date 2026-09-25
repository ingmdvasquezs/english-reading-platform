package com.soap.soap.application.usecase;

import com.soap.soap.application.command.CompleteDocumentImportCommand;
import com.soap.soap.application.port.out.ImportJobRepositoryPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.ImportJob;
import com.soap.soap.domain.model.ImportJobStatus;
import com.soap.soap.domain.model.ImportedDocument;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompleteDocumentImportUseCase {

  private final ImportJobRepositoryPort importJobs;
  private final ImportedDocumentRepositoryPort importedDocuments;
  private final Clock clock;

  @Transactional
  public boolean completeImport(CompleteDocumentImportCommand command) {
    Objects.requireNonNull(command, "command must not be null");

    ImportJob job =
        importJobs
            .findByIdAndLock(command.jobId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Cannot complete import: job not found: " + command.jobId()));

    LocalDateTime now = LocalDateTime.now(clock);

    if (job.status() != ImportJobStatus.PROCESSING) {
      throw new IllegalStateException(
          "Cannot complete import: job is not in PROCESSING state (was " + job.status() + ")");
    }
    if (job.leaseToken() == null || !job.leaseToken().equals(command.leaseToken())) {
      throw new IllegalStateException(
          "Cannot complete import: fencing lease token does not match active lease");
    }
    if (job.workerId() == null || !job.workerId().equals(command.workerId())) {
      throw new IllegalStateException(
          "Cannot complete import: worker ID does not match active worker");
    }
    if (job.leaseUntil() == null || job.leaseUntil().isBefore(now)) {
      throw new IllegalStateException("Cannot complete import: worker lease has expired");
    }

    ImportedDocument document =
        importedDocuments
            .findDocumentById(job.documentId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Cannot complete import: document not found: " + job.documentId()));

    if (document.importStatus() != DocumentImportStatus.PROCESSING) {
      throw new IllegalStateException(
          "Cannot complete import: document is not in PROCESSING state (was "
              + document.importStatus()
              + ")");
    }

    // 1. Atomically replace document structure (sections + units)
    importedDocuments.replaceDocumentStructure(
        job.documentId(), command.sections(), command.units());

    // 2. Mark document as READY
    String title =
        command.title() != null && !command.title().isBlank() ? command.title() : document.title();
    String author = command.author() != null ? command.author() : document.author();
    String language =
        command.language() != null && !command.language().isBlank()
            ? command.language()
            : document.language();
    String coverAssetKey =
        command.coverAssetKey() != null ? command.coverAssetKey() : document.coverAssetKey();

    ImportedDocument readyDocument =
        new ImportedDocument(
            document.id(),
            document.ownerId(),
            title,
            author,
            language,
            document.format(),
            coverAssetKey,
            document.sourceAssetKey(),
            document.originalFilename(),
            document.sourceSha256(),
            DocumentImportStatus.READY,
            null,
            document.chunkingVersion(),
            document.createdAt(),
            now);
    importedDocuments.saveDocument(readyDocument);

    // 3. Mark job as COMPLETED
    boolean jobCompleted = importJobs.complete(job.id(), command.leaseToken(), command.workerId());
    if (!jobCompleted) {
      throw new IllegalStateException(
          "Cannot complete import: conditional update on job completion failed");
    }

    return true;
  }
}
