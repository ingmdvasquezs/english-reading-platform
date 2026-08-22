package com.soap.soap.application.model;

import java.util.List;

public record WordLookup(
    String word,
    String normalizedWord,
    String translation,
    String phonetic,
    String audioUrl,
    List<WordMeaning> meanings) {
  public WordLookup {
    meanings = List.copyOf(meanings);
  }
}
