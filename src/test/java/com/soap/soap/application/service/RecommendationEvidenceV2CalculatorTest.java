package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.domain.model.VocabularyStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecommendationEvidenceV2CalculatorTest {
  private final TextWordProcessor words = new TextWordProcessor();
  private final RecommendationEvidenceV2Calculator calculator =
      new RecommendationEvidenceV2Calculator();

  @Test
  void keepsTokenEaseSeparateFromUniqueLexicalChallenge() {
    var evidence =
        calculator.calculate(
            words.tokenize("the the the bridge bridge unfamiliar"),
            Map.of("the", VocabularyStatus.KNOWN, "bridge", VocabularyStatus.LEARNING));

    assertThat(evidence.totalTokens()).isEqualTo(6);
    assertThat(evidence.knownTokens()).isEqualTo(3);
    assertThat(evidence.learningTokens()).isEqualTo(2);
    assertThat(evidence.uniqueWords()).isEqualTo(3);
    assertThat(evidence.knownUniqueWords()).isEqualTo(1);
    assertThat(evidence.learningUniqueWords()).isEqualTo(1);
    assertThat(evidence.unclassifiedUniqueWords()).isEqualTo(1);
  }
}
