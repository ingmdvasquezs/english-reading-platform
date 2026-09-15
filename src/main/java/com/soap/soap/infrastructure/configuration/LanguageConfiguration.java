package com.soap.soap.infrastructure.configuration;

import com.soap.soap.application.policy.LanguageAvailabilityPolicy;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link LanguageAvailabilityPolicy} bean from product configuration.
 *
 * <p>Configuration keys:
 *
 * <ul>
 *   <li>{@code app.languages.enabled-learning} — comma-separated BCP-47 tags for learning
 *   <li>{@code app.languages.enabled-content} — comma-separated BCP-47 tags for content
 * </ul>
 *
 * <p>Both default to {@code en}. Override via environment variables {@code
 * LANGUAGES_ENABLED_LEARNING} and {@code LANGUAGES_ENABLED_CONTENT} to enable additional languages
 * without code changes.
 */
@Configuration
class LanguageConfiguration {

  @Bean
  LanguageAvailabilityPolicy languageAvailabilityPolicy(
      @Value("${app.languages.enabled-learning:en}") String enabledLearning,
      @Value("${app.languages.enabled-content:en}") String enabledContent) {
    return new LanguageAvailabilityPolicy(
        Arrays.stream(enabledLearning.split(","))
            .map(String::strip)
            .filter(v -> !v.isEmpty())
            .collect(Collectors.toSet()),
        Arrays.stream(enabledContent.split(","))
            .map(String::strip)
            .filter(v -> !v.isEmpty())
            .collect(Collectors.toSet()));
  }
}
