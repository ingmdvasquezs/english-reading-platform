package com.soap.soap.application.model;

import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgressStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContinueReadingItem(
    UUID readingId,
    String title,
    ReadingOrigin origin,
    ReadingProgressStatus progressStatus,
    String coverKey,
    EditorialLevel editorialLevel,
    String category,
    LocalDateTime startedAt,
    String shortDescription,
    BigDecimal progressPercentage) {
  public ContinueReadingItem(
      UUID readingId,
      String title,
      ReadingOrigin origin,
      ReadingProgressStatus progressStatus,
      String coverKey,
      EditorialLevel editorialLevel,
      String category,
      LocalDateTime startedAt) {
    this(
        readingId,
        title,
        origin,
        progressStatus,
        coverKey,
        editorialLevel,
        category,
        startedAt,
        null,
        progressStatus == ReadingProgressStatus.COMPLETED ? BigDecimal.valueOf(100.0) : null);
  }
}
