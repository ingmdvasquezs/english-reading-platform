package com.soap.soap.application.port.out;

import com.soap.soap.domain.model.DocumentProgress;
import java.util.Optional;
import java.util.UUID;

public interface DocumentProgressRepositoryPort {
  DocumentProgress save(DocumentProgress progress);

  Optional<DocumentProgress> findByUserIdAndDocumentId(UUID userId, UUID documentId);
}
