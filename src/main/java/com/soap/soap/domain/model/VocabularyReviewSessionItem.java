package com.soap.soap.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record VocabularyReviewSessionItem(
    UUID id,
    UUID sessionId,
    UserVocabulary vocabulary,
    int baseOrder,
    LocalDateTime introducedAt,
    Long pendingQueueSequence) {

  public VocabularyReviewSessionItem withIntroducedAt(LocalDateTime introducedAt) {
    return new VocabularyReviewSessionItem(
        id, sessionId, vocabulary, baseOrder, introducedAt, pendingQueueSequence);
  }

  public VocabularyReviewSessionItem withPendingQueueSequence(Long pendingQueueSequence) {
    return new VocabularyReviewSessionItem(
        id, sessionId, vocabulary, baseOrder, introducedAt, pendingQueueSequence);
  }

  public VocabularyReviewSessionItem withVocabulary(UserVocabulary vocabulary) {
    return new VocabularyReviewSessionItem(
        id, sessionId, vocabulary, baseOrder, introducedAt, pendingQueueSequence);
  }
}
