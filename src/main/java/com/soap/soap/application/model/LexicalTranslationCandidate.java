package com.soap.soap.application.model;

import java.util.List;

public record LexicalTranslationCandidate(
    String target, String posTag, double confidence, List<String> backTranslations) {
  public LexicalTranslationCandidate {
    if (backTranslations == null) {
      backTranslations = List.of();
    }
  }
}
