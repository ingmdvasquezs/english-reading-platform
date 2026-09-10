package com.soap.soap.application.service;

import com.soap.soap.application.model.RecommendationEvidenceV2;
import com.soap.soap.application.model.RecommendationScoreV2;
import com.soap.soap.domain.model.EditorialLevel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class RecommendationScorerV2 {
  static final BigDecimal KNOWN_TOKEN_WEIGHT = new BigDecimal("0.50");
  static final BigDecimal LEARNING_REINFORCEMENT_WEIGHT = new BigDecimal("0.30");
  static final BigDecimal LEXICAL_ACCESSIBILITY_WEIGHT = new BigDecimal("0.20");
  static final BigDecimal TARGET_LEARNING_PERCENTAGE = new BigDecimal("15");
  static final BigDecimal EXPLICIT_NEW_RISK_WEIGHT = BigDecimal.ONE;
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  public RecommendationScoreV2 score(
      RecommendationEvidenceV2 evidence, EditorialLevel editorialLevel) {
    var relevantTokens = evidence.totalTokens() - evidence.ignoredTokens();
    var relevantUnique = evidence.uniqueWords() - evidence.ignoredUniqueWords();
    if (relevantTokens <= 0 || relevantUnique <= 0) {
      return zeroScore();
    }

    var knownTokenCoverage = percentage(evidence.knownTokens(), relevantTokens);
    var learningUniquePercentage = percentage(evidence.learningUniqueWords(), relevantUnique);
    var learningReinforcement =
        learningUniquePercentage
            .multiply(ONE_HUNDRED)
            .divide(TARGET_LEARNING_PERCENTAGE, 8, RoundingMode.HALF_UP)
            .min(ONE_HUNDRED);
    var lexicalChallenge =
        percentage(
            evidence.explicitNewUniqueWords() + evidence.unclassifiedUniqueWords(), relevantUnique);
    var lexicalAccessibility = ONE_HUNDRED.subtract(lexicalChallenge);
    var confidence =
        percentage(
            evidence.uniqueWords() - evidence.unclassifiedUniqueWords(), evidence.uniqueWords());
    var confidenceRatio = confidence.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
    var editorialPrior = editorialPrior(editorialLevel);

    var rawPersonalized =
        knownTokenCoverage
            .multiply(KNOWN_TOKEN_WEIGHT)
            .add(learningReinforcement.multiply(LEARNING_REINFORCEMENT_WEIGHT))
            .add(lexicalAccessibility.multiply(LEXICAL_ACCESSIBILITY_WEIGHT));
    // Keeping the personalized branch at least at the prior makes positive evidence monotonic:
    // learning more about KNOWN/LEARNING vocabulary cannot lower the blended score.
    var personalized = rawPersonalized.max(editorialPrior);
    var explicitNewRisk =
        percentage(evidence.explicitNewUniqueWords(), relevantUnique)
            .multiply(EXPLICIT_NEW_RISK_WEIGHT);
    var finalScore =
        confidenceRatio
            .multiply(personalized)
            .add(BigDecimal.ONE.subtract(confidenceRatio).multiply(editorialPrior))
            .subtract(explicitNewRisk)
            .max(BigDecimal.ZERO)
            .min(ONE_HUNDRED);

    return new RecommendationScoreV2(
        scale(finalScore),
        scale(personalized),
        scale(editorialPrior),
        scale(knownTokenCoverage),
        scale(learningReinforcement),
        scale(lexicalChallenge),
        scale(confidence),
        scale(explicitNewRisk));
  }

  private BigDecimal editorialPrior(EditorialLevel level) {
    return switch (level) {
      case A1 -> new BigDecimal("70");
      case A2 -> new BigDecimal("66");
      case B1 -> new BigDecimal("62");
      case B2 -> new BigDecimal("58");
      case C1 -> new BigDecimal("54");
      case C2 -> new BigDecimal("50");
    };
  }

  private BigDecimal percentage(int count, int total) {
    return BigDecimal.valueOf(count)
        .multiply(ONE_HUNDRED)
        .divide(BigDecimal.valueOf(total), 8, RoundingMode.HALF_UP);
  }

  private BigDecimal scale(BigDecimal value) {
    return value.setScale(4, RoundingMode.HALF_UP);
  }

  private RecommendationScoreV2 zeroScore() {
    var zero = BigDecimal.ZERO.setScale(4);
    return new RecommendationScoreV2(zero, zero, zero, zero, zero, zero, zero, zero);
  }
}
