package com.soap.soap.application.policy;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.domain.model.LanguageTag;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Product-level gate that controls which languages are currently available for learning and
 * content.
 *
 * <p>The underlying architecture supports any BCP-47 language ({@link LanguageTag} is open). This
 * policy is the single configuration-driven source of truth for which languages the product
 * currently offers, independently of what the domain can represent.
 *
 * <p>Enabling a new language is an operational change (configuration), not a code change.
 */
public class LanguageAvailabilityPolicy {

  private final Set<String> enabledLearning;
  private final Set<String> enabledContent;

  public LanguageAvailabilityPolicy(Set<String> enabledLearning, Set<String> enabledContent) {
    this.enabledLearning =
        enabledLearning.stream()
            .map(v -> LanguageTag.of(v.strip()).value())
            .collect(Collectors.toUnmodifiableSet());
    this.enabledContent =
        enabledContent.stream()
            .map(v -> LanguageTag.of(v.strip()).value())
            .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Asserts that the given language is currently enabled for learning.
   *
   * @throws InvalidApplicationArgumentException if the language is not available
   */
  public void requireLearningLanguageEnabled(LanguageTag tag) {
    if (!enabledLearning.contains(tag.value())) {
      throw new InvalidApplicationArgumentException(
          "Learning language '" + tag.value() + "' is not currently supported");
    }
  }

  /**
   * Asserts that the given language is currently enabled for content.
   *
   * @throws InvalidApplicationArgumentException if the language is not available
   */
  public void requireContentLanguageEnabled(LanguageTag tag) {
    if (!enabledContent.contains(tag.value())) {
      throw new InvalidApplicationArgumentException(
          "Content language '" + tag.value() + "' is not currently supported");
    }
  }

  /** Returns true if the given language is currently enabled for learning. */
  public boolean isLearningLanguageEnabled(LanguageTag tag) {
    return enabledLearning.contains(tag.value());
  }

  /** Returns true if the given language is currently enabled for content. */
  public boolean isContentLanguageEnabled(LanguageTag tag) {
    return enabledContent.contains(tag.value());
  }
}
