package com.soap.soap.application.service;

import com.soap.soap.domain.model.AnalyzedWord;
import com.soap.soap.domain.model.ReadingAnalysis;
import com.soap.soap.domain.model.VocabularyStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ReadingAnalyzer {

  public ReadingAnalysis analyze(
      List<TextWordProcessor.Token> tokens, Map<String, VocabularyStatus> statuses) {
    var words =
        tokens.stream()
            .map(
                token ->
                    new AnalyzedWord(
                        token.value(),
                        token.normalizedValue(),
                        statuses.getOrDefault(token.normalizedValue(), VocabularyStatus.NEW)))
            .toList();
    var frequencies =
        words.stream()
            .filter(word -> word.status() == VocabularyStatus.NEW)
            .map(AnalyzedWord::normalizedValue)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .sorted(
                Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                    .thenComparing(Map.Entry.comparingByKey()))
            .map(entry -> new ReadingAnalysis.WordFrequency(entry.getKey(), entry.getValue()))
            .toList();
    return new ReadingAnalysis(words, frequencies);
  }
}
