package com.soap.soap.application.model;

import java.math.BigDecimal;

public record VocabularyCompatibility(
    VocabularyBreakdown breakdown,
    BigDecimal vocabularyFitPercentage,
    BigDecimal classificationConfidencePercentage) {}
