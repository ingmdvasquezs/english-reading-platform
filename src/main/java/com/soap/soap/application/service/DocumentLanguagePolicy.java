package com.soap.soap.application.service;

import com.soap.soap.application.exception.DocumentImportException;
import java.util.Locale;
import java.util.Set;

public class DocumentLanguagePolicy {
  private final Set<String> supported;

  public DocumentLanguagePolicy(Set<String> supported) {
    this.supported =
        supported.stream()
            .map(value -> primary(value.strip()))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  public void requireSupported(String language) {
    if (!supported.contains(primary(language))) {
      throw new DocumentImportException(
          DocumentImportException.Reason.UNSUPPORTED_LANGUAGE,
          "Document language is not supported");
    }
  }

  private static String primary(String value) {
    return Locale.forLanguageTag(value).getLanguage().toLowerCase(Locale.ROOT);
  }
}
