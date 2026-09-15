package com.soap.soap.infrastructure.configuration;

import com.soap.soap.application.model.DocumentImportLimits;
import com.soap.soap.application.policy.LanguageAvailabilityPolicy;
import com.soap.soap.application.service.DocumentLanguagePolicy;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@org.springframework.scheduling.annotation.EnableScheduling
public class DocumentImportConfiguration {

  @Bean
  DocumentImportLimits documentImportLimits(
      @Value("${app.documents.max-source-bytes:52428800}") long maxSourceBytes,
      @Value("${app.documents.max-zip-entries:2000}") int maxZipEntries,
      @Value("${app.documents.max-entry-bytes:10485760}") long maxEntryBytes,
      @Value("${app.documents.max-expanded-bytes:157286400}") long maxExpandedBytes,
      @Value("${app.documents.max-compression-ratio:100}") double maxCompressionRatio,
      @Value("${app.documents.max-cover-bytes:10485760}") long maxCoverBytes,
      @Value("${app.documents.max-cover-pixels:25000000}") int maxCoverPixels,
      @Value("${app.documents.max-pdf-pages:1000}") int maxPdfPages,
      @Value("${app.documents.max-extracted-characters:5000000}") int maxExtractedCharacters) {
    return new DocumentImportLimits(
        maxSourceBytes,
        maxZipEntries,
        maxEntryBytes,
        maxExpandedBytes,
        maxCompressionRatio,
        maxCoverBytes,
        maxCoverPixels,
        maxPdfPages,
        maxExtractedCharacters);
  }

  @Bean
  Path documentStorageRoot(@Value("${app.documents.storage-root:.private-assets}") String root) {
    return Path.of(root).toAbsolutePath().normalize();
  }

  /**
   * Wires {@link DocumentLanguagePolicy} using the shared {@link LanguageAvailabilityPolicy} bean
   * as the single source of truth for which content languages are currently enabled.
   *
   * <p>Previously read from {@code app.documents.supported-languages}. That key is now removed —
   * use {@code app.languages.enabled-content} (or env var {@code LANGUAGES_ENABLED_CONTENT}).
   */
  @Bean
  DocumentLanguagePolicy documentLanguagePolicy(
      LanguageAvailabilityPolicy languageAvailabilityPolicy) {
    return new DocumentLanguagePolicy(languageAvailabilityPolicy);
  }
}
