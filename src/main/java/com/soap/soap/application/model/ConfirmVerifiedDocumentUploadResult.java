package com.soap.soap.application.model;

import com.soap.soap.domain.model.DocumentImportStatus;
import java.util.Objects;
import java.util.UUID;

public record ConfirmVerifiedDocumentUploadResult(
    UUID uploadId, UUID documentId, UUID jobId, DocumentImportStatus status) {

  public ConfirmVerifiedDocumentUploadResult {
    Objects.requireNonNull(uploadId, "uploadId must not be null");
    Objects.requireNonNull(documentId, "documentId must not be null");
    Objects.requireNonNull(jobId, "jobId must not be null");
    Objects.requireNonNull(status, "status must not be null");
  }
}
