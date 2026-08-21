package com.soap.soap.domain.model;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record ReadingAnalysis(List<AnalyzedWord> words, List<WordFrequency> frequentUnknownWords) {

  public ReadingAnalysis {
    words = List.copyOf(words);
    frequentUnknownWords = List.copyOf(frequentUnknownWords);
  }

  public int totalTokens() {
    return words.size();
  }

  public long uniqueWords() {
    return words.stream().map(AnalyzedWord::normalizedValue).distinct().count();
  }

  public long knownTokens() {
    return tokenCount(VocabularyStatus.KNOWN);
  }

  public long learningTokens() {
    return tokenCount(VocabularyStatus.LEARNING);
  }

  public long unknownTokens() {
    return tokenCount(VocabularyStatus.NEW);
  }

  public long ignoredTokens() {
    return tokenCount(VocabularyStatus.IGNORED);
  }

  public long knownWords() {
    return uniqueCount(VocabularyStatus.KNOWN);
  }

  public long learningWords() {
    return uniqueCount(VocabularyStatus.LEARNING);
  }

  public long unknownWords() {
    return uniqueCount(VocabularyStatus.NEW);
  }

  public long ignoredWords() {
    return uniqueCount(VocabularyStatus.IGNORED);
  }

  public double personalizedCoveragePercentage() {
    long relevantTokens = totalTokens() - ignoredTokens();
    return relevantTokens == 0 ? 0.0 : ((double) knownTokens() / relevantTokens) * 100.0;
  }

  private long tokenCount(VocabularyStatus status) {
    return words.stream().filter(word -> word.status() == status).count();
  }

  private long uniqueCount(VocabularyStatus status) {
    Set<String> values =
        words.stream()
            .filter(word -> word.status() == status)
            .map(AnalyzedWord::normalizedValue)
            .collect(Collectors.toSet());
    return values.size();
  }

  public record WordFrequency(String normalizedValue, long count) {}
}
