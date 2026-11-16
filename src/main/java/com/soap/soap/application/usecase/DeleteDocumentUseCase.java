package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.DocumentNotFoundException;
import com.soap.soap.application.exception.DocumentStillProcessingException;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.DocumentAssetStoragePort;
import com.soap.soap.application.port.out.DocumentObjectStoragePort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.domain.model.StorageProvider;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Deletes a document owned by the current user together with its stored source (BACK-003).
 *
 * <p>There is no distributed transaction between PostgreSQL and S3, so the order is chosen to avoid
 * <em>silent</em> inconsistency:
 *
 * <ol>
 *   <li>S3 source first. DeleteObject is idempotent (an absent object counts as deleted). If it
 *       fails, nothing is removed from the database and the client gets a retryable storage error;
 *       the user simply deletes again.
 *   <li>Database row second. If this fails after the S3 delete, the document is still listed, the
 *       client gets an error and a retry succeeds because step 1 is idempotent. The user asked for
 *       the deletion, so the transiently missing source is not a data-loss risk.
 *   <li>Filesystem assets (legacy source, covers) last and best-effort, as before: anything left
 *       behind is removed by {@code DocumentAssetReconciliationJob}, which deletes unreferenced
 *       local files.
 * </ol>
 *
 * The reverse order (database first) would leave an orphaned private S3 object with nothing in the
 * database pointing at it, which is exactly the leak BACK-003 describes.
 */
@Component
@RequiredArgsConstructor
public class DeleteDocumentUseCase {
  private static final Logger LOG = LoggerFactory.getLogger(DeleteDocumentUseCase.class);
  private final CurrentUserPort currentUser;
  private final ImportedDocumentRepositoryPort documents;
  private final DocumentAssetStoragePort storage;
  private final DocumentObjectStoragePort objectStorage;
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

    if (document.sourceStorageProvider() == StorageProvider.S3) {
      deleteS3Source(document);
    }
    documents.deleteDocument(document.id());
    if (document.sourceStorageProvider() == StorageProvider.FILESYSTEM) {
      cleanup(document.sourceAssetKey(), "source", document.id(), userId);
    }
    cleanup(document.coverAssetKey(), "cover", document.id(), userId);
    meters
        .counter("documents.deletes", "outcome", "deleted", "format", document.format().name())
        .increment();
    LOG.info(
        "document_delete_completed documentId={} userId={} format={} sourceProvider={}",
        document.id(),
        userId,
        document.format(),
        document.sourceStorageProvider());
  }

  /**
   * Deletes exactly this document's S3 source object. Keys are generated server-side as {@code
   * documents/{ownerId}/{documentId}/source.<ext>}; any other key is refused so a corrupted row can
   * never make us delete another user's object.
   */
  private void deleteS3Source(ImportedDocument document) {
    var key = document.sourceAssetKey();
    if (key == null) return;
    var ownedPrefix = "documents/" + document.ownerId() + "/" + document.id() + "/";
    if (!key.startsWith(ownedPrefix) || key.contains("..")) {
      meters.counter("document.cleanup.failure", "type", "source_key_not_owned").increment();
      LOG.error(
          "document_source_delete_skipped documentId={} userId={} reason=key_outside_document_prefix"
              + " objectKey={}",
          document.id(),
          document.ownerId(),
          key);
      return;
    }
    try {
      objectStorage.deleteObject(key);
    } catch (RuntimeException exception) {
      meters.counter("document.cleanup.failure", "type", "document_source").increment();
      LOG.warn(
          "document_source_delete_failed documentId={} userId={} objectKey={} reason={}",
          document.id(),
          document.ownerId(),
          key,
          exception.getClass().getSimpleName());
      throw exception;
    }
    meters.counter("document.cleanup.deleted", "kind", "source", "provider", "S3").increment();
    LOG.info(
        "document_source_delete_completed documentId={} userId={} provider=S3 objectKey={}",
        document.id(),
        document.ownerId(),
        key);
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
