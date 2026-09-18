package com.soap.soap.domain.model;

import java.time.LocalDateTime;

public record SrsItemParameters(
    SrsState state,
    VocabularyStatus vocabularyStatus,
    double stability,
    double difficulty,
    int repetitions,
    int lapses,
    LocalDateTime lastReviewedAt,
    LocalDateTime nextReviewAt) {

  public static SrsItemParameters defaultForNew() {
    return new SrsItemParameters(SrsState.NEW, VocabularyStatus.NEW, 0.0, 5.0, 0, 0, null, null);
  }
}
