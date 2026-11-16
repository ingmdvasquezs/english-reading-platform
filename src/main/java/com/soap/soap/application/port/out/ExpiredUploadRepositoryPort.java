package com.soap.soap.application.port.out;

import com.soap.soap.domain.model.StorageProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for cleaning up upload intents that were never confirmed (BACK-003). Transitions
 * re-validate their eligibility inside the statement, so a candidate whose state changed after the
 * scan is never overwritten.
 */
public interface ExpiredUploadRepositoryPort {

  /** PENDING upload intents whose expiry is older than {@code expiredBefore}, oldest first. */
  List<ExpiredUpload> findExpiredPendingUploads(LocalDateTime expiredBefore, int limit);

  /**
   * Locks one upload row ({@code FOR UPDATE SKIP LOCKED}) only if it is still PENDING and expired
   * before {@code expiredBefore}. Must run inside a transaction.
   */
  Optional<ExpiredUpload> lockExpiredPendingUpload(UUID uploadId, LocalDateTime expiredBefore);

  /** PENDING → EXPIRED. Returns false if the upload is no longer PENDING. */
  boolean markUploadExpired(UUID uploadId, LocalDateTime now);

  record ExpiredUpload(
      UUID uploadId,
      UUID userId,
      UUID documentId,
      String storageKey,
      StorageProvider storageProvider) {}
}
