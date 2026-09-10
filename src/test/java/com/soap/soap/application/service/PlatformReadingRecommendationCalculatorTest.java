package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.VocabularyStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlatformReadingRecommendationCalculatorTest {
  private final PlatformReadingRecommendationCalculator calculator =
      new PlatformReadingRecommendationCalculator();

  @Test
  void appliesEveryApprovedWeightAndKeepsUnclassifiedSeparateFromExplicitNew() {
    var result =
        calculator.calculate(
            reading("mixed"),
            words("known", "learning", "new", "ignored", "absent"),
            Map.of(
                "known", VocabularyStatus.KNOWN,
                "learning", VocabularyStatus.LEARNING,
                "new", VocabularyStatus.NEW,
                "ignored", VocabularyStatus.IGNORED));

    assertThat(result.uniqueWords()).isEqualTo(5);
    assertThat(result.knownWords()).isEqualTo(1);
    assertThat(result.learningWords()).isEqualTo(1);
    assertThat(result.explicitNewWords()).isEqualTo(1);
    assertThat(result.ignoredWords()).isEqualTo(1);
    assertThat(result.unclassifiedWords()).isEqualTo(1);
    assertThat(result.vocabularyFitPercentage()).isEqualByComparingTo("56.00");
    assertThat(result.classificationConfidencePercentage()).isEqualByComparingTo("80.00");
    assertThat(
            result.knownWords()
                + result.learningWords()
                + result.explicitNewWords()
                + result.ignoredWords()
                + result.unclassifiedWords())
        .isEqualTo(result.uniqueWords());
  }

  @Test
  void calculatesTheBoundaryCasesExactly() {
    assertThat(score(words("one"), Map.of("one", VocabularyStatus.KNOWN)))
        .isEqualByComparingTo("100.00");
    assertThat(score(words("one"), Map.of("one", VocabularyStatus.IGNORED)))
        .isEqualByComparingTo("100.00");
    assertThat(score(words("one"), Map.of("one", VocabularyStatus.LEARNING)))
        .isEqualByComparingTo("50.00");
    assertThat(score(words("one"), Map.of("one", VocabularyStatus.NEW)))
        .isEqualByComparingTo("0.00");
    assertThat(score(words("one"), Map.of())).isEqualByComparingTo("30.00");
  }

  @Test
  void anEmptyReadingHasNoFrictionAndNoClassificationConfidence() {
    var result = calculator.calculate(reading("empty"), Set.of(), Map.of());

    assertThat(result.uniqueWords()).isZero();
    assertThat(result.vocabularyFitPercentage()).isEqualByComparingTo("100.00");
    assertThat(result.classificationConfidencePercentage()).isEqualByComparingTo("0.00");
  }

  private BigDecimal score(Set<String> words, Map<String, VocabularyStatus> statuses) {
    return calculator.calculate(reading("test"), words, statuses).vocabularyFitPercentage();
  }

  private Set<String> words(String... values) {
    return new LinkedHashSet<>(java.util.List.of(values));
  }

  private Reading reading(String title) {
    return new Reading(
        UUID.randomUUID(),
        null,
        title,
        title,
        "en",
        LocalDateTime.parse("2026-08-29T12:00:00"),
        ReadingOrigin.PLATFORM,
        EditorialLevel.A1,
        "Test");
  }
}
