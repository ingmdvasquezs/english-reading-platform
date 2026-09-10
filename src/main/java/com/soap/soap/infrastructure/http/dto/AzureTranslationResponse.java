package com.soap.soap.infrastructure.http.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AzureTranslationResponse(List<Translation> translations) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Translation(String text, String to) {}
}
