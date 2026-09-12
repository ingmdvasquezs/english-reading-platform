package com.soap.soap.application.model;

import java.util.List;
import java.util.UUID;

public record ComprehensionQuizView(
    UUID readingId,
    boolean available,
    List<ComprehensionQuizQuestionView> questions,
    Integer selectionVersion) {

  public ComprehensionQuizView(
      UUID readingId, boolean available, List<ComprehensionQuizQuestionView> questions) {
    this(readingId, available, questions, null);
  }
}
