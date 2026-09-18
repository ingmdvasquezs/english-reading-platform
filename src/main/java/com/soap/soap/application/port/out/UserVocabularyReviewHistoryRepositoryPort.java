package com.soap.soap.application.port.out;

import com.soap.soap.application.model.VocabularyReviewHistoryEntry;

public interface UserVocabularyReviewHistoryRepositoryPort {
  void recordReviewHistory(VocabularyReviewHistoryEntry entry);
}
