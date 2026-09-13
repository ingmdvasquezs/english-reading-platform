package com.soap.soap.application.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record ReadingLexicalEvidence(
    UUID readingId,
    int totalTokens,
    int knownTokens,
    int learningTokens,
    int ignoredTokens,
    int totalUnique,
    int knownUnique,
    int learningUnique,
    int explicitNewUnique,
    int ignoredUnique,
    int unclassifiedUnique) {

  public ReadingLexicalEvidence {
    if (readingId == null) {
      throw new IllegalArgumentException("Reading ID must not be null");
    }
    if (totalTokens < 0
        || knownTokens < 0
        || learningTokens < 0
        || ignoredTokens < 0
        || totalUnique < 0
        || knownUnique < 0
        || learningUnique < 0
        || explicitNewUnique < 0
        || ignoredUnique < 0
        || unclassifiedUnique < 0) {
      throw new IllegalArgumentException("Lexical evidence counts must not be negative");
    }
    if (knownTokens + learningTokens + ignoredTokens > totalTokens) {
      throw new IllegalArgumentException("Token counts exceed total tokens");
    }
    if (knownUnique + learningUnique + explicitNewUnique + ignoredUnique + unclassifiedUnique
        != totalUnique) {
      throw new IllegalArgumentException("Unique word counts do not sum to total unique words");
    }
  }

  public int relevantTokens() {
    return totalTokens - ignoredTokens;
  }

  public int relevantUnique() {
    return totalUnique - ignoredUnique;
  }

  public int classifiedUnique() {
    return totalUnique - unclassifiedUnique;
  }

  public BigDecimal localClassificationConfidence() {
    if (totalUnique <= 0) {
      return BigDecimal.ZERO.setScale(2);
    }
    return BigDecimal.valueOf(classifiedUnique())
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(totalUnique), 2, RoundingMode.HALF_UP);
  }

  public RecommendationEvidenceV2 toRecommendationEvidenceV2() {
    return new RecommendationEvidenceV2(
        totalTokens,
        knownTokens,
        learningTokens,
        ignoredTokens,
        totalUnique,
        knownUnique,
        learningUnique,
        explicitNewUnique,
        ignoredUnique,
        unclassifiedUnique);
  }
}
