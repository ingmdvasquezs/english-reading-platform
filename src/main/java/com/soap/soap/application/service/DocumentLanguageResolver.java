package com.soap.soap.application.service;

import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.exception.DocumentImportException.Reason;
import java.util.IllformedLocaleException;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DocumentLanguageResolver {

  public String resolve(String override, String declared) {
    var candidate = override == null || override.isBlank() ? declared : override;
    if (candidate == null || candidate.isBlank()) {
      throw new DocumentImportException(Reason.LANGUAGE_REQUIRED, "Document language is required");
    }
    try {
      var locale = new Locale.Builder().setLanguageTag(candidate.strip().replace('_', '-')).build();
      if (locale.getLanguage().isBlank() || "und".equals(locale.toLanguageTag())) {
        throw invalid();
      }
      return locale.toLanguageTag();
    } catch (IllformedLocaleException exception) {
      throw invalid();
    }
  }

  private DocumentImportException invalid() {
    return new DocumentImportException(Reason.LANGUAGE_REQUIRED, "Document language is invalid");
  }
}
