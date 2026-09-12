package com.soap.soap.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ComprehensionQuiz(UUID readingId, List<ComprehensionQuestion> questions) {
  public ComprehensionQuiz {
    Objects.requireNonNull(readingId, "Reading id must not be null");
    questions = questions == null ? List.of() : List.copyOf(questions);
  }

  public boolean isAvailable() {
    return !questions.isEmpty();
  }
}
