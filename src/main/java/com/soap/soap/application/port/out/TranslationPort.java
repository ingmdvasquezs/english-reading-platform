package com.soap.soap.application.port.out;

import com.soap.soap.application.model.LexicalTranslationCandidate;
import java.util.List;

public interface TranslationPort {
  String translate(String text, String sourceLanguage, String targetLanguage);

  List<String> translateBatch(List<String> texts, String sourceLanguage, String targetLanguage);

  default List<LexicalTranslationCandidate> lookupLexical(
      String word, String sourceLanguage, String targetLanguage) {
    return List.of();
  }
}
