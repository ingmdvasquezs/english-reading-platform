package com.soap.soap.application.service;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class LanguageNormalizer {
  private static final Pattern SUPPORTED_LANGUAGE =
      Pattern.compile("[A-Za-z]{2,3}(?:-[A-Za-z]{2,3})?");

  public String normalize(String language) {
    if (language == null || language.isBlank()) {
      throw new InvalidApplicationArgumentException("Language must not be blank");
    }
    var trimmed = language.trim();
    if (!SUPPORTED_LANGUAGE.matcher(trimmed).matches()) {
      throw new InvalidApplicationArgumentException("Language format is invalid");
    }
    return trimmed.toLowerCase(Locale.ROOT);
  }
}
