package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReadingAnalyzerTest {

  @Test
  void preservesTokenOrderAndMarksEveryKnownOccurrence() {
    var tokens =
        List.of(
            new TextWordProcessor.Token("Hello", "hello"),
            new TextWordProcessor.Token("world", "world"),
            new TextWordProcessor.Token("HELLO", "hello"));

    var analysis = new ReadingAnalyzer().analyze(tokens, Set.of("hello"));

    assertThat(analysis.words())
        .extracting(word -> word.value() + ":" + word.known())
        .containsExactly("Hello:true", "world:false", "HELLO:true");
  }
}
