package com.soap.soap.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record DocumentProgress(
    UUID id,
    UUID userId,
    UUID documentId,
    UUID currentUnitId,
    DocumentProgressStatus status,
    LocalDateTime startedAt,
    LocalDateTime lastReadAt,
    LocalDateTime completedAt,
    Long version) {

  public DocumentProgress {
    Objects.requireNonNull(userId, "Document progress user id must not be null");
    Objects.requireNonNull(documentId, "Document progress document id must not be null");
    Objects.requireNonNull(currentUnitId, "Document progress current unit id must not be null");
    Objects.requireNonNull(status, "Document progress status must not be null");
    Objects.requireNonNull(startedAt, "Document progress startedAt must not be null");
    Objects.requireNonNull(lastReadAt, "Document progress lastReadAt must not be null");
    if (lastReadAt.isBefore(startedAt)) {
      throw new IllegalArgumentException("Document progress lastReadAt must not precede startedAt");
    }
    if (status == DocumentProgressStatus.IN_PROGRESS && completedAt != null) {
      throw new IllegalArgumentException("In-progress document must not have completedAt");
    }
    if (status == DocumentProgressStatus.COMPLETED && completedAt == null) {
      throw new IllegalArgumentException("Completed document must have completedAt");
    }
    if (completedAt != null && completedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException(
          "Document progress completedAt must not precede startedAt");
    }
  }

  public static DocumentProgress start(
      UUID userId, UUID documentId, UUID currentUnitId, LocalDateTime openedAt) {
    return new DocumentProgress(
        null,
        userId,
        documentId,
        currentUnitId,
        DocumentProgressStatus.IN_PROGRESS,
        openedAt,
        openedAt,
        null,
        null);
  }

  public DocumentProgress moveTo(UUID unitId, LocalDateTime readAt) {
    Objects.requireNonNull(unitId, "Document progress unit id must not be null");
    Objects.requireNonNull(readAt, "Document progress readAt must not be null");
    if (readAt.isBefore(lastReadAt)) {
      throw new IllegalArgumentException("Document progress readAt must not precede lastReadAt");
    }
    if (unitId.equals(currentUnitId) && readAt.equals(lastReadAt)) return this;
    return new DocumentProgress(
        id, userId, documentId, unitId, status, startedAt, readAt, completedAt, version);
  }

  public DocumentProgress complete(UUID lastUnitId, LocalDateTime completedAt) {
    Objects.requireNonNull(completedAt, "Document progress completedAt must not be null");
    if (status == DocumentProgressStatus.COMPLETED) return this;
    return new DocumentProgress(
        id,
        userId,
        documentId,
        lastUnitId,
        DocumentProgressStatus.COMPLETED,
        startedAt,
        completedAt,
        completedAt,
        version);
  }
}
