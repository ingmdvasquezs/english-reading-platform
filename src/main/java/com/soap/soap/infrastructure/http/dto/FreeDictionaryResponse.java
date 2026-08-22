package com.soap.soap.infrastructure.http.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FreeDictionaryResponse(
    String word, String phonetic, List<Phonetic> phonetics, List<Meaning> meanings) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Phonetic(String text, String audio) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Meaning(String partOfSpeech, List<Definition> definitions) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Definition(String definition, String example) {}
}
