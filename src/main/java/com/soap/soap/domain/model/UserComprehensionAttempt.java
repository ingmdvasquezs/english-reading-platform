package com.soap.soap.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record UserComprehensionAttempt(
    UUID id,
    UUID userId,
    UUID readingId,
    UUID submissionId,
    BigDecimal scorePercentage,
    int correctAnswersCount,
    int totalQuestionsCount,
    LocalDateTime submittedAt,
    List<UserComprehensionAnswer> answers) {

  public UserComprehensionAttempt {
    Objects.requireNonNull(id, "Attempt id must not be null");
    Objects.requireNonNull(userId, "User id must not be null");
    Objects.requireNonNull(readingId, "Reading id must not be null");
    Objects.requireNonNull(submissionId, "Submission id must not be null");
    Objects.requireNonNull(scorePercentage, "Score percentage must not be null");
    if (correctAnswersCount < 0) {
      throw new IllegalArgumentException("Correct answers count must not be negative");
    }
    if (totalQuestionsCount <= 0) {
      throw new IllegalArgumentException("Total questions count must be positive");
    }
    Objects.requireNonNull(submittedAt, "Submitted at must not be null");
    answers = answers == null ? List.of() : List.copyOf(answers);
  }
}
