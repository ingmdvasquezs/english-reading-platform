package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.DocumentNotFoundException;
import com.soap.soap.application.exception.DocumentStillProcessingException;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.DocumentAssetStoragePort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.domain.model.DocumentImportStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeleteDocumentUseCase {
  private static final Logger LOG = LoggerFactory.getLogger(DeleteDocumentUseCase.class);
  private final CurrentUserPort currentUser;
  private final ImportedDocumentRepositoryPort documents;
  private final DocumentAssetStoragePort storage;
  private final MeterRegistry meters;

  public void delete(UUID documentId) {
    var userId = currentUser.requireUserId();
    var document =
        documents
            .findDocumentById(documentId)
            .filter(value -> value.ownerId().equals(userId))
            .orElseThrow(() -> new DocumentNotFoundException(documentId));
    if (document.importStatus() == DocumentImportStatus.PROCESSING) {
      throw new DocumentStillProcessingException();
    }

    documents.deleteDocument(document.id());
    cleanup(document.sourceAssetKey(), "source", document.id(), userId);
    cleanup(document.coverAssetKey(), "cover", document.id(), userId);
    meters
        .counter("documents.deletes", "outcome", "deleted", "format", document.format().name())
        .increment();
    LOG.info(
        "document_delete_completed documentId={} userId={} format={}",
        document.id(),
        userId,
        document.format());
  }

  private void cleanup(String key, String kind, UUID documentId, UUID userId) {
    if (key == null) return;
    try {
      storage.delete(key);
      LOG.info(
          "document_asset_delete_completed documentId={} userId={} kind={}",
          documentId,
          userId,
          kind);
    } catch (RuntimeException exception) {
      meters.counter("documents.deletes", "outcome", "cleanup_failed", "kind", kind).increment();
      LOG.warn(
          "document_asset_delete_failed documentId={} userId={} kind={}", documentId, userId, kind);
    }
  }
}
