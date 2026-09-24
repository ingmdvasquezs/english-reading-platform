package com.soap.soap.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record ImportJob(
    UUID id,
    UUID documentId,
    UUID userId,
    ImportJobStatus status,
    int attemptCount,
    int maxAttempts,
    String sourceAssetKey,
    StorageProvider storageProvider,
    String languageOverride,
    String workerId,
    UUID leaseToken,
    LocalDateTime leaseUntil,
    LocalDateTime nextAttemptAt,
    LocalDateTime heartbeatAt,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    String lastErrorCode,
    String lastErrorMessage,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    long version) {

  public ImportJob {
    Objects.requireNonNull(documentId, "documentId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(sourceAssetKey, "sourceAssetKey must not be null");
    Objects.requireNonNull(storageProvider, "storageProvider must not be null");
    if (attemptCount < 0) {
      throw new IllegalArgumentException("attemptCount must not be negative");
    }
    if (maxAttempts <= 0) {
      throw new IllegalArgumentException("maxAttempts must be positive");
    }
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    if (updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("updatedAt must not precede createdAt");
    }
  }

  public ImportJob(
      UUID id,
      UUID documentId,
      UUID userId,
      ImportJobStatus status,
      int attemptCount,
      int maxAttempts,
      String sourceAssetKey,
      String languageOverride,
      String workerId,
      UUID leaseToken,
      LocalDateTime leaseUntil,
      LocalDateTime nextAttemptAt,
      LocalDateTime heartbeatAt,
      LocalDateTime startedAt,
      LocalDateTime finishedAt,
      String lastErrorCode,
      String lastErrorMessage,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      long version) {
    this(
        id,
        documentId,
        userId,
        status,
        attemptCount,
        maxAttempts,
        sourceAssetKey,
        StorageProvider.FILESYSTEM,
        languageOverride,
        workerId,
        leaseToken,
        leaseUntil,
        nextAttemptAt,
        heartbeatAt,
        startedAt,
        finishedAt,
        lastErrorCode,
        lastErrorMessage,
        createdAt,
        updatedAt,
        version);
  }

  public boolean isProcessing() {
    return status == ImportJobStatus.PROCESSING;
  }

  public boolean isLeaseValid(LocalDateTime now) {
    return isProcessing() && leaseUntil != null && leaseUntil.isAfter(now);
  }

  public boolean isLeaseExpired(LocalDateTime now) {
    return isProcessing() && (leaseUntil == null || !leaseUntil.isAfter(now));
  }

  public boolean hasReachedMaxAttempts() {
    return attemptCount >= maxAttempts;
  }
}
