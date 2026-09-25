package com.soap.soap.application.usecase;

import com.soap.soap.application.command.ConfirmVerifiedDocumentUploadCommand;
import com.soap.soap.application.exception.DocumentAlreadyImportedException;
import com.soap.soap.application.exception.DuplicateActiveDocumentSourceException;
import com.soap.soap.application.exception.UploadAbortedException;
import com.soap.soap.application.exception.UploadExpiredException;
import com.soap.soap.application.exception.UploadNotFoundException;
import com.soap.soap.application.model.ConfirmVerifiedDocumentUploadResult;
import com.soap.soap.application.port.out.DocumentUploadRepositoryPort;
import com.soap.soap.application.port.out.ImportJobRepositoryPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.OutboxEventRepositoryPort;
import com.soap.soap.domain.model.DocumentImportRequestedEvent;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.DocumentUpload;
import com.soap.soap.domain.model.DocumentUploadStatus;
import com.soap.soap.domain.model.ImportJob;
import com.soap.soap.domain.model.ImportJobStatus;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.domain.model.OutboxEvent;
import com.soap.soap.domain.model.OutboxEventStatus;
import com.soap.soap.domain.model.StorageProvider;
import com.soap.soap.infrastructure.persistence.mapper.OutboxEventSerializer;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ConfirmVerifiedDocumentUploadUseCase {

  private static final String DEFAULT_SOURCE_SHA256 = "0".repeat(64);

  private final DocumentUploadRepositoryPort documentUploads;
  private final ImportedDocumentRepositoryPort importedDocuments;
  private final ImportJobRepositoryPort importJobs;
  private final OutboxEventRepositoryPort outboxEvents;
  private final OutboxEventSerializer outboxEventSerializer;
  private final PlatformTransactionManager transactionManager;

  @Transactional
  public ConfirmVerifiedDocumentUploadResult confirm(ConfirmVerifiedDocumentUploadCommand command) {
    return new TransactionTemplate(transactionManager).execute(status -> executeConfirm(command));
  }

  private ConfirmVerifiedDocumentUploadResult executeConfirm(
      ConfirmVerifiedDocumentUploadCommand command) {
    Objects.requireNonNull(command, "command must not be null");

    DocumentUpload upload =
        documentUploads
            .findByIdAndLock(command.uploadId())
            .filter(u -> u.userId().equals(command.userId()))
            .orElseThrow(() -> new UploadNotFoundException(command.uploadId()));

    if (upload.isAborted()) {
      throw new UploadAbortedException(upload.id());
    }

    if (upload.isConfirmed()) {
      return new ConfirmVerifiedDocumentUploadResult(
          upload.id(),
          upload.documentId(),
          upload.confirmedJobId(),
          DocumentImportStatus.PROCESSING);
    }

    LocalDateTime now = LocalDateTime.now();
    if (upload.isExpired(now)) {
      throw new UploadExpiredException(upload.id());
    }

    UUID jobId = UUID.randomUUID();
    String language =
        command.languageOverride() != null && !command.languageOverride().isBlank()
            ? command.languageOverride()
            : "en";

    String sha256 =
        command.verifiedChecksumSha256() != null && !command.verifiedChecksumSha256().isBlank()
            ? command.verifiedChecksumSha256().toLowerCase(Locale.ROOT)
            : (upload.expectedChecksumSha256() != null && !upload.expectedChecksumSha256().isBlank()
                ? upload.expectedChecksumSha256().toLowerCase(Locale.ROOT)
                : DEFAULT_SOURCE_SHA256);

    StorageProvider provider = command.storageProvider();
    if (provider == null) {
      provider = upload.storageProvider() != null ? upload.storageProvider() : StorageProvider.S3;
    }

    // 0. Pre-check deduplication constraint for user
    importedDocuments
        .findByOwnerAndSourceSha256AndStatusIn(
            upload.userId(),
            sha256,
            Set.of(DocumentImportStatus.PROCESSING, DocumentImportStatus.READY))
        .ifPresent(
            existing -> {
              throw new DocumentAlreadyImportedException(existing.id(), existing.importStatus());
            });

    // 1. Create or ensure ImportedDocument in PROCESSING status
    try {
      StorageProvider effectiveProvider = provider;
      if (importedDocuments.findDocumentById(upload.documentId()).isEmpty()) {
        importedDocuments.saveDocument(
            new ImportedDocument(
                upload.documentId(),
                upload.userId(),
                upload.originalFilename(),
                null,
                language,
                upload.format(),
                null,
                upload.storageKey(),
                effectiveProvider,
                upload.originalFilename(),
                sha256,
                DocumentImportStatus.PROCESSING,
                null,
                5,
                now,
                now));
      }
    } catch (DuplicateActiveDocumentSourceException exception) {
      var ex = new DocumentAlreadyImportedException(null, DocumentImportStatus.PROCESSING);
      ex.addSuppressed(exception);
      throw ex;
    }

    // 2. Create ImportJob in PENDING status
    ImportJob job =
        new ImportJob(
            jobId,
            upload.documentId(),
            upload.userId(),
            ImportJobStatus.PENDING,
            0,
            3,
            upload.storageKey(),
            provider,
            command.languageOverride(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            now,
            now,
            0);
    importJobs.save(job);

    // 3. Create OutboxEvent DOCUMENT_IMPORT_REQUESTED
    UUID eventId = UUID.randomUUID();
    DocumentImportRequestedEvent eventPayload =
        new DocumentImportRequestedEvent(
            eventId,
            1,
            jobId,
            upload.documentId(),
            upload.userId(),
            upload.format(),
            upload.storageKey(),
            command.languageOverride(),
            now);
    String serializedPayload = outboxEventSerializer.serializeDocumentImportRequested(eventPayload);

    OutboxEvent outboxEvent =
        new OutboxEvent(
            eventId,
            "IMPORT_JOB",
            jobId,
            "DOCUMENT_IMPORT_REQUESTED",
            serializedPayload,
            OutboxEventStatus.PENDING,
            0,
            5,
            null,
            null,
            null,
            null,
            null,
            now,
            now,
            0);
    outboxEvents.save(outboxEvent);

    // 4. Update DocumentUpload to CONFIRMED
    DocumentUpload confirmedUpload =
        new DocumentUpload(
            upload.id(),
            upload.documentId(),
            upload.userId(),
            upload.originalFilename(),
            upload.format(),
            upload.contentType(),
            upload.expectedSizeBytes(),
            upload.expectedChecksumSha256(),
            upload.storageKey(),
            provider,
            DocumentUploadStatus.CONFIRMED,
            upload.expiresAt(),
            now,
            jobId,
            upload.createdAt(),
            now,
            upload.version());
    documentUploads.save(confirmedUpload);

    return new ConfirmVerifiedDocumentUploadResult(
        upload.id(), upload.documentId(), jobId, DocumentImportStatus.PROCESSING);
  }
}
