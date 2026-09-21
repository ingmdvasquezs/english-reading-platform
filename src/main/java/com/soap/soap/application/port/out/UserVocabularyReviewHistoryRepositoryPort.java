package com.soap.soap.application.port.out;

import com.soap.soap.application.model.ReviewedWordDaySummary;
import com.soap.soap.application.model.VocabularyReviewHistoryEntry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface UserVocabularyReviewHistoryRepositoryPort {
  void recordReviewHistory(VocabularyReviewHistoryEntry entry);

  List<UUID> findDistinctUserVocabularyIdsReviewedBetween(
      UUID userId, LocalDateTime start, LocalDateTime end);

  long countDistinctReviewedWordsBetween(UUID userId, LocalDateTime start, LocalDateTime end);

  List<ReviewedWordDaySummary> findReviewedWordsSummaryBetween(
      UUID userId, LocalDateTime start, LocalDateTime end);
}
