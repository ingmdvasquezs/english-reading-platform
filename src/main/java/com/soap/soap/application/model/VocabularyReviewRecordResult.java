package com.soap.soap.application.model;

import com.soap.soap.domain.model.SrsState;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record VocabularyReviewRecordResult(
    UserVocabulary vocabulary,
    List<ReviewRatingOption> ratingOptions,
    Long pendingQueueSequence,
    Integer baseOrder) {

  public VocabularyReviewRecordResult(
      UserVocabulary vocabulary, List<ReviewRatingOption> ratingOptions) {
    this(vocabulary, ratingOptions, null, null);
  }

  public UUID id() {
    return vocabulary.id();
  }

  public Word word() {
    return vocabulary.word();
  }

  public VocabularyStatus status() {
    return vocabulary.status();
  }

  public SrsState srsState() {
    return vocabulary.srsState();
  }

  public LocalDateTime nextReviewAt() {
    return vocabulary.nextReviewAt();
  }

  public LocalDateTime lastReviewedAt() {
    return vocabulary.lastReviewedAt();
  }

  public LocalDateTime learnedAt() {
    return vocabulary.learnedAt();
  }

  public int repetitions() {
    return vocabulary.repetitions();
  }

  public int lapses() {
    return vocabulary.lapses();
  }

  public double stability() {
    return vocabulary.stability();
  }

  public double difficulty() {
    return vocabulary.difficulty();
  }
}
