package com.soap.soap.application.service;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class LanguageNormalizer {
  private static final Pattern SUPPORTED_LANGUAGE =
      Pattern.compile("[A-Za-z]{2,3}(?:[-_][A-Za-z]{2,3})?");

  public String normalize(String language) {
    if (language == null || language.isBlank()) {
      throw new InvalidApplicationArgumentException("Language must not be blank");
    }
    var trimmed = language.trim();
    if (!SUPPORTED_LANGUAGE.matcher(trimmed).matches()) {
      throw new InvalidApplicationArgumentException("Language format is invalid");
    }
    var normalized = trimmed.toLowerCase(Locale.ROOT);
    if (normalized.equals("en") || normalized.startsWith("en-") || normalized.startsWith("en_")) {
      return "en";
    }
    return normalized;
  }

  public Set<String> equivalentLanguages(String language) {
    var normalized = normalize(language);
    return normalized.equals("en") ? Set.of("en", "en-us", "en-gb") : Set.of(normalized);
  }
}
