package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.port.out.ImportJobRepositoryPort;
import com.soap.soap.domain.model.ImportJob;
import com.soap.soap.domain.model.ImportJobStatus;
import com.soap.soap.domain.model.WorkerClaim;
import com.soap.soap.domain.model.WorkerClaimResult;
import com.soap.soap.infrastructure.persistence.mapper.ImportJobEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaImportJobRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ImportJobPersistenceAdapter implements ImportJobRepositoryPort {

  private final JpaImportJobRepository repository;
  private final ImportJobEntityMapper mapper;

  @Override
  @Transactional
  public ImportJob save(ImportJob job) {
    var entity = mapper.toEntity(job);
    return mapper.toDomain(repository.saveAndFlush(entity));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ImportJob> findById(UUID jobId) {
    return repository.findById(jobId).map(mapper::toDomain);
  }

  @Override
  @Transactional
  public Optional<ImportJob> findByIdAndLock(UUID jobId) {
    return repository.findByIdAndLock(jobId).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ImportJob> findActiveByDocumentId(UUID documentId) {
    return repository
        .findFirstByDocumentIdAndStatusIn(
            documentId, List.of(ImportJobStatus.PENDING, ImportJobStatus.PROCESSING))
        .map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ImportJob> findLatestByDocumentId(UUID documentId) {
    return repository.findFirstByDocumentIdOrderByCreatedAtDesc(documentId).map(mapper::toDomain);
  }

  @Override
  @Transactional
  public WorkerClaim claim(UUID jobId, String workerId, Duration leaseDuration) {
    LocalDateTime now = LocalDateTime.now();
    UUID leaseToken = UUID.randomUUID();
    LocalDateTime leaseUntil = now.plus(leaseDuration);

    int updatedRows =
        repository.claimJob(
            jobId, workerId, leaseToken, ImportJobStatus.PROCESSING, leaseUntil, now);

    if (updatedRows > 0) {
      return repository
          .findById(jobId)
          .map(updated -> WorkerClaim.acquired(mapper.toDomain(updated), leaseToken))
          .orElseGet(() -> WorkerClaim.rejected(WorkerClaimResult.NOT_FOUND));
    }

    // Claim failed: inspect current state from database to report the exact reason
    return repository
        .findById(jobId)
        .map(
            current -> {
              if (current.getStatus() == ImportJobStatus.COMPLETED) {
                return WorkerClaim.rejected(WorkerClaimResult.ALREADY_COMPLETED);
              }
              if (current.getStatus() == ImportJobStatus.FAILED) {
                return WorkerClaim.rejected(WorkerClaimResult.FINAL_FAILED);
              }
              if (current.getStatus() == ImportJobStatus.ABORTED) {
                return WorkerClaim.rejected(WorkerClaimResult.ABORTED);
              }
              if (current.getAttemptCount() >= current.getMaxAttempts()) {
                return WorkerClaim.rejected(WorkerClaimResult.RETRY_LIMIT_EXCEEDED);
              }
              return WorkerClaim.rejected(WorkerClaimResult.ACTIVE_BY_OTHER_WORKER);
            })
        .orElseGet(() -> WorkerClaim.rejected(WorkerClaimResult.NOT_FOUND));
  }

  @Override
  @Transactional
  public boolean renewLease(
      UUID jobId, UUID leaseToken, String workerId, Duration additionalDuration) {
    if (leaseToken == null || workerId == null) {
      return false;
    }
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime newLeaseUntil = now.plus(additionalDuration);
    return repository.renewLease(jobId, leaseToken, workerId, newLeaseUntil, now) > 0;
  }

  @Override
  @Transactional
  public boolean releaseForRetry(
      UUID jobId,
      UUID leaseToken,
      String workerId,
      Duration backoff,
      String errorCode,
      String errorMessage) {
    if (leaseToken == null || workerId == null) {
      return false;
    }
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime nextAttemptAt = now.plus(backoff);
    return repository.releaseForRetry(
            jobId, leaseToken, workerId, nextAttemptAt, errorCode, errorMessage, now)
        > 0;
  }

  @Override
  @Transactional
  public boolean complete(UUID jobId, UUID leaseToken, String workerId) {
    if (leaseToken == null || workerId == null) {
      return false;
    }
    LocalDateTime now = LocalDateTime.now();
    return repository.completeJob(jobId, leaseToken, workerId, now) > 0;
  }

  @Override
  @Transactional
  public boolean failFinal(
      UUID jobId, UUID leaseToken, String workerId, String errorCode, String errorMessage) {
    if (leaseToken == null || workerId == null) {
      return false;
    }
    LocalDateTime now = LocalDateTime.now();
    return repository.failFinal(jobId, leaseToken, workerId, errorCode, errorMessage, now) > 0;
  }

  @Override
  @Transactional
  public boolean abort(UUID jobId) {
    LocalDateTime now = LocalDateTime.now();
    return repository.abortJob(jobId, now) > 0;
  }
}
