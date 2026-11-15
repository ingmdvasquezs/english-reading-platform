package com.soap.soap.application.model;

import java.nio.charset.StandardCharsets;

public record InputLimits(
    int maxNameCharacters,
    int maxEmailCharacters,
    int maxPasswordCharacters,
    int maxTitleCharacters,
    int maxReadingContentBytes,
    int maxWordCharacters,
    int maxOnboardingWords) {

  /**
   * BCrypt only accepts the first 72 bytes of a password and Spring Security rejects longer input
   * with an exception. The character limit alone is not enough because multibyte UTF-8 characters
   * can exceed 72 bytes well below {@link #maxPasswordCharacters()}.
   */
  public static final int MAX_PASSWORD_UTF8_BYTES = 72;

  public static InputLimits defaults() {
    return new InputLimits(100, 254, 128, 200, 1_048_576, 100, 100);
  }

  public static boolean exceedsPasswordHashingLimit(String password) {
    return password != null
        && password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_UTF8_BYTES;
  }
}
