package com.soap.soap.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ComprehensionOption(
    UUID id, UUID questionId, int ordinal, String content, boolean isCorrect) {
  public ComprehensionOption {
    Objects.requireNonNull(id, "Option id must not be null");
    Objects.requireNonNull(questionId, "Question id must not be null");
    if (ordinal < 1) {
      throw new IllegalArgumentException("Ordinal must be greater than or equal to 1");
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("Content must not be blank");
    }
  }
}
