package com.soap.soap.application.service;

import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.policy.LanguageAvailabilityPolicy;
import com.soap.soap.domain.model.LanguageTag;
import java.util.Locale;

/**
 * Import-specific language gate for document ingestion (EPUB, PDF).
 *
 * <p>Preserves import semantics:
 *
 * <ul>
 *   <li>Primary-subtag matching: {@code en-US} is accepted when {@code en} is enabled.
 *   <li>Throws {@link DocumentImportException} with reason {@code UNSUPPORTED_LANGUAGE} — distinct
 *       from the {@link com.soap.soap.application.exception.InvalidApplicationArgumentException}
 *       thrown for learning-language violations.
 * </ul>
 *
 * <p>The question "is this language currently enabled for content?" is answered by delegating to
 * {@link LanguageAvailabilityPolicy}, which is the single source of truth for that decision.
 */
public class DocumentLanguagePolicy {

  private final LanguageAvailabilityPolicy availability;

  public DocumentLanguagePolicy(LanguageAvailabilityPolicy availability) {
    this.availability = availability;
  }

  /**
   * Requires that {@code language} (a BCP-47 string, possibly with region subtag) is enabled for
   * content. Uses primary-subtag resolution: {@code en-US} resolves to {@code en}.
   *
   * @throws DocumentImportException with reason {@code UNSUPPORTED_LANGUAGE} if not enabled
   */
  public void requireSupported(String language) {
    var primary = primarySubtag(language);
    try {
      availability.requireContentLanguageEnabled(LanguageTag.of(primary));
    } catch (com.soap.soap.application.exception.InvalidApplicationArgumentException e) {
      throw new DocumentImportException(
          DocumentImportException.Reason.UNSUPPORTED_LANGUAGE,
          "Document language is not supported");
    }
  }

  private static String primarySubtag(String value) {
    return Locale.forLanguageTag(value).getLanguage().toLowerCase(Locale.ROOT);
  }
}
