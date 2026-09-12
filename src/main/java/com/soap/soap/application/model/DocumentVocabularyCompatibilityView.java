package com.soap.soap.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record DocumentVocabularyCompatibilityView(
    UUID documentId,
    int uniqueWords,
    int knownWords,
    int learningWords,
    int explicitNewWords,
    int ignoredWords,
    int unclassifiedWords,
    BigDecimal vocabularyFitPercentage,
    BigDecimal classificationConfidencePercentage) {}
