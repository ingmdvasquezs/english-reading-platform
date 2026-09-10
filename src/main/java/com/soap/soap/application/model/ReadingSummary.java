package com.soap.soap.application.model;

import com.soap.soap.domain.model.ReadingProgressStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReadingSummary(
    UUID id,
    String title,
    String language,
    LocalDateTime createdAt,
    int uniqueWords,
    int knownWords,
    int learningWords,
    int explicitNewWords,
    int ignoredWords,
    int unclassifiedWords,
    BigDecimal vocabularyFitPercentage,
    BigDecimal classificationConfidencePercentage,
    ReadingProgressStatus progressStatus) {

  public ReadingSummary(
      UUID id,
      String title,
      String language,
      LocalDateTime createdAt,
      int uniqueWords,
      int knownWords,
      int learningWords,
      int explicitNewWords,
      int ignoredWords,
      int unclassifiedWords) {
    this(
        id,
        title,
        language,
        createdAt,
        uniqueWords,
        knownWords,
        learningWords,
        explicitNewWords,
        ignoredWords,
        unclassifiedWords,
        null,
        null,
        null);
  }

  public ReadingSummary(UUID id, String title, String language, LocalDateTime createdAt) {
    this(id, title, language, createdAt, 0, 0, 0, 0, 0, 0, null, null, null);
  }
}
