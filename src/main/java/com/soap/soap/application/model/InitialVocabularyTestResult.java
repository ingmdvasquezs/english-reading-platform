package com.soap.soap.application.model;

import java.util.List;

public record InitialVocabularyTestResult(
    int confirmedWordCount, List<String> knownWords, double markedPercentage) {
  public InitialVocabularyTestResult {
    knownWords = List.copyOf(knownWords);
  }
}
