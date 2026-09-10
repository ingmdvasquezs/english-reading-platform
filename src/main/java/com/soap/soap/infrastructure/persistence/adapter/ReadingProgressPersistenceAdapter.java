package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.model.ContinueReadingItem;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.infrastructure.persistence.entity.ReadingProgressEntity;
import com.soap.soap.infrastructure.persistence.repository.JpaReadingProgressRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReadingProgressPersistenceAdapter implements ReadingProgressRepositoryPort {
  private final JpaReadingProgressRepository repository;

  @Override
  @Transactional(readOnly = true)
  public Optional<ReadingProgress> findByUserIdAndReadingId(UUID userId, UUID readingId) {
    return repository.findForUserAndReading(userId, readingId).map(this::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, ReadingProgress> findByUserIdAndReadingIds(UUID userId, Set<UUID> readingIds) {
    if (readingIds.isEmpty()) {
      return Map.of();
    }
    return repository.findForUserAndReadingIds(userId, readingIds).stream()
        .map(this::toDomain)
        .collect(Collectors.toUnmodifiableMap(ReadingProgress::readingId, Function.identity()));
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<ContinueReadingItem> findInProgressReadings(
      UUID userId, PageRequest pageRequest) {
    var page =
        repository.findInProgressReadings(
            userId,
            org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size()));
    return new PageResult<>(
        page.getContent().stream()
            .map(
                item ->
                    new ContinueReadingItem(
                        item.getReadingId(),
                        item.getTitle(),
                        item.getOrigin(),
                        item.getProgressStatus(),
                        item.getCoverKey(),
                        item.getEditorialLevel(),
                        item.getCategory(),
                        item.getStartedAt()))
            .toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());
  }

  @Override
  @Transactional
  public ReadingProgress startIfAbsent(UUID userId, UUID readingId, LocalDateTime startedAt) {
    repository.insertInProgressIfAbsent(UUID.randomUUID(), userId, readingId, startedAt);
    return required(userId, readingId);
  }

  @Override
  @Transactional
  public ReadingProgress complete(UUID userId, UUID readingId, LocalDateTime completedAt) {
    repository.insertCompletedIfAbsent(UUID.randomUUID(), userId, readingId, completedAt);
    repository.completeIfInProgress(userId, readingId, completedAt);
    return required(userId, readingId);
  }

  @Override
  @Transactional
  public ReadingProgress updatePosition(
      UUID userId, UUID readingId, int currentPartOrdinal, int paginationVersion) {
    if (repository.updatePosition(userId, readingId, currentPartOrdinal, paginationVersion) != 1) {
      throw new IllegalStateException("Reading progress does not exist");
    }
    return required(userId, readingId);
  }

  private ReadingProgress required(UUID userId, UUID readingId) {
    return repository
        .findForUserAndReading(userId, readingId)
        .map(this::toDomain)
        .orElseThrow(() -> new IllegalStateException("Reading progress persistence failed"));
  }

  private ReadingProgress toDomain(ReadingProgressEntity entity) {
    return new ReadingProgress(
        entity.getId(),
        entity.getUser().getId(),
        entity.getReading().getId(),
        entity.getStatus(),
        entity.getStartedAt(),
        entity.getCompletedAt(),
        entity.getCurrentPartOrdinal(),
        entity.getPaginationVersion());
  }
}
