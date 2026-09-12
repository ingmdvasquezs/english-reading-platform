package com.soap.soap.application.service;

import com.soap.soap.application.model.VocabularyBreakdown;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.RecommendationReasonCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class RecommendationReasonEvaluator {
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
  private static final BigDecimal TWENTY = new BigDecimal("20.00");
  private static final BigDecimal FORTY = new BigDecimal("40.00");
  private static final BigDecimal FIFTY = new BigDecimal("50.00");
  private static final BigDecimal SIXTY_FIVE = new BigDecimal("65.00");
  private static final BigDecimal TEN = new BigDecimal("10.00");
  private static final BigDecimal FIFTEEN = new BigDecimal("15.00");
  private static final BigDecimal THIRTY = new BigDecimal("30.00");

  public RecommendationReasonCode evaluate(
      VocabularyBreakdown breakdown,
      BigDecimal classificationConfidencePercentage,
      ReadingProgressStatus progressStatus) {
    if (progressStatus == ReadingProgressStatus.IN_PROGRESS) {
      return RecommendationReasonCode.CONTINUE_READING;
    }

    var confidence =
        classificationConfidencePercentage != null
            ? classificationConfidencePercentage
            : BigDecimal.ZERO;

    if (breakdown == null
        || breakdown.uniqueWords() == 0
        || (breakdown.uniqueWords() - breakdown.ignoredWords()) <= 0
        || confidence.compareTo(TWENTY) < 0) {
      return RecommendationReasonCode.DISCOVERY;
    }

    var relevantWords = breakdown.uniqueWords() - breakdown.ignoredWords();
    var known = percentage(breakdown.knownWords(), relevantWords).setScale(2, RoundingMode.HALF_UP);
    var learning =
        percentage(breakdown.learningWords(), relevantWords).setScale(2, RoundingMode.HALF_UP);
    var explicitNew =
        percentage(breakdown.explicitNewWords(), relevantWords).setScale(2, RoundingMode.HALF_UP);
    var unclassified =
        percentage(breakdown.unclassifiedWords(), relevantWords).setScale(2, RoundingMode.HALF_UP);
    var challenge = explicitNew.add(unclassified).setScale(2, RoundingMode.HALF_UP);

    if (confidence.compareTo(FIFTY) >= 0 && known.compareTo(SIXTY_FIVE) >= 0) {
      return RecommendationReasonCode.HIGH_VOCABULARY_MATCH;
    }

    if (confidence.compareTo(FORTY) >= 0 && learning.compareTo(TEN) >= 0) {
      return RecommendationReasonCode.PRACTICE_VOCABULARY;
    }

    if (confidence.compareTo(FORTY) >= 0
        && challenge.compareTo(FIFTEEN) >= 0
        && challenge.compareTo(THIRTY) <= 0) {
      return RecommendationReasonCode.BALANCED_CHALLENGE;
    }

    if (confidence.compareTo(FORTY) >= 0 && challenge.compareTo(THIRTY) > 0) {
      return RecommendationReasonCode.MORE_CHALLENGING;
    }

    return RecommendationReasonCode.DISCOVERY;
  }

  private BigDecimal percentage(int count, int total) {
    return BigDecimal.valueOf(count)
        .multiply(ONE_HUNDRED)
        .divide(BigDecimal.valueOf(total), 8, RoundingMode.HALF_UP);
  }
}
