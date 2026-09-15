package com.soap.soap.application.model;

import com.soap.soap.domain.model.AccessTier;
import com.soap.soap.domain.model.EditorialContentType;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialRegion;
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
    String coverKey,
    String shortDescription,
    EditorialContentType contentType,
    String countryCode,
    EditorialRegion region,
    AccessTier accessTier) {
  public PlatformReadingSummary(
      UUID id,
      String title,
      String language,
      EditorialLevel editorialLevel,
      String category,
      LocalDateTime createdAt,
      ReadingProgressStatus progressStatus,
      String coverKey) {
    this(
        id,
        title,
        language,
        editorialLevel,
        category,
        createdAt,
        progressStatus,
        coverKey,
        null,
        null,
        null,
        null,
        null);
  }

  public PlatformReadingSummary(
      UUID id,
      String title,
      String language,
      EditorialLevel editorialLevel,
      String category,
      LocalDateTime createdAt,
      ReadingProgressStatus progressStatus) {
    this(
        id,
        title,
        language,
        editorialLevel,
        category,
        createdAt,
        progressStatus,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public PlatformReadingSummary(
      UUID id,
      String title,
      String language,
      EditorialLevel editorialLevel,
      String category,
      LocalDateTime createdAt) {
    this(
        id,
        title,
        language,
        editorialLevel,
        category,
        createdAt,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
