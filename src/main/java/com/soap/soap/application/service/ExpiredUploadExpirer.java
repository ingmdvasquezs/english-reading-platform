package com.soap.soap.application.service;

import com.soap.soap.application.port.out.DocumentObjectStoragePort;
import com.soap.soap.application.port.out.ExpiredUploadRepositoryPort;
import com.soap.soap.application.port.out.ExpiredUploadRepositoryPort.ExpiredUpload;
import com.soap.soap.domain.model.StorageProvider;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expires one upload intent in its own transaction.
 *
 * <p>The row is locked ({@code FOR UPDATE SKIP LOCKED}) and re-validated as PENDING and expired
 * before the object is deleted, so a concurrent confirmation or presign refresh either finishes
 * first (and the row is skipped) or waits and then sees EXPIRED. If the S3 delete fails the
 * transaction rolls back and the intent stays PENDING for the next run; if the commit fails after
 * the delete, the next run deletes again (idempotent) and commits. Running twice is harmless.
 */
@Component
@RequiredArgsConstructor
public class ExpiredUploadExpirer {
  private static final Logger LOG = LoggerFactory.getLogger(ExpiredUploadExpirer.class);

  private final ExpiredUploadRepositoryPort lifecycle;
  private final DocumentObjectStoragePort objectStorage;
  private final MeterRegistry meters;

  @Transactional
  public boolean expire(UUID uploadId, LocalDateTime expiredBefore, LocalDateTime now) {
    var locked = lifecycle.lockExpiredPendingUpload(uploadId, expiredBefore);
    if (locked.isEmpty()) return false;
    var upload = locked.get();
    if (upload.storageProvider() == StorageProvider.S3 && upload.storageKey() != null) {
      deleteOwnedObject(upload);
    }
    if (!lifecycle.markUploadExpired(uploadId, now)) return false;
    meters.counter("document.cleanup.expired_upload").increment();
    LOG.info(
        "document_cleanup type=expired_upload uploadId={} documentId={} userId={}"
            + " transition=PENDING->EXPIRED objectKey={}",
        upload.uploadId(),
        upload.documentId(),
        upload.userId(),
        upload.storageKey());
    return true;
  }

  private void deleteOwnedObject(ExpiredUpload upload) {
    var ownedPrefix = "documents/" + upload.userId() + "/" + upload.documentId() + "/";
    if (!upload.storageKey().startsWith(ownedPrefix) || upload.storageKey().contains("..")) {
      meters.counter("document.cleanup.failure", "type", "upload_key_not_owned").increment();
      LOG.error(
          "document_cleanup_skipped type=expired_upload uploadId={} reason=key_outside_upload_prefix"
              + " objectKey={}",
          upload.uploadId(),
          upload.storageKey());
      return;
    }
    objectStorage.deleteObject(upload.storageKey());
  }
}
