package com.soap.soap.application.model;

import java.util.List;

public record VocabularyReviewPreparation(
    long dueCount, long totalReviewableCount, List<VocabularyReviewItem> entries) {
  public VocabularyReviewPreparation {
    entries = List.copyOf(entries);
  }
}
