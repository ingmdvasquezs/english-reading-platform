package com.soap.soap.application.model;

import java.math.BigDecimal;

public record RecommendationScoreV2(
    BigDecimal finalScore,
    BigDecimal personalizedScore,
    BigDecimal editorialPriorScore,
    BigDecimal tokenKnownCoverage,
    BigDecimal learningReinforcement,
    BigDecimal lexicalChallenge,
    BigDecimal classificationConfidence,
    BigDecimal explicitNewRisk) {}
