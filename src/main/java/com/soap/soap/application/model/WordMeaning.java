package com.soap.soap.application.model;

import java.util.List;

public record WordMeaning(String partOfSpeech, List<WordDefinition> definitions) {
  public WordMeaning {
    definitions = List.copyOf(definitions);
  }
}
