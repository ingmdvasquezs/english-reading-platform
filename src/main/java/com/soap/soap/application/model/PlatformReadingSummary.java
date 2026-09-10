package com.soap.soap.application.model;

import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.ReadingProgressStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record PlatformReadingSummary(
    UUID id,
    String title,
    String language,
    EditorialLevel editorialLevel,
    String category,
    LocalDateTime createdAt,
    ReadingProgressStatus progressStatus,
    String coverKey) {
  public PlatformReadingSummary(
      UUID id,
      String title,
      String language,
      EditorialLevel editorialLevel,
      String category,
      LocalDateTime createdAt,
      ReadingProgressStatus progressStatus) {
    this(id, title, language, editorialLevel, category, createdAt, progressStatus, null);
  }

  public PlatformReadingSummary(
      UUID id,
      String title,
      String language,
      EditorialLevel editorialLevel,
      String category,
      LocalDateTime createdAt) {
    this(id, title, language, editorialLevel, category, createdAt, null, null);
  }
}
