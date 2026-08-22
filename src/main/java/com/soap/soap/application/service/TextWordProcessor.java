package com.soap.soap.application.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TextWordProcessor {

  private static final Pattern WORD_PATTERN =
      Pattern.compile("[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)*");

  public String normalize(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Word must not be blank");
    }
    return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
        .replace('’', '\'')
        .toLowerCase(Locale.ROOT);
  }

  public List<Token> tokenize(String text) {
    var matcher = WORD_PATTERN.matcher(text);
    var tokens = new java.util.ArrayList<Token>();
    while (matcher.find()) {
      var value = matcher.group();
      tokens.add(new Token(value, normalize(value)));
    }
    return List.copyOf(tokens);
  }

  Pattern wordPattern() {
    return WORD_PATTERN;
  }

  public record Token(String value, String normalizedValue) {}
}
