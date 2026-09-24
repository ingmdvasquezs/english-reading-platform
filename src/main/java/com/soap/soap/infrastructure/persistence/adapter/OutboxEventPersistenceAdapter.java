package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.port.out.OutboxEventRepositoryPort;
import com.soap.soap.domain.model.OutboxEvent;
import com.soap.soap.infrastructure.persistence.mapper.OutboxEventEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaOutboxEventRepository;
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
public class OutboxEventPersistenceAdapter implements OutboxEventRepositoryPort {

  private final JpaOutboxEventRepository repository;
  private final OutboxEventEntityMapper mapper;

  @Override
  @Transactional
  public OutboxEvent save(OutboxEvent event) {
    var entity = mapper.toEntity(event);
    return mapper.toDomain(repository.saveAndFlush(entity));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<OutboxEvent> findById(UUID eventId) {
    return repository.findById(eventId).map(mapper::toDomain);
  }

  @Override
  @Transactional
  public List<OutboxEvent> claimBatch(String dispatcherId, int batchSize, Duration lockDuration) {
    if (batchSize <= 0 || lockDuration == null || dispatcherId == null) {
      return List.of();
    }
    LocalDateTime now = LocalDateTime.now();
    List<UUID> claimableIds = repository.findClaimableIds(now, batchSize);
    if (claimableIds.isEmpty()) {
      return List.of();
    }
    LocalDateTime lockedUntil = now.plus(lockDuration);
    repository.lockBatch(claimableIds, dispatcherId, lockedUntil, now);
    return repository.findByIdInOrderByCreatedAtAsc(claimableIds).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  @Transactional
  public boolean markPublished(UUID eventId, String dispatcherId) {
    if (eventId == null || dispatcherId == null) {
      return false;
    }
    LocalDateTime now = LocalDateTime.now();
    return repository.markPublished(eventId, dispatcherId, now) > 0;
  }

  @Override
  @Transactional
  public boolean markFailedAttempt(
      UUID eventId, String dispatcherId, String error, Duration retryBackoff) {
    if (eventId == null || dispatcherId == null) {
      return false;
    }
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime nextAttemptAt = retryBackoff != null ? now.plus(retryBackoff) : now;
    int retried = repository.markFailedRetry(eventId, dispatcherId, nextAttemptAt, error, now);
    if (retried > 0) {
      return true;
    }
    return repository.markFailedFinal(eventId, dispatcherId, error, now) > 0;
  }
}
