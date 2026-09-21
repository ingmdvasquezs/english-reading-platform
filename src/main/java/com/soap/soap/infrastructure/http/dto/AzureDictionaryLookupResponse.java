package com.soap.soap.infrastructure.http.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AzureDictionaryLookupResponse(
    String normalizedSource, String displaySource, List<TranslationItem> translations) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record TranslationItem(
      String normalizedTarget,
      String displayTarget,
      String posTag,
      double confidence,
      String prefixWord,
      List<BackTranslationItem> backTranslations) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record BackTranslationItem(
      String normalizedText, String displayText, int numExamples, int frequencyCount) {}
}
