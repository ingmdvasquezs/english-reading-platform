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
    String coverKey) {

  public Reading(
      UUID id,
      User user,
      String title,
      String content,
      String language,
      LocalDateTime createdAt,
      ReadingOrigin origin,
      EditorialLevel editorialLevel,
      String category) {
    this(id, user, title, content, language, createdAt, origin, editorialLevel, category, null);
  }

  public Reading(
      UUID id, User user, String title, String content, String language, LocalDateTime createdAt) {
    this(id, user, title, content, language, createdAt, ReadingOrigin.USER, null, null, null);
  }

  public Reading {
    Objects.requireNonNull(origin, "Reading origin must not be null");
    if (origin == ReadingOrigin.USER && user == null) {
      throw new IllegalArgumentException("User reading must have an owner");
    }
    if (origin == ReadingOrigin.PLATFORM && user != null) {
      throw new IllegalArgumentException("Platform reading must not have an owner");
    }
    if (origin == ReadingOrigin.PLATFORM
        && (editorialLevel == null || category == null || category.isBlank())) {
      throw new IllegalArgumentException("Platform reading must have editorial metadata");
    }
    if (origin == ReadingOrigin.USER && (editorialLevel != null || category != null)) {
      throw new IllegalArgumentException("User reading must not have platform metadata");
    }
  }

  public boolean isAccessibleBy(UUID userId) {
    return origin == ReadingOrigin.PLATFORM || userId.equals(user.id());
  }
}
