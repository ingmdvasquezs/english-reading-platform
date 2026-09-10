package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.domain.model.VocabularyStatus;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VocabularyCompatibilityCalculatorTest {
  private final VocabularyCompatibilityCalculator compatibility =
      new VocabularyCompatibilityCalculator();
  private final PlatformReadingRecommendationCalculator recommendation =
      new PlatformReadingRecommendationCalculator(compatibility);

  @Test
  void libraryAndRecommendationShareExactlyTheSameCompatibilityMath() {
    var words = Set.of("known", "learning", "new", "ignored", "unclassified");
    var statuses =
        Map.of(
            "known", VocabularyStatus.KNOWN,
            "learning", VocabularyStatus.LEARNING,
            "new", VocabularyStatus.NEW,
            "ignored", VocabularyStatus.IGNORED);

    var library = compatibility.calculate(words, statuses);
    var home = recommendation.calculate(TestReadings.platform(), words, statuses);

    assertThat(library.vocabularyFitPercentage())
        .isEqualByComparingTo(home.vocabularyFitPercentage());
    assertThat(library.classificationConfidencePercentage())
        .isEqualByComparingTo(home.classificationConfidencePercentage());
    assertThat(library.breakdown().uniqueWords()).isEqualTo(home.uniqueWords());
    assertThat(library.breakdown().knownWords()).isEqualTo(home.knownWords());
    assertThat(library.breakdown().learningWords()).isEqualTo(home.learningWords());
    assertThat(library.breakdown().explicitNewWords()).isEqualTo(home.explicitNewWords());
    assertThat(library.breakdown().ignoredWords()).isEqualTo(home.ignoredWords());
    assertThat(library.breakdown().unclassifiedWords()).isEqualTo(home.unclassifiedWords());
  }

  @Test
  void preservesEveryBoundaryCaseIncludingEmptyInput() {
    assertFit("100.00", Set.of("word"), Map.of("word", VocabularyStatus.KNOWN));
    assertFit("50.00", Set.of("word"), Map.of("word", VocabularyStatus.LEARNING));
    assertFit("0.00", Set.of("word"), Map.of("word", VocabularyStatus.NEW));
    assertFit("100.00", Set.of("word"), Map.of("word", VocabularyStatus.IGNORED));
    assertFit("30.00", Set.of("word"), Map.of());
    var empty = compatibility.calculate(Set.of(), Map.of());
    assertThat(empty.vocabularyFitPercentage()).isEqualByComparingTo("100.00");
    assertThat(empty.classificationConfidencePercentage()).isEqualByComparingTo("0.00");
  }

  private void assertFit(
      String expected, Set<String> words, Map<String, VocabularyStatus> statuses) {
    assertThat(compatibility.calculate(words, statuses).vocabularyFitPercentage())
        .isEqualByComparingTo(expected);
  }
}
