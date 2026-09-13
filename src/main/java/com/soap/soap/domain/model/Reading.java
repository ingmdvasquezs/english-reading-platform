package com.soap.soap.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record Reading(
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

  public Reading {
    Objects.requireNonNull(origin, "Reading origin must not be null");
    if (origin == ReadingOrigin.USER) {
      if (user == null) {
        throw new IllegalArgumentException("User reading must have an owner");
      }
      if (editorialLevel != null || category != null || editorialStatus != null) {
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
    }
  }

  public Reading(
      UUID id, User user, String title, String content, String language, LocalDateTime createdAt) {
    this(id, user, title, content, language, createdAt, ReadingOrigin.USER, null, null, null, null);
  }

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
}
