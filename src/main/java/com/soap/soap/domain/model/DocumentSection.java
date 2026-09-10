package com.soap.soap.domain.model;

import java.util.Objects;
import java.util.UUID;

public record DocumentSection(
    UUID id, UUID documentId, int ordinal, String title, String sourceLocator) {

  public DocumentSection {
    Objects.requireNonNull(documentId, "Section document id must not be null");
    if (ordinal <= 0) throw new IllegalArgumentException("Section ordinal must be positive");
    title = optionalText(title, "Section title");
    sourceLocator = optionalText(sourceLocator, "Section source locator");
  }

  private static String optionalText(String value, String field) {
    if (value == null) return null;
    var normalized = value.strip();
    if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
    return normalized;
  }
}
