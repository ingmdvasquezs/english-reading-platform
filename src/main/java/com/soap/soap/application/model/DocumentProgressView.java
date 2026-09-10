package com.soap.soap.application.model;

import com.soap.soap.domain.model.DocumentProgressStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentProgressView(
    UUID documentId,
    UUID currentUnitId,
    String status,
    LocalDateTime startedAt,
    LocalDateTime lastReadAt,
    LocalDateTime completedAt,
    Long version) {
  public static DocumentProgressView notStarted(UUID documentId) {
    return new DocumentProgressView(documentId, null, "NOT_STARTED", null, null, null, null);
  }

  public static DocumentProgressView from(com.soap.soap.domain.model.DocumentProgress value) {
    DocumentProgressStatus status = value.status();
    return new DocumentProgressView(
        value.documentId(),
        value.currentUnitId(),
        status.name(),
        value.startedAt(),
        value.lastReadAt(),
        value.completedAt(),
        value.version());
  }
}
