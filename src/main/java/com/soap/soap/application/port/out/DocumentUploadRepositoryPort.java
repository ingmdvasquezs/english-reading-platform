package com.soap.soap.application.port.out;

import com.soap.soap.domain.model.DocumentUpload;
import java.util.Optional;
import java.util.UUID;

public interface DocumentUploadRepositoryPort {
  DocumentUpload save(DocumentUpload upload);

  Optional<DocumentUpload> findById(UUID uploadId);

  Optional<DocumentUpload> findByDocumentId(UUID documentId);

  Optional<DocumentUpload> findByIdAndLock(UUID uploadId);

  boolean abort(UUID uploadId, UUID userId);
}
