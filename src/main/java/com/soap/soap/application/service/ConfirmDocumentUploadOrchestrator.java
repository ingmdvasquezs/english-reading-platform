package com.soap.soap.application.service;

import com.soap.soap.application.command.ConfirmVerifiedDocumentUploadCommand;
import com.soap.soap.application.exception.StorageChecksumUnavailableException;
import com.soap.soap.application.exception.StorageObjectNotFoundException;
import com.soap.soap.application.exception.TransientStorageException;
import com.soap.soap.application.exception.UploadAbortedException;
import com.soap.soap.application.exception.UploadExpiredException;
import com.soap.soap.application.exception.UploadIntegrityMismatchException;
import com.soap.soap.application.exception.UploadNotCompletedException;
import com.soap.soap.application.exception.UploadNotFoundException;
import com.soap.soap.application.model.ConfirmVerifiedDocumentUploadResult;
import com.soap.soap.application.model.DocumentImportLimits;
import com.soap.soap.application.port.out.DocumentObjectStoragePort;
import com.soap.soap.application.port.out.DocumentUploadRepositoryPort;
import com.soap.soap.application.usecase.ConfirmVerifiedDocumentUploadUseCase;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.DocumentUpload;
import com.soap.soap.domain.model.StoredObjectAttributes;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConfirmDocumentUploadOrchestrator {

  private static final Logger LOG =
      LoggerFactory.getLogger(ConfirmDocumentUploadOrchestrator.class);

  private final DocumentUploadRepositoryPort documentUploads;
  private final DocumentObjectStoragePort storagePort;
  private final ConfirmVerifiedDocumentUploadUseCase confirmVerifiedUseCase;
  private final DocumentImportLimits limits;
  private final Clock clock;

  public ConfirmDocumentUploadOrchestrator(
      DocumentUploadRepositoryPort documentUploads,
      DocumentObjectStoragePort storagePort,
      ConfirmVerifiedDocumentUploadUseCase confirmVerifiedUseCase,
      DocumentImportLimits limits,
      Clock clock) {
    this.documentUploads = documentUploads;
    this.storagePort = storagePort;
    this.confirmVerifiedUseCase = confirmVerifiedUseCase;
    this.limits = limits;
    this.clock = clock;
  }

  public ConfirmVerifiedDocumentUploadResult confirmUpload(
      UUID uploadId, UUID userId, String languageOverride) {
    Objects.requireNonNull(uploadId, "uploadId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");

    // 1. Load upload intent without holding DB row lock
    DocumentUpload upload =
        documentUploads
            .findById(uploadId)
            .filter(u -> u.userId().equals(userId))
            .orElseThrow(() -> new UploadNotFoundException(uploadId));

    // 2. Return idempotent result if already confirmed
    if (upload.isConfirmed()) {
      return new ConfirmVerifiedDocumentUploadResult(
          upload.id(),
          upload.documentId(),
          upload.confirmedJobId(),
          DocumentImportStatus.PROCESSING);
    }

    if (upload.isAborted()) {
      throw new UploadAbortedException(uploadId);
    }

    LocalDateTime now = LocalDateTime.now(clock);
    if (upload.isExpired(now)) {
      throw new UploadExpiredException(uploadId);
    }

    // 3. Inspect S3 object outside of DB transaction
    StoredObjectAttributes attributes;
    try {
      attributes = storagePort.inspectObject(upload.storageKey());
    } catch (StorageObjectNotFoundException e) {
      LOG.info("Upload object not found in storage for uploadId={}", uploadId);
      throw new UploadNotCompletedException(uploadId);
    } catch (TransientStorageException e) {
      LOG.warn("Transient storage error inspecting uploadId={}: {}", uploadId, e.getMessage());
      throw e;
    }

    // 4. Verify checksum availability
    long actualSize = attributes.sizeBytes();
    String actualChecksum = attributes.checksumSha256();

    if (actualChecksum == null) {
      LOG.warn("Storage provider returned null checksum for uploadId={}", uploadId);
      throw new StorageChecksumUnavailableException(uploadId);
    }

    // 5. Verify size and checksum matching
    String mismatchReason = null;
    if (actualSize != upload.expectedSizeBytes()) {
      mismatchReason =
          "Actual size ("
              + actualSize
              + ") does not match expected size ("
              + upload.expectedSizeBytes()
              + ")";
    } else if (actualSize > limits.maxSourceBytes()) {
      mismatchReason =
          "Actual size (" + actualSize + ") exceeds maximum limit: " + limits.maxSourceBytes();
    } else if (!actualChecksum.equalsIgnoreCase(upload.expectedChecksumSha256())) {
      mismatchReason =
          "Actual checksum ("
              + actualChecksum
              + ") does not match expected checksum ("
              + upload.expectedChecksumSha256()
              + ")";
    }

    // 6. Handle permanent integrity mismatch
    if (mismatchReason != null) {
      LOG.error("Integrity mismatch on uploadId={}: {}", uploadId, mismatchReason);
      documentUploads.abort(uploadId, userId);
      storagePort.deleteObject(upload.storageKey());
      throw new UploadIntegrityMismatchException(uploadId, mismatchReason);
    }

    // 6. Build verified command and execute transactional confirmation
    ConfirmVerifiedDocumentUploadCommand command =
        new ConfirmVerifiedDocumentUploadCommand(
            upload.id(),
            upload.userId(),
            languageOverride,
            actualChecksum,
            actualSize,
            upload.storageProvider());

    return confirmVerifiedUseCase.confirm(command);
  }
}
