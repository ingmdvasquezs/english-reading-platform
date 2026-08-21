package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.domain.model.VocabularyStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReadingAnalyzerTest {

  @Test
  void preservesTokenOrderAndMarksEveryKnownOccurrence() {
    var tokens =
        List.of(
            new TextWordProcessor.Token("Hello", "hello"),
            new TextWordProcessor.Token("world", "world"),
            new TextWordProcessor.Token("HELLO", "hello"));

    var analysis = new ReadingAnalyzer().analyze(tokens, Map.of("hello", VocabularyStatus.KNOWN));

    assertThat(analysis.words())
        .extracting(word -> word.value() + ":" + word.status())
        .containsExactly("Hello:KNOWN", "world:NEW", "HELLO:KNOWN");
    assertThat(analysis.frequentUnknownWords())
        .containsExactly(new com.soap.soap.domain.model.ReadingAnalysis.WordFrequency("world", 1));
  }

  @Test
  void separatesEveryStatusAndExcludesIgnoredTokensFromCoverage() {
    var tokens =
        List.of(
            new TextWordProcessor.Token("Known", "known"),
            new TextWordProcessor.Token("learning", "learning"),
            new TextWordProcessor.Token("ignored", "ignored"),
            new TextWordProcessor.Token("unknown", "unknown"),
            new TextWordProcessor.Token("UNKNOWN", "unknown"));
    var analysis =
        new ReadingAnalyzer()
            .analyze(
                tokens,
                Map.of(
                    "known", VocabularyStatus.KNOWN,
                    "learning", VocabularyStatus.LEARNING,
                    "ignored", VocabularyStatus.IGNORED));

    assertThat(analysis.knownTokens()).isEqualTo(1);
    assertThat(analysis.learningTokens()).isEqualTo(1);
    assertThat(analysis.ignoredTokens()).isEqualTo(1);
    assertThat(analysis.unknownTokens()).isEqualTo(2);
    assertThat(analysis.uniqueWords()).isEqualTo(4);
    assertThat(analysis.personalizedCoveragePercentage()).isEqualTo(25.0);
    assertThat(analysis.frequentUnknownWords())
        .containsExactly(
            new com.soap.soap.domain.model.ReadingAnalysis.WordFrequency("unknown", 2));
  }
}
