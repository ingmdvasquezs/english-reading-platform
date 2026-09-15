package com.soap.soap.application.service;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.domain.model.LanguageTag;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LanguageNormalizer {

  public String normalize(String language) {
    if (language == null || language.isBlank()) {
      throw new InvalidApplicationArgumentException("Language must not be blank");
    }
    try {
      var tag = LanguageTag.of(language.trim().replace('_', '-'));
      var val = tag.value();
      if (val.equalsIgnoreCase("en") || val.toLowerCase(Locale.ROOT).startsWith("en-")) {
        return "en";
      }
      return val;
    } catch (IllegalArgumentException e) {
      throw new InvalidApplicationArgumentException("Language format is invalid", e);
    }
  }

  public Set<String> equivalentLanguages(String language) {
    var normalized = normalize(language);
    return normalized.equals("en") ? Set.of("en", "en-us", "en-gb") : Set.of(normalized);
  }
}
