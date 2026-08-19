package com.soap.soap.application.service;

import com.soap.soap.domain.model.AnalyzedWord;
import com.soap.soap.domain.model.ReadingAnalysis;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ReadingAnalyzer {

  public ReadingAnalysis analyze(List<TextWordProcessor.Token> tokens, Set<String> knownValues) {
    var words =
        tokens.stream()
            .map(
                token ->
                    new AnalyzedWord(
                        token.value(),
                        token.normalizedValue(),
                        knownValues.contains(token.normalizedValue())))
            .toList();
    return new ReadingAnalysis(words);
  }
}
