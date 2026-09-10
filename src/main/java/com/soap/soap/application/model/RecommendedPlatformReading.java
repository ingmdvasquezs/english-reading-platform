package com.soap.soap.application.model;

import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.ReadingProgressStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record RecommendedPlatformReading(
    UUID readingId,
    String title,
    String language,
    EditorialLevel editorialLevel,
    String category,
    LocalDateTime createdAt,
    int uniqueWords,
    int knownWords,
    int learningWords,
    int explicitNewWords,
    int ignoredWords,
    int unclassifiedWords,
    BigDecimal vocabularyFitPercentage,
    BigDecimal classificationConfidencePercentage,
    ReadingProgressStatus progressStatus,
    String coverKey) {
  public RecommendedPlatformReading(
      UUID readingId,
      String title,
      String language,
      EditorialLevel editorialLevel,
      String category,
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
    this(
        readingId,
        title,
        language,
        editorialLevel,
        category,
        createdAt,
        uniqueWords,
        knownWords,
        learningWords,
        explicitNewWords,
        ignoredWords,
        unclassifiedWords,
        vocabularyFitPercentage,
        classificationConfidencePercentage,
        progressStatus,
        null);
  }

  public RecommendedPlatformReading(
      UUID readingId,
      String title,
      String language,
      EditorialLevel editorialLevel,
      String category,
      LocalDateTime createdAt,
      int uniqueWords,
      int knownWords,
      int learningWords,
      int explicitNewWords,
      int ignoredWords,
      int unclassifiedWords,
      BigDecimal vocabularyFitPercentage,
      BigDecimal classificationConfidencePercentage) {
    this(
        readingId,
        title,
        language,
        editorialLevel,
        category,
        createdAt,
        uniqueWords,
        knownWords,
        learningWords,
        explicitNewWords,
        ignoredWords,
        unclassifiedWords,
        vocabularyFitPercentage,
        classificationConfidencePercentage,
        null,
        null);
  }
}
