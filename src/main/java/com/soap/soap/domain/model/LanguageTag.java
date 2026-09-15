package com.soap.soap.domain.model;

import java.io.Serializable;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Open Value Object representing a canonical BCP-47 language tag (RFC 5646).
 *
 * <p>Preserves all valid subtags (scripts, numeric regions, countries, variants, and extensions)
 * without destructive collapsing. Canonicalizes casing deterministically (e.g. "en-us" -> "en-US",
 * "zh-hans-cn" -> "zh-Hans-CN", "sl-rozaj" -> "sl-rozaj").
 */
public record LanguageTag(String value) implements Serializable, Comparable<LanguageTag> {

  public static final int MAX_LENGTH = 50;
  private static final Pattern BCP47_SYNTAX =
      Pattern.compile("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$");

  public LanguageTag {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Language tag must not be blank");
    }
    String trimmed = value.trim();
    if (trimmed.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Language tag exceeds maximum length of " + MAX_LENGTH + " characters: " + trimmed);
    }
    if (!BCP47_SYNTAX.matcher(trimmed).matches()) {
      throw new IllegalArgumentException("Invalid BCP-47 language tag syntax: " + trimmed);
    }
    try {
      // Lowercase before passing to Locale.Builder so that variants are normalized per IANA
      // registry
      Locale loc = new Locale.Builder().setLanguageTag(trimmed.toLowerCase(Locale.ROOT)).build();
      String canonical = loc.toLanguageTag();
      if ("und".equals(canonical) && !"und".equalsIgnoreCase(trimmed)) {
        throw new IllegalArgumentException("Unsupported or ill-formed language tag: " + trimmed);
      }
      value = canonical;
    } catch (IllformedLocaleException e) {
      throw new IllegalArgumentException("Invalid BCP-47 language tag: " + trimmed, e);
    }
  }

  public static LanguageTag of(String raw) {
    return new LanguageTag(raw);
  }

  public String primaryLanguage() {
    int dash = value.indexOf('-');
    return dash < 0 ? value : value.substring(0, dash);
  }

  public Locale toLocale() {
    return Locale.forLanguageTag(value);
  }

  @Override
  public int compareTo(LanguageTag o) {
    return this.value.compareTo(o.value);
  }

  @Override
  public String toString() {
    return value;
  }
}
