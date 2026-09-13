package com.soap.soap.application.service;

import com.soap.soap.application.model.VocabularyBreakdown;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.RecommendationReasonCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class RecommendationReasonEvaluator {
  private static final BigDecimal TWENTY_FIVE = new BigDecimal("25.00");
  private static final BigDecimal FORTY = new BigDecimal("40.00");
  private static final BigDecimal NINETY = new BigDecimal("90.00");
  private static final BigDecimal FIFTEEN = new BigDecimal("15.00");
  private static final BigDecimal TEN = new BigDecimal("10.00");
  private static final BigDecimal THIRTY = new BigDecimal("30.00");

  public RecommendationReasonCode evaluate(
      ReadingProgressStatus progressStatus,
      boolean isGlobalColdStart,
      boolean insufficientLocalEvidence,
      BigDecimal localConfidence,
      BigDecimal knownTokenCoverage,
      BigDecimal learningUniqueRatio,
      BigDecimal uniqueChallenge) {
    // 1. CONTINUE_READING: progress == IN_PROGRESS
    if (progressStatus == ReadingProgressStatus.IN_PROGRESS) {
      return RecommendationReasonCode.CONTINUE_READING;
    }

    // 2. DISCOVERY: global cold start
    if (isGlobalColdStart) {
      return RecommendationReasonCode.DISCOVERY;
    }

    // 3. DISCOVERY: insufficient local evidence / localConfidence < 25
    if (insufficientLocalEvidence
        || localConfidence == null
        || localConfidence.compareTo(TWENTY_FIVE) < 0) {
      return RecommendationReasonCode.DISCOVERY;
    }

    // Rules with localConfidence >= 40%
    if (localConfidence.compareTo(FORTY) >= 0) {
      // 4. HIGH_VOCABULARY_MATCH: localConfidence >= 40% AND knownTokenCoverage >= 90% AND
      // UniqueChallenge <= 15%
      if (knownTokenCoverage != null
          && knownTokenCoverage.compareTo(NINETY) >= 0
          && uniqueChallenge != null
          && uniqueChallenge.compareTo(FIFTEEN) <= 0) {
        return RecommendationReasonCode.HIGH_VOCABULARY_MATCH;
      }

      // 5. PRACTICE_VOCABULARY: localConfidence >= 40% AND learningUniqueRatio >= 10%
      if (learningUniqueRatio != null && learningUniqueRatio.compareTo(TEN) >= 0) {
        return RecommendationReasonCode.PRACTICE_VOCABULARY;
      }

      // 6. BALANCED_CHALLENGE: localConfidence >= 40% AND UniqueChallenge >= 15% AND
      // UniqueChallenge <= 30%
      if (uniqueChallenge != null
          && uniqueChallenge.compareTo(FIFTEEN) >= 0
          && uniqueChallenge.compareTo(THIRTY) <= 0) {
        return RecommendationReasonCode.BALANCED_CHALLENGE;
      }

      // 7. MORE_CHALLENGING: localConfidence >= 40% AND UniqueChallenge > 30%
      if (uniqueChallenge != null && uniqueChallenge.compareTo(THIRTY) > 0) {
        return RecommendationReasonCode.MORE_CHALLENGING;
      }
    }

    // 8. DISCOVERY fallback (e.g. 25 <= localConfidence < 40)
    return RecommendationReasonCode.DISCOVERY;
  }

  public RecommendationReasonCode evaluate(
      VocabularyBreakdown breakdown,
      BigDecimal classificationConfidencePercentage,
      ReadingProgressStatus progressStatus) {
    if (progressStatus == ReadingProgressStatus.IN_PROGRESS) {
      return RecommendationReasonCode.CONTINUE_READING;
    }
    if (breakdown == null || breakdown.uniqueWords() == 0) {
      return RecommendationReasonCode.DISCOVERY;
    }
    var relevantWords = breakdown.uniqueWords() - breakdown.ignoredWords();
    if (relevantWords <= 0) {
      return RecommendationReasonCode.DISCOVERY;
    }
    var known = percentage(breakdown.knownWords(), relevantWords);
    var learning = percentage(breakdown.learningWords(), relevantWords);
    var challenge =
        percentage(breakdown.explicitNewWords() + breakdown.unclassifiedWords(), relevantWords);
    return evaluate(
        progressStatus,
        false,
        false,
        classificationConfidencePercentage,
        known,
        learning,
        challenge);
  }

  private BigDecimal percentage(int count, int total) {
    return BigDecimal.valueOf(count)
        .multiply(new BigDecimal("100"))
        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
  }
}
