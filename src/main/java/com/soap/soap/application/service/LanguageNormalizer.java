package com.soap.soap.application.service;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class LanguageNormalizer {

  public String normalize(String language) {
    if (language == null || language.isBlank()) {
      throw new IllegalArgumentException("Language must not be blank");
    }
    return language.trim().toLowerCase(Locale.ROOT);
  }
}
