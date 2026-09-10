package com.soap.soap.domain.model;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record DocumentUnit(
    UUID id,
    UUID documentId,
    UUID sectionId,
    int globalOrdinal,
    int sectionOrdinal,
    DocumentUnitKind kind,
    String content,
    int wordCount,
    String sourceLocator,
    String contentHash) {

  private static final Pattern SHA_256 = Pattern.compile("^[a-f0-9]{64}$");

  public DocumentUnit {
    Objects.requireNonNull(documentId, "Unit document id must not be null");
    if (globalOrdinal <= 0)
      throw new IllegalArgumentException("Unit global ordinal must be positive");
    if (sectionOrdinal <= 0) {
      throw new IllegalArgumentException("Unit section ordinal must be positive");
    }
    Objects.requireNonNull(kind, "Unit kind must not be null");
    Objects.requireNonNull(content, "Unit content must not be null");
    if (content.isBlank()) throw new IllegalArgumentException("Unit content must not be blank");
    if (wordCount < 0) throw new IllegalArgumentException("Unit word count must not be negative");
    sourceLocator = optionalText(sourceLocator, "Unit source locator");
    if (contentHash != null && !SHA_256.matcher(contentHash).matches()) {
      throw new IllegalArgumentException("Unit content hash must contain 64 lowercase hex digits");
    }
  }

  private static String optionalText(String value, String field) {
    if (value == null) return null;
    var normalized = value.strip();
    if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
    return normalized;
  }
}
