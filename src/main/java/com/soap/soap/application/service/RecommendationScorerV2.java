package com.soap.soap.application.service;

import com.soap.soap.application.model.ReadingLexicalEvidence;
import com.soap.soap.application.model.RecommendationEvidenceV2;
import com.soap.soap.application.model.RecommendationScoreV2;
import com.soap.soap.domain.model.EditorialLevel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class RecommendationScorerV2 {
  public static final BigDecimal MIN_COMFORT_TARGET = new BigDecimal("95");
  public static final BigDecimal TARGET_LEARNING_PERCENTAGE = new BigDecimal("15");
  public static final BigDecimal SOFT_CHALLENGE_LIMIT = new BigDecimal("30");
  public static final BigDecimal EXCESS_PENALTY_MULTIPLIER = new BigDecimal("1.50");
  public static final BigDecimal COMFORT_WEIGHT = new BigDecimal("0.70");
  public static final BigDecimal REINFORCEMENT_WEIGHT = new BigDecimal("0.30");
  public static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  public RecommendationScoreV2 score(
      ReadingLexicalEvidence evidence, EditorialLevel editorialLevel, boolean isGlobalColdStart) {
    if (evidence == null) {
      return insufficientEvidenceScore(editorialLevel, 0, 0);
    }
    return calculateScore(
        evidence.totalTokens(),
        evidence.knownTokens(),
        evidence.learningTokens(),
        evidence.ignoredTokens(),
        evidence.totalUnique(),
        evidence.knownUnique(),
        evidence.learningUnique(),
        evidence.explicitNewUnique(),
        evidence.ignoredUnique(),
        evidence.unclassifiedUnique(),
        editorialLevel,
        isGlobalColdStart);
  }

  public RecommendationScoreV2 score(
      ReadingLexicalEvidence evidence, EditorialLevel editorialLevel) {
    return score(evidence, editorialLevel, false);
  }

  public RecommendationScoreV2 score(
      RecommendationEvidenceV2 evidence, EditorialLevel editorialLevel, boolean isGlobalColdStart) {
    if (evidence == null) {
      return insufficientEvidenceScore(editorialLevel, 0, 0);
    }
    return calculateScore(
        evidence.totalTokens(),
        evidence.knownTokens(),
        evidence.learningTokens(),
        evidence.ignoredTokens(),
        evidence.uniqueWords(),
        evidence.knownUniqueWords(),
        evidence.learningUniqueWords(),
        evidence.explicitNewUniqueWords(),
        evidence.ignoredUniqueWords(),
        evidence.unclassifiedUniqueWords(),
        editorialLevel,
        isGlobalColdStart);
  }

  public RecommendationScoreV2 score(
      RecommendationEvidenceV2 evidence, EditorialLevel editorialLevel) {
    return score(evidence, editorialLevel, false);
  }

  private RecommendationScoreV2 calculateScore(
      int totalTokens,
      int knownTokens,
      int learningTokens,
      int ignoredTokens,
      int totalUnique,
      int knownUnique,
      int learningUnique,
      int explicitNewUnique,
      int ignoredUnique,
      int unclassifiedUnique,
      EditorialLevel editorialLevel,
      boolean isGlobalColdStart) {
    var relevantTokens = totalTokens - ignoredTokens;
    var relevantUnique = totalUnique - ignoredUnique;
    var classifiedUnique = knownUnique + learningUnique + explicitNewUnique + ignoredUnique;
    var unclassifiedUniqueCalc = totalUnique - classifiedUnique;
    if (totalUnique <= 0 || relevantUnique <= 0 || relevantTokens <= 0) {
      return insufficientEvidenceScore(editorialLevel, totalUnique, classifiedUnique);
    }

    var editorialPrior = editorialPrior(editorialLevel);

    // 1. knownTokenCoverage = knownTokens / relevantTokens * 100
    var knownTokenCoverage = percentage(knownTokens, relevantTokens);

    // 2. KnownComfort = min(100, knownTokenCoverage / 95 * 100)
    var knownComfort =
        knownTokenCoverage
            .multiply(ONE_HUNDRED)
            .divide(MIN_COMFORT_TARGET, 8, RoundingMode.HALF_UP)
            .min(ONE_HUNDRED);

    // 3. learningUniqueRatio = learningUnique / relevantUnique * 100
    var learningUniqueRatio = percentage(learningUnique, relevantUnique);

    // 4. LearningReinforcement = min(100, learningUniqueRatio / 15 * 100)
    var learningReinforcement =
        learningUniqueRatio
            .multiply(ONE_HUNDRED)
            .divide(TARGET_LEARNING_PERCENTAGE, 8, RoundingMode.HALF_UP)
            .min(ONE_HUNDRED);

    // 5. localConfidence = classifiedUnique / totalUnique * 100
    // where:
    // classifiedUnique = knownUnique + learningUnique + explicitNewUnique + ignoredUnique
    // and unclassifiedUnique = totalUnique - classifiedUnique
    var localConfidence = percentage(classifiedUnique, totalUnique);
    var localConfidenceRatio = localConfidence.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);

    // 6. challengeUnique = explicitNewUnique + (unclassifiedUnique * localConfidenceRatio)
    //    UniqueChallenge = challengeUnique / relevantUnique * 100
    var unclassifiedWeighted =
        BigDecimal.valueOf(unclassifiedUniqueCalc).multiply(localConfidenceRatio);
    var challengeUnique = BigDecimal.valueOf(explicitNewUnique).add(unclassifiedWeighted);
    var uniqueChallenge =
        challengeUnique
            .multiply(ONE_HUNDRED)
            .divide(BigDecimal.valueOf(relevantUnique), 8, RoundingMode.HALF_UP);

    // 7. ExcessChallengePenalty = max(0, (UniqueChallenge - 30) * 1.50)
    var excessChallengePenalty =
        uniqueChallenge.compareTo(SOFT_CHALLENGE_LIMIT) > 0
            ? uniqueChallenge.subtract(SOFT_CHALLENGE_LIMIT).multiply(EXCESS_PENALTY_MULTIPLIER)
            : BigDecimal.ZERO;

    // 8. ScorePersonalized = clamp(KnownComfort * 0.70 + LearningReinforcement * 0.30 -
    // ExcessChallengePenalty, 0, 100)
    var rawPersonalized =
        knownComfort
            .multiply(COMFORT_WEIGHT)
            .add(learningReinforcement.multiply(REINFORCEMENT_WEIGHT))
            .subtract(excessChallengePenalty);
    var scorePersonalized = clamp(rawPersonalized, BigDecimal.ZERO, ONE_HUNDRED);

    // 9. FinalScore
    BigDecimal finalScore;
    if (isGlobalColdStart) {
      finalScore = editorialPrior;
    } else {
      var blended =
          localConfidenceRatio
              .multiply(scorePersonalized)
              .add(BigDecimal.ONE.subtract(localConfidenceRatio).multiply(editorialPrior));
      finalScore = clamp(blended, BigDecimal.ZERO, ONE_HUNDRED);
    }

    return new RecommendationScoreV2(
        scale4(finalScore),
        scale4(scorePersonalized),
        scale4(editorialPrior),
        scale2(knownComfort),
        scale2(learningReinforcement),
        scale2(uniqueChallenge),
        scale2(excessChallengePenalty),
        scale2(localConfidence),
        scale2(knownTokenCoverage),
        scale2(learningUniqueRatio),
        false);
  }

  public BigDecimal editorialPrior(EditorialLevel level) {
    if (level == null) {
      return new BigDecimal("50.0000");
    }
    return switch (level) {
      case A1 -> new BigDecimal("70.0000");
      case A2 -> new BigDecimal("66.0000");
      case B1 -> new BigDecimal("62.0000");
      case B2 -> new BigDecimal("58.0000");
      case C1 -> new BigDecimal("54.0000");
      case C2 -> new BigDecimal("50.0000");
    };
  }

  private BigDecimal percentage(int count, int total) {
    return BigDecimal.valueOf(count)
        .multiply(ONE_HUNDRED)
        .divide(BigDecimal.valueOf(total), 8, RoundingMode.HALF_UP);
  }

  private BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
    if (value.compareTo(min) < 0) {
      return min;
    }
    if (value.compareTo(max) > 0) {
      return max;
    }
    return value;
  }

  private BigDecimal scale4(BigDecimal value) {
    return value.setScale(4, RoundingMode.HALF_UP);
  }

  private BigDecimal scale2(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private RecommendationScoreV2 insufficientEvidenceScore(
      EditorialLevel editorialLevel, int totalUnique, int classifiedUnique) {
    var prior = editorialPrior(editorialLevel);
    var zero2 = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    var zero4 = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    var confidence =
        totalUnique > 0
            ? percentage(classifiedUnique, totalUnique).setScale(2, RoundingMode.HALF_UP)
            : zero2;
    return new RecommendationScoreV2(
        scale4(prior),
        zero4,
        scale4(prior),
        zero2,
        zero2,
        zero2,
        zero2,
        confidence,
        zero2,
        zero2,
        true);
  }
}
