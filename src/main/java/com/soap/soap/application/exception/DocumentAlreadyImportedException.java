package com.soap.soap.application.exception;

import com.soap.soap.domain.model.DocumentImportStatus;
import java.util.UUID;

public class DocumentAlreadyImportedException extends RuntimeException {
  private final UUID existingDocumentId;
  private final DocumentImportStatus status;

  public DocumentAlreadyImportedException(UUID existingDocumentId, DocumentImportStatus status) {
    super("Document source was already imported");
    this.existingDocumentId = existingDocumentId;
    this.status = status;
  }

  public UUID existingDocumentId() {
    return existingDocumentId;
  }

  public DocumentImportStatus status() {
    return status;
  }
}
