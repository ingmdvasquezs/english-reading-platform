package com.soap.soap.application.model;

public record RecommendationEvidenceV2(
    int totalTokens,
    int knownTokens,
    int learningTokens,
    int ignoredTokens,
    int uniqueWords,
    int knownUniqueWords,
    int learningUniqueWords,
    int explicitNewUniqueWords,
    int ignoredUniqueWords,
    int unclassifiedUniqueWords) {

  public RecommendationEvidenceV2 {
    if (totalTokens < 0
        || knownTokens < 0
        || learningTokens < 0
        || ignoredTokens < 0
        || uniqueWords < 0
        || knownUniqueWords < 0
        || learningUniqueWords < 0
        || explicitNewUniqueWords < 0
        || ignoredUniqueWords < 0
        || unclassifiedUniqueWords < 0) {
      throw new IllegalArgumentException("Recommendation evidence counts must not be negative");
    }
    if (knownTokens + learningTokens + ignoredTokens > totalTokens
        || knownUniqueWords
                + learningUniqueWords
                + explicitNewUniqueWords
                + ignoredUniqueWords
                + unclassifiedUniqueWords
            != uniqueWords) {
      throw new IllegalArgumentException("Recommendation evidence counts are inconsistent");
    }
  }
}
