package com.soap.soap.application.port.out;

import com.soap.soap.domain.model.ImportJob;
import com.soap.soap.domain.model.WorkerClaim;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface ImportJobRepositoryPort {
  ImportJob save(ImportJob job);

  Optional<ImportJob> findById(UUID jobId);

  Optional<ImportJob> findByIdAndLock(UUID jobId);

  Optional<ImportJob> findActiveByDocumentId(UUID documentId);

  Optional<ImportJob> findLatestByDocumentId(UUID documentId);

  WorkerClaim claim(UUID jobId, String workerId, Duration leaseDuration);

  boolean renewLease(UUID jobId, UUID leaseToken, String workerId, Duration additionalDuration);

  boolean releaseForRetry(
      UUID jobId,
      UUID leaseToken,
      String workerId,
      Duration backoff,
      String errorCode,
      String errorMessage);

  boolean complete(UUID jobId, UUID leaseToken, String workerId);

  boolean failFinal(
      UUID jobId, UUID leaseToken, String workerId, String errorCode, String errorMessage);

  boolean abort(UUID jobId);
}
