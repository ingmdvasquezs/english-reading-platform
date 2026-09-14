package com.soap.soap.application.port.out;

import com.soap.soap.application.model.ContinueReadingItem;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.PlatformReadingHistoryItem;
import com.soap.soap.domain.model.ReadingProgress;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ReadingProgressRepositoryPort {
  Optional<ReadingProgress> findByUserIdAndReadingId(UUID userId, UUID readingId);

  Map<UUID, ReadingProgress> findByUserIdAndReadingIds(UUID userId, Set<UUID> readingIds);

  PageResult<ContinueReadingItem> findInProgressReadings(UUID userId, PageRequest pageRequest);

  PageResult<PlatformReadingHistoryItem> findPlatformReadingHistory(
      UUID userId, PageRequest pageRequest);

  ReadingProgress startIfAbsent(UUID userId, UUID readingId, LocalDateTime startedAt);

  ReadingProgress complete(UUID userId, UUID readingId, LocalDateTime completedAt);

  ReadingProgress updatePosition(
      UUID userId, UUID readingId, int currentPartOrdinal, int paginationVersion);
}
