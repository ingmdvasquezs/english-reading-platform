package com.soap.soap.application.service;

import com.soap.soap.application.model.VocabularyCompatibility;
import com.soap.soap.domain.model.VocabularyStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class VocabularyCompatibilityCalculator {
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
  private static final BigDecimal LEARNING_WEIGHT = new BigDecimal("0.50");
  private static final BigDecimal NEW_WEIGHT = new BigDecimal("1.00");
  private static final BigDecimal UNCLASSIFIED_WEIGHT = new BigDecimal("0.70");

  private final VocabularyBreakdownCalculator breakdownCalculator;

  public VocabularyCompatibilityCalculator() {
    this(new VocabularyBreakdownCalculator());
  }

  public VocabularyCompatibilityCalculator(VocabularyBreakdownCalculator breakdownCalculator) {
    this.breakdownCalculator = breakdownCalculator;
  }

  public VocabularyCompatibility calculate(
      Set<String> uniqueWords, Map<String, VocabularyStatus> explicitStatuses) {
    var breakdown = breakdownCalculator.calculate(uniqueWords, explicitStatuses);
    var total = breakdown.uniqueWords();
    var fit =
        total == 0
            ? ONE_HUNDRED.setScale(2)
            : ONE_HUNDRED.subtract(
                LEARNING_WEIGHT
                    .multiply(BigDecimal.valueOf(breakdown.learningWords()))
                    .add(NEW_WEIGHT.multiply(BigDecimal.valueOf(breakdown.explicitNewWords())))
                    .add(
                        UNCLASSIFIED_WEIGHT.multiply(
                            BigDecimal.valueOf(breakdown.unclassifiedWords())))
                    .multiply(ONE_HUNDRED)
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP));
    var confidence =
        total == 0
            ? BigDecimal.ZERO.setScale(2)
            : BigDecimal.valueOf(total - breakdown.unclassifiedWords())
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    return new VocabularyCompatibility(breakdown, fit, confidence);
  }
}
