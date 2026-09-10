package com.soap.soap.application.service;

import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.VocabularyStatus;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PlatformReadingRecommendationCalculator {
  private final VocabularyCompatibilityCalculator compatibilityCalculator;

  public PlatformReadingRecommendationCalculator() {
    this(new VocabularyCompatibilityCalculator());
  }

  @Autowired
  public PlatformReadingRecommendationCalculator(
      VocabularyCompatibilityCalculator compatibilityCalculator) {
    this.compatibilityCalculator = compatibilityCalculator;
  }

  public RecommendedPlatformReading calculate(
      Reading reading, Set<String> uniqueWords, Map<String, VocabularyStatus> explicitStatuses) {
    var compatibility = compatibilityCalculator.calculate(uniqueWords, explicitStatuses);
    var breakdown = compatibility.breakdown();

    return new RecommendedPlatformReading(
        reading.id(),
        reading.title(),
        reading.language(),
        reading.editorialLevel(),
        reading.category(),
        reading.createdAt(),
        breakdown.uniqueWords(),
        breakdown.knownWords(),
        breakdown.learningWords(),
        breakdown.explicitNewWords(),
        breakdown.ignoredWords(),
        breakdown.unclassifiedWords(),
        compatibility.vocabularyFitPercentage(),
        compatibility.classificationConfidencePercentage(),
        null,
        reading.coverKey());
  }
}
