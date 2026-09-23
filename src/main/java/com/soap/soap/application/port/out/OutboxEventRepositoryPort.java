package com.soap.soap.application.port.out;

import com.soap.soap.domain.model.OutboxEvent;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepositoryPort {
  OutboxEvent save(OutboxEvent event);

  Optional<OutboxEvent> findById(UUID eventId);

  List<OutboxEvent> claimBatch(String dispatcherId, int batchSize, Duration lockDuration);

  boolean markPublished(UUID eventId, String dispatcherId);

  boolean markFailedAttempt(UUID eventId, String dispatcherId, String error, Duration retryBackoff);
}
