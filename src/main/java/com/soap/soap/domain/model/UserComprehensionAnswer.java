package com.soap.soap.domain.model;

import java.util.Objects;
import java.util.UUID;

public record UserComprehensionAnswer(
    UUID id, UUID attemptId, UUID questionId, UUID selectedOptionId, boolean isCorrect) {
  public UserComprehensionAnswer {
    Objects.requireNonNull(id, "Answer id must not be null");
    Objects.requireNonNull(questionId, "Question id must not be null");
    Objects.requireNonNull(selectedOptionId, "Selected option id must not be null");
  }
}
