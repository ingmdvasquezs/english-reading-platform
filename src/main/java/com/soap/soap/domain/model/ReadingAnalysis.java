package com.soap.soap.domain.model;

import java.util.List;

public record ReadingAnalysis(List<AnalyzedWord> words) {

  public ReadingAnalysis {
    words = List.copyOf(words);
  }

  public int totalWords() {
    return words.size();
  }

  public long knownWords() {
    return words.stream().filter(AnalyzedWord::known).count();
  }

  public long unknownWords() {
    return totalWords() - knownWords();
  }

  public double comprehensionPercentage() {
    if (words.isEmpty()) {
      return 0.0;
    }

    return ((double) knownWords() / totalWords()) * 100;
  }
}
