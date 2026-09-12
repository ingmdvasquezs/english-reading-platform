package com.soap.soap.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ComprehensionQuestion(
    UUID id,
    UUID readingId,
    int ordinal,
    QuestionType questionType,
    String prompt,
    String explanation,
    LocalDateTime createdAt,
    List<ComprehensionOption> options) {

  public ComprehensionQuestion {
    Objects.requireNonNull(id, "Question id must not be null");
    Objects.requireNonNull(readingId, "Reading id must not be null");
    if (ordinal < 1) {
      throw new IllegalArgumentException("Ordinal must be greater than or equal to 1");
    }
    Objects.requireNonNull(questionType, "Question type must not be null");
    if (prompt == null || prompt.isBlank()) {
      throw new IllegalArgumentException("Prompt must not be blank");
    }
    if (explanation == null || explanation.isBlank()) {
      throw new IllegalArgumentException("Explanation must not be blank");
    }
    Objects.requireNonNull(createdAt, "CreatedAt must not be null");
    if (options != null) {
      options = List.copyOf(options);
      if (options.size() != 4) {
        throw new IllegalArgumentException("Question must have exactly 4 options");
      }
      long correctCount = options.stream().filter(ComprehensionOption::isCorrect).count();
      if (correctCount != 1) {
        throw new IllegalArgumentException("Question must have exactly 1 correct option");
      }
    }
  }
}
