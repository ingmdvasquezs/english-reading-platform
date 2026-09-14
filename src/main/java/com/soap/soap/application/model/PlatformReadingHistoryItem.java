package com.soap.soap.application.model;

import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.ReadingProgressStatus;
import java.util.Objects;
import java.util.UUID;

public record PlatformReadingHistoryItem(
    UUID readingId,
    String title,
    EditorialLevel editorialLevel,
    String category,
    String coverKey,
    ReadingProgressStatus progressStatus) {

  public PlatformReadingHistoryItem {
    Objects.requireNonNull(readingId, "readingId must not be null");
    Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(editorialLevel, "editorialLevel must not be null");
    Objects.requireNonNull(category, "category must not be null");
    Objects.requireNonNull(progressStatus, "progressStatus must not be null");
  }
}
