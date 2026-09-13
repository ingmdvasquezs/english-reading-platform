package com.soap.soap.application.model;

import java.math.BigDecimal;

public record RecommendationScoreV2(
    BigDecimal finalScore,
    BigDecimal personalizedScore,
    BigDecimal editorialPriorScore,
    BigDecimal knownComfort,
    BigDecimal learningReinforcement,
    BigDecimal uniqueChallenge,
    BigDecimal excessChallengePenalty,
    BigDecimal classificationConfidence,
    BigDecimal knownTokenCoverage,
    BigDecimal learningUniqueRatio,
    boolean insufficientEvidence) {

  public RecommendationScoreV2(
      BigDecimal finalScore,
      BigDecimal personalizedScore,
      BigDecimal editorialPriorScore,
      BigDecimal knownComfort,
      BigDecimal learningReinforcement,
      BigDecimal uniqueChallenge,
      BigDecimal excessChallengePenalty,
      BigDecimal classificationConfidence,
      BigDecimal knownTokenCoverage,
      BigDecimal learningUniqueRatio) {
    this(
        finalScore,
        personalizedScore,
        editorialPriorScore,
        knownComfort,
        learningReinforcement,
        uniqueChallenge,
        excessChallengePenalty,
        classificationConfidence,
        knownTokenCoverage,
        learningUniqueRatio,
        false);
  }

  public BigDecimal tokenKnownCoverage() {
    return knownTokenCoverage;
  }

  public BigDecimal lexicalChallenge() {
    return uniqueChallenge;
  }
}
