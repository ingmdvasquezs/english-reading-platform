package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.domain.model.ImportJobStatus;
import com.soap.soap.infrastructure.persistence.entity.ImportJobEntity;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaImportJobRepository extends JpaRepository<ImportJobEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT j FROM ImportJobEntity j WHERE j.id = :id")
  Optional<ImportJobEntity> findByIdAndLock(@Param("id") UUID id);

  Optional<ImportJobEntity> findFirstByDocumentIdAndStatusIn(
      UUID documentId, Collection<ImportJobStatus> statuses);

  Optional<ImportJobEntity> findFirstByDocumentIdOrderByCreatedAtDesc(UUID documentId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      UPDATE ImportJobEntity j
      SET j.status = :newStatus,
          j.workerId = :workerId,
          j.leaseToken = :leaseToken,
          j.leaseUntil = :leaseUntil,
          j.heartbeatAt = :now,
          j.startedAt = COALESCE(j.startedAt, :now),
          j.attemptCount = j.attemptCount + 1,
          j.updatedAt = :now,
          j.version = j.version + 1
      WHERE j.id = :jobId
        AND j.attemptCount < j.maxAttempts
        AND (
          (j.status = com.soap.soap.domain.model.ImportJobStatus.PENDING AND (j.nextAttemptAt IS NULL OR j.nextAttemptAt <= :now))
          OR (j.status = com.soap.soap.domain.model.ImportJobStatus.PROCESSING AND (j.leaseUntil IS NULL OR j.leaseUntil <= :now))
        )
      """)
  int claimJob(
      @Param("jobId") UUID jobId,
      @Param("workerId") String workerId,
      @Param("leaseToken") UUID leaseToken,
      @Param("newStatus") ImportJobStatus newStatus,
      @Param("leaseUntil") LocalDateTime leaseUntil,
      @Param("now") LocalDateTime now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      UPDATE ImportJobEntity j
      SET j.leaseUntil = :newLeaseUntil,
          j.heartbeatAt = :now,
          j.updatedAt = :now,
          j.version = j.version + 1
      WHERE j.id = :jobId
        AND j.leaseToken = :leaseToken
        AND j.workerId = :workerId
        AND j.status = com.soap.soap.domain.model.ImportJobStatus.PROCESSING
        AND j.leaseUntil >= :now
      """)
  int renewLease(
      @Param("jobId") UUID jobId,
      @Param("leaseToken") UUID leaseToken,
      @Param("workerId") String workerId,
      @Param("newLeaseUntil") LocalDateTime newLeaseUntil,
      @Param("now") LocalDateTime now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      UPDATE ImportJobEntity j
      SET j.status = com.soap.soap.domain.model.ImportJobStatus.PENDING,
          j.workerId = NULL,
          j.leaseToken = NULL,
          j.leaseUntil = NULL,
          j.nextAttemptAt = :nextAttemptAt,
          j.lastErrorCode = :errorCode,
          j.lastErrorMessage = :errorMessage,
          j.updatedAt = :now,
          j.version = j.version + 1
      WHERE j.id = :jobId
        AND j.leaseToken = :leaseToken
        AND j.workerId = :workerId
        AND j.status = com.soap.soap.domain.model.ImportJobStatus.PROCESSING
        AND j.attemptCount < j.maxAttempts
      """)
  int releaseForRetry(
      @Param("jobId") UUID jobId,
      @Param("leaseToken") UUID leaseToken,
      @Param("workerId") String workerId,
      @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
      @Param("errorCode") String errorCode,
      @Param("errorMessage") String errorMessage,
      @Param("now") LocalDateTime now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      UPDATE ImportJobEntity j
      SET j.status = com.soap.soap.domain.model.ImportJobStatus.COMPLETED,
          j.finishedAt = :now,
          j.updatedAt = :now,
          j.version = j.version + 1
      WHERE j.id = :jobId
        AND j.leaseToken = :leaseToken
        AND j.workerId = :workerId
        AND j.status = com.soap.soap.domain.model.ImportJobStatus.PROCESSING
        AND j.leaseUntil >= :now
      """)
  int completeJob(
      @Param("jobId") UUID jobId,
      @Param("leaseToken") UUID leaseToken,
      @Param("workerId") String workerId,
      @Param("now") LocalDateTime now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      UPDATE ImportJobEntity j
      SET j.status = com.soap.soap.domain.model.ImportJobStatus.FAILED,
          j.finishedAt = :now,
          j.lastErrorCode = :errorCode,
          j.lastErrorMessage = :errorMessage,
          j.updatedAt = :now,
          j.version = j.version + 1
      WHERE j.id = :jobId
        AND j.leaseToken = :leaseToken
        AND j.workerId = :workerId
        AND j.status = com.soap.soap.domain.model.ImportJobStatus.PROCESSING
      """)
  int failFinal(
      @Param("jobId") UUID jobId,
      @Param("leaseToken") UUID leaseToken,
      @Param("workerId") String workerId,
      @Param("errorCode") String errorCode,
      @Param("errorMessage") String errorMessage,
      @Param("now") LocalDateTime now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      UPDATE ImportJobEntity j
      SET j.status = com.soap.soap.domain.model.ImportJobStatus.ABORTED,
          j.finishedAt = :now,
          j.updatedAt = :now,
          j.version = j.version + 1
      WHERE j.id = :jobId
        AND j.status IN (com.soap.soap.domain.model.ImportJobStatus.PENDING, com.soap.soap.domain.model.ImportJobStatus.PROCESSING)
      """)
  int abortJob(@Param("jobId") UUID jobId, @Param("now") LocalDateTime now);
}
