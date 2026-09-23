package com.soap.soap.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record DocumentImportRequestedEvent(
    UUID eventId,
    int eventVersion,
    UUID jobId,
    UUID documentId,
    UUID userId,
    DocumentFormat format,
    String sourceAssetKey,
    String languageOverride,
    LocalDateTime requestedAt) {

  public DocumentImportRequestedEvent {
    Objects.requireNonNull(eventId, "eventId must not be null");
    if (eventVersion <= 0) {
      throw new IllegalArgumentException("eventVersion must be positive");
    }
    Objects.requireNonNull(jobId, "jobId must not be null");
    Objects.requireNonNull(documentId, "documentId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(format, "format must not be null");
    Objects.requireNonNull(sourceAssetKey, "sourceAssetKey must not be null");
    Objects.requireNonNull(requestedAt, "requestedAt must not be null");
  }
}
