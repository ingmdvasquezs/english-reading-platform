package com.soap.soap.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record ReadingProgress(
    UUID id,
    UUID userId,
    UUID readingId,
    ReadingProgressStatus status,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    Integer currentPartOrdinal,
    Integer paginationVersion) {

  public ReadingProgress {
    Objects.requireNonNull(userId, "Reading progress user id must not be null");
    Objects.requireNonNull(readingId, "Reading progress reading id must not be null");
    Objects.requireNonNull(status, "Reading progress status must not be null");
    Objects.requireNonNull(startedAt, "Reading progress startedAt must not be null");
    if (status == ReadingProgressStatus.IN_PROGRESS && completedAt != null) {
      throw new IllegalArgumentException("In-progress reading must not have completedAt");
    }
    if (status == ReadingProgressStatus.COMPLETED && completedAt == null) {
      throw new IllegalArgumentException("Completed reading must have completedAt");
    }
    if ((currentPartOrdinal == null) != (paginationVersion == null)) {
      throw new IllegalArgumentException(
          "Part ordinal and pagination version must be provided together");
    }
    if (currentPartOrdinal != null && (currentPartOrdinal < 1 || paginationVersion < 1)) {
      throw new IllegalArgumentException("Part ordinal and pagination version must be positive");
    }
  }

  public ReadingProgress(
      UUID id,
      UUID userId,
      UUID readingId,
      ReadingProgressStatus status,
      LocalDateTime startedAt,
      LocalDateTime completedAt) {
    this(id, userId, readingId, status, startedAt, completedAt, null, null);
  }

  public static ReadingProgress inProgress(UUID userId, UUID readingId, LocalDateTime startedAt) {
    return new ReadingProgress(
        null, userId, readingId, ReadingProgressStatus.IN_PROGRESS, startedAt, null);
  }

  public static ReadingProgress completed(
      UUID userId, UUID readingId, LocalDateTime startedAt, LocalDateTime completedAt) {
    return new ReadingProgress(
        null, userId, readingId, ReadingProgressStatus.COMPLETED, startedAt, completedAt);
  }

  public ReadingProgress complete(LocalDateTime completedAt) {
    Objects.requireNonNull(completedAt, "Reading progress completedAt must not be null");
    return status == ReadingProgressStatus.COMPLETED
        ? this
        : new ReadingProgress(
            id,
            userId,
            readingId,
            ReadingProgressStatus.COMPLETED,
            startedAt,
            completedAt,
            currentPartOrdinal,
            paginationVersion);
  }

  public ReadingProgress withPosition(int partOrdinal, int version) {
    return new ReadingProgress(
        id, userId, readingId, status, startedAt, completedAt, partOrdinal, version);
  }
}
