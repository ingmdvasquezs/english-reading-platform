package com.soap.soap.infrastructure.http.configuration;

import java.time.Duration;

public record TranslationClientPolicy(Duration timeout, int maxRetries, Duration retryDelay) {
  public TranslationClientPolicy {
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("Translation timeout must be positive");
    }
    if (maxRetries < 0 || maxRetries > 1) {
      throw new IllegalArgumentException("Translation max retries must be between 0 and 1");
    }
    if (retryDelay == null || retryDelay.isNegative()) {
      throw new IllegalArgumentException("Translation retry delay must not be negative");
    }
  }

  public static TranslationClientPolicy defaults() {
    return new TranslationClientPolicy(Duration.ofSeconds(7), 1, Duration.ofMillis(400));
  }
}
