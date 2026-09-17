package com.soap.soap.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record Reading(
    UUID id,
    User user,
    String title,
    String content,
    LanguageTag language,
    LocalDateTime createdAt,
    ReadingOrigin origin,
    EditorialLevel editorialLevel,
    String category,
    String coverKey,
    EditorialStatus editorialStatus,
    String shortDescription,
    EditorialContentType contentType,
    String countryCode,
    EditorialRegion region,
    SourceKind sourceKind,
    RightsStatus rightsStatus,
    AdaptationKind adaptationKind,
    LanguageTag sourceLanguage,
    String sourceTitle,
    String sourceAuthor,
    String sourceUrl,
    String sourceNotes,
    String adaptationGroupKey,
    String coverAttribution,
    AccessTier accessTier) {

  public Reading {
    Objects.requireNonNull(origin, "Reading origin must not be null");
    Objects.requireNonNull(language, "Reading language must not be null");

    if (origin == ReadingOrigin.USER) {
      if (user == null) {
        throw new IllegalArgumentException("User reading must have an owner");
      }
      if (editorialLevel != null
          || category != null
          || editorialStatus != null
          || shortDescription != null
          || contentType != null
          || countryCode != null
          || region != null
          || sourceKind != null
          || rightsStatus != null
          || adaptationKind != null
          || sourceLanguage != null
          || sourceTitle != null
          || sourceAuthor != null
          || sourceUrl != null
          || sourceNotes != null
          || adaptationGroupKey != null
          || coverAttribution != null
          || accessTier != null) {
        throw new IllegalArgumentException(
            "User reading must not have platform metadata or editorial status");
      }
    }

    if (origin == ReadingOrigin.PLATFORM) {
      if (user != null) {
        throw new IllegalArgumentException("Platform reading must not have an owner");
      }
      if (editorialLevel == null || category == null || category.isBlank()) {
        throw new IllegalArgumentException("Platform reading must have editorial metadata");
      }
      if (editorialStatus == null) {
        throw new IllegalArgumentException("Platform reading must have explicit editorial status");
      }
      if (shortDescription == null || shortDescription.isBlank()) {
        throw new IllegalArgumentException("Platform reading must have a short description");
      }
      if (contentType == null) {
        throw new IllegalArgumentException("Platform reading must have a content type");
      }
      if (region == null) {
        throw new IllegalArgumentException("Platform reading must have a region");
      }
      if (sourceKind == null) {
        throw new IllegalArgumentException("Platform reading must have a source kind");
      }
      if (rightsStatus == null) {
        throw new IllegalArgumentException("Platform reading must have a rights status");
      }
      if (adaptationKind == null) {
        throw new IllegalArgumentException("Platform reading must have an adaptation kind");
      }
      if (accessTier == null) {
        throw new IllegalArgumentException("Platform reading must have an access tier");
      }
      if (adaptationKind == AdaptationKind.TRANSLATED_ADAPTATION && sourceLanguage == null) {
        throw new IllegalArgumentException("Translated adaptation requires source language");
      }
      if (countryCode != null && !countryCode.matches("^[A-Z]{2}$")) {
        throw new IllegalArgumentException(
            "Country code must be 2 uppercase ISO letters: " + countryCode);
      }
    }
  }

  /** Minimal constructor for USER readings with String language. */
  public Reading(
      UUID id, User user, String title, String content, String language, LocalDateTime createdAt) {
    this(
        id,
        user,
        title,
        content,
        LanguageTag.of(language),
        createdAt,
        ReadingOrigin.USER,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  /** Minimal constructor for USER readings with LanguageTag. */
  public Reading(
      UUID id,
      User user,
      String title,
      String content,
      LanguageTag language,
      LocalDateTime createdAt) {
    this(
        id,
        user,
        title,
        content,
        language,
        createdAt,
        ReadingOrigin.USER,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  /** Backward-compatible constructor for existing tests. */
  public Reading(
      UUID id,
      User user,
      String title,
      String content,
      String language,
      LocalDateTime createdAt,
      ReadingOrigin origin,
      EditorialLevel editorialLevel,
      String category,
      String coverKey,
      EditorialStatus editorialStatus) {
    this(
        id,
        user,
        title,
        content,
        LanguageTag.of(language),
        createdAt,
        origin,
        editorialLevel,
        category,
        coverKey,
        editorialStatus,
        origin == ReadingOrigin.PLATFORM ? "Default short description for " + title : null,
        origin == ReadingOrigin.PLATFORM ? EditorialContentType.FICTION : null,
        null,
        origin == ReadingOrigin.PLATFORM ? EditorialRegion.GLOBAL : null,
        origin == ReadingOrigin.PLATFORM ? SourceKind.ORIGINAL_EDITORIAL : null,
        origin == ReadingOrigin.PLATFORM ? RightsStatus.ORIGINAL : null,
        origin == ReadingOrigin.PLATFORM ? AdaptationKind.ORIGINAL : null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        origin == ReadingOrigin.PLATFORM ? AccessTier.FREE : null);
  }

  /** Backward-compatible constructor without coverKey. */
  public Reading(
      UUID id,
      User user,
      String title,
      String content,
      String language,
      LocalDateTime createdAt,
      ReadingOrigin origin,
      EditorialLevel editorialLevel,
      String category,
      EditorialStatus editorialStatus) {
    this(
        id,
        user,
        title,
        content,
        language,
        createdAt,
        origin,
        editorialLevel,
        category,
        null,
        editorialStatus);
  }

  public boolean isAccessibleBy(UUID userId) {
    return origin == ReadingOrigin.PLATFORM || (user != null && userId.equals(user.id()));
  }

  public Reading withContent(String newContent) {
    return new Reading(
        this.id,
        this.user,
        this.title,
        newContent,
        this.language,
        this.createdAt,
        this.origin,
        this.editorialLevel,
        this.category,
        this.coverKey,
        this.editorialStatus,
        this.shortDescription,
        this.contentType,
        this.countryCode,
        this.region,
        this.sourceKind,
        this.rightsStatus,
        this.adaptationKind,
        this.sourceLanguage,
        this.sourceTitle,
        this.sourceAuthor,
        this.sourceUrl,
        this.sourceNotes,
        this.adaptationGroupKey,
        this.coverAttribution,
        this.accessTier);
  }
}
