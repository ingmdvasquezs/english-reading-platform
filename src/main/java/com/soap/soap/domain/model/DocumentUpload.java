package com.soap.soap.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record DocumentUpload(
    UUID id,
    UUID documentId,
    UUID userId,
    String originalFilename,
    DocumentFormat format,
    String contentType,
    long expectedSizeBytes,
    String expectedChecksumSha256,
    String storageKey,
    StorageProvider storageProvider,
    DocumentUploadStatus status,
    LocalDateTime expiresAt,
    LocalDateTime confirmedAt,
    UUID confirmedJobId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    long version) {

  public DocumentUpload {
    Objects.requireNonNull(documentId, "documentId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(originalFilename, "originalFilename must not be null");
    Objects.requireNonNull(format, "format must not be null");
    Objects.requireNonNull(contentType, "contentType must not be null");
    if (expectedSizeBytes < 0) {
      throw new IllegalArgumentException("expectedSizeBytes must not be negative");
    }
    Objects.requireNonNull(storageKey, "storageKey must not be null");
    Objects.requireNonNull(storageProvider, "storageProvider must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    if (updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("updatedAt must not precede createdAt");
    }
  }

  public DocumentUpload(
      UUID id,
      UUID documentId,
      UUID userId,
      String originalFilename,
      DocumentFormat format,
      String contentType,
      long expectedSizeBytes,
      String expectedChecksumSha256,
      String storageKey,
      DocumentUploadStatus status,
      LocalDateTime expiresAt,
      LocalDateTime confirmedAt,
      UUID confirmedJobId,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      long version) {
    this(
        id,
        documentId,
        userId,
        originalFilename,
        format,
        contentType,
        expectedSizeBytes,
        expectedChecksumSha256,
        storageKey,
        StorageProvider.S3,
        status,
        expiresAt,
        confirmedAt,
        confirmedJobId,
        createdAt,
        updatedAt,
        version);
  }

  public boolean isExpired(LocalDateTime now) {
    return status == DocumentUploadStatus.EXPIRED
        || (status == DocumentUploadStatus.PENDING && !expiresAt.isAfter(now));
  }

  public boolean isConfirmed() {
    return status == DocumentUploadStatus.CONFIRMED;
  }

  public boolean isAborted() {
    return status == DocumentUploadStatus.ABORTED;
  }
}
