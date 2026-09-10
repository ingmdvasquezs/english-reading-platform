package com.soap.soap.infrastructure.http.configuration;

import java.time.Duration;

public record DictionaryClientPolicy(Duration timeout, int maxRetries, Duration retryDelay) {
  public DictionaryClientPolicy {
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("Dictionary timeout must be positive");
    }
    if (maxRetries < 0 || maxRetries > 1) {
      throw new IllegalArgumentException("Dictionary max retries must be between 0 and 1");
    }
    if (retryDelay == null || retryDelay.isNegative()) {
      throw new IllegalArgumentException("Dictionary retry delay must not be negative");
    }
  }

  public static DictionaryClientPolicy defaults() {
    return new DictionaryClientPolicy(Duration.ofSeconds(7), 1, Duration.ofMillis(400));
  }
}
