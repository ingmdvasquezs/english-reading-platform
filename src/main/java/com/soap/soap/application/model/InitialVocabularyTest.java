package com.soap.soap.application.model;

import java.util.List;

public record InitialVocabularyTest(String testId, String text, List<String> selectableWords) {
  public InitialVocabularyTest {
    selectableWords = List.copyOf(selectableWords);
  }
}
