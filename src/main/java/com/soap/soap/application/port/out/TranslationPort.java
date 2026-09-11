package com.soap.soap.application.port.out;

import java.util.List;

public interface TranslationPort {
  String translate(String text, String sourceLanguage, String targetLanguage);

  List<String> translateBatch(List<String> texts, String sourceLanguage, String targetLanguage);
}
