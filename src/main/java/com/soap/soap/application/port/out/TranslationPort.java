package com.soap.soap.application.port.out;

public interface TranslationPort {
  String translate(String text, String sourceLanguage, String targetLanguage);
}
