package com.soap.soap.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record OutboxEvent(
    UUID id,
    String aggregateType,
    UUID aggregateId,
    String eventType,
    String payload,
    OutboxEventStatus status,
    int attemptCount,
    int maxAttempts,
    LocalDateTime nextAttemptAt,
    String lockedBy,
    LocalDateTime lockedUntil,
    LocalDateTime publishedAt,
    String lastError,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    long version) {

  public OutboxEvent {
    Objects.requireNonNull(aggregateType, "aggregateType must not be null");
    Objects.requireNonNull(aggregateId, "aggregateId must not be null");
    Objects.requireNonNull(eventType, "eventType must not be null");
    Objects.requireNonNull(payload, "payload must not be null");
    Objects.requireNonNull(status, "status must not be null");
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

  public boolean isLocked(LocalDateTime now) {
    return status == OutboxEventStatus.SENDING && lockedUntil != null && lockedUntil.isAfter(now);
  }
}
