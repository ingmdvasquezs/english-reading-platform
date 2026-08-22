package com.soap.soap.application.model;

import java.util.List;

public record DictionaryEntry(
    String word, String phonetic, String audioUrl, List<WordMeaning> meanings) {
  public DictionaryEntry {
    meanings = List.copyOf(meanings);
  }
}
