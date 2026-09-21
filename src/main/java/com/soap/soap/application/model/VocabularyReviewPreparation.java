package com.soap.soap.application.model;

import java.util.List;

public record VocabularyReviewPreparation(
    long dueCount,
    long totalReviewableCount,
    int dailyLimit,
    int dailyBaseCompleted,
    int dailyBaseRemaining,
    int pendingLearningCount,
    boolean dailyComplete,
    List<VocabularyReviewItem> entries,
    List<VocabularyReviewItem> learnAheadEntries) {

  public VocabularyReviewPreparation {
    entries = entries != null ? List.copyOf(entries) : List.of();
    learnAheadEntries = learnAheadEntries != null ? List.copyOf(learnAheadEntries) : List.of();
  }

  public VocabularyReviewPreparation(
      long dueCount,
      long totalReviewableCount,
      int dailyLimit,
      int dailyBaseCompleted,
      int dailyBaseRemaining,
      int pendingLearningCount,
      boolean dailyComplete,
      List<VocabularyReviewItem> entries) {
    this(
        dueCount,
        totalReviewableCount,
        dailyLimit,
        dailyBaseCompleted,
        dailyBaseRemaining,
        pendingLearningCount,
        dailyComplete,
        entries,
        List.of());
  }

  public VocabularyReviewPreparation(
      long dueCount, long totalReviewableCount, List<VocabularyReviewItem> entries) {
    this(dueCount, totalReviewableCount, 15, 0, 15, 0, false, entries, List.of());
  }
}
