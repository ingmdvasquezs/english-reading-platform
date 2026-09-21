package com.soap.soap.application.model;

import com.soap.soap.domain.model.SrsState;
import com.soap.soap.domain.model.VocabularyStatus;
import java.util.List;
import java.util.UUID;

public record VocabularyReviewItem(
    UUID wordId,
    String word,
    String language,
    VocabularyStatus status,
    SrsState srsState,
    List<ReviewRatingOption> ratingOptions,
    Long pendingQueueSequence,
    Integer baseOrder) {

  public VocabularyReviewItem(
      UUID wordId,
      String word,
      String language,
      VocabularyStatus status,
      SrsState srsState,
      List<ReviewRatingOption> ratingOptions) {
    this(wordId, word, language, status, srsState, ratingOptions, null, null);
  }

  public VocabularyReviewItem(UUID wordId, String word, String language, VocabularyStatus status) {
    this(wordId, word, language, status, SrsState.NEW, List.of(), null, null);
  }
}
