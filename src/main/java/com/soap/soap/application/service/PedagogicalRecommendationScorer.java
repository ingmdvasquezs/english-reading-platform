package com.soap.soap.application.service;

import com.soap.soap.application.model.PedagogicalRecommendationScore;
import com.soap.soap.application.model.VocabularyBreakdown;
import com.soap.soap.domain.model.EditorialLevel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class PedagogicalRecommendationScorer {
  static final BigDecimal TARGET_KNOWN = new BigDecimal("65");
  static final BigDecimal TARGET_LEARNING = new BigDecimal("15");
  static final BigDecimal TARGET_CHALLENGE = new BigDecimal("20");
  static final BigDecimal KNOWN_DISTANCE_WEIGHT = new BigDecimal("0.80");
  static final BigDecimal LEARNING_DISTANCE_WEIGHT = new BigDecimal("1.40");
  static final BigDecimal CHALLENGE_DISTANCE_WEIGHT = new BigDecimal("1.00");
  static final BigDecimal EXPLICIT_NEW_RISK_WEIGHT = new BigDecimal("0.35");
  static final BigDecimal UNCLASSIFIED_RISK_WEIGHT = new BigDecimal("0.10");
  static final BigDecimal MINIMUM_EVIDENCE_WEIGHT = new BigDecimal("0.35");
  static final BigDecimal EDITORIAL_SAFETY_BONUS_PER_LEVEL = new BigDecimal("1.50");
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
  private static final BigDecimal MAX_SCORE = new BigDecimal("100");

  public PedagogicalRecommendationScore score(
      VocabularyBreakdown breakdown,
      BigDecimal classificationConfidencePercentage,
      EditorialLevel editorialLevel) {
    var relevantWords = breakdown.uniqueWords() - breakdown.ignoredWords();
    if (relevantWords <= 0) {
      return zeroScore();
    }

    var known = percentage(breakdown.knownWords(), relevantWords);
    var learning = percentage(breakdown.learningWords(), relevantWords);
    var explicitNew = percentage(breakdown.explicitNewWords(), relevantWords);
    var unclassified = percentage(breakdown.unclassifiedWords(), relevantWords);
    var accessible = known.add(learning);
    var challenge = explicitNew.add(unclassified);
    var confidence =
        classificationConfidencePercentage
            .max(BigDecimal.ZERO)
            .min(ONE_HUNDRED)
            .divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);

    // Sparse classifications are evidence of uncertainty, not proof that every unclassified word
    // is unknown. Keep a minimum signal, then increase the distance penalty with confidence.
    var evidenceWeight =
        MINIMUM_EVIDENCE_WEIGHT.add(
            BigDecimal.ONE.subtract(MINIMUM_EVIDENCE_WEIGHT).multiply(confidence));
    var distributionDistance =
        known
            .subtract(TARGET_KNOWN)
            .abs()
            .multiply(KNOWN_DISTANCE_WEIGHT)
            .add(learning.subtract(TARGET_LEARNING).abs().multiply(LEARNING_DISTANCE_WEIGHT))
            .add(challenge.subtract(TARGET_CHALLENGE).abs().multiply(CHALLENGE_DISTANCE_WEIGHT));

    // Explicit NEW is stronger evidence of difficulty. Unclassified risk grows only as the
    // surrounding classification becomes reliable.
    var evidenceRisk =
        explicitNew
            .multiply(EXPLICIT_NEW_RISK_WEIGHT)
            .add(unclassified.multiply(UNCLASSIFIED_RISK_WEIGHT).multiply(confidence));
    var lowConfidenceEditorialSafety =
        BigDecimal.valueOf(EditorialLevel.values().length - 1L - editorialLevel.ordinal())
            .multiply(EDITORIAL_SAFETY_BONUS_PER_LEVEL)
            .multiply(BigDecimal.ONE.subtract(confidence));
    var score =
        MAX_SCORE
            .subtract(distributionDistance.multiply(evidenceWeight))
            .subtract(evidenceRisk)
            .add(lowConfidenceEditorialSafety)
            .max(BigDecimal.ZERO)
            .min(MAX_SCORE)
            .setScale(4, RoundingMode.HALF_UP);
    return new PedagogicalRecommendationScore(
        score,
        known.setScale(2, RoundingMode.HALF_UP),
        learning.setScale(2, RoundingMode.HALF_UP),
        accessible.setScale(2, RoundingMode.HALF_UP),
        challenge.setScale(2, RoundingMode.HALF_UP),
        explicitNew.setScale(2, RoundingMode.HALF_UP),
        unclassified.setScale(2, RoundingMode.HALF_UP));
  }

  private BigDecimal percentage(int count, int total) {
    return BigDecimal.valueOf(count)
        .multiply(ONE_HUNDRED)
        .divide(BigDecimal.valueOf(total), 8, RoundingMode.HALF_UP);
  }

  private PedagogicalRecommendationScore zeroScore() {
    var zero = BigDecimal.ZERO.setScale(2);
    return new PedagogicalRecommendationScore(
        BigDecimal.ZERO.setScale(4), zero, zero, zero, zero, zero, zero);
  }
}
