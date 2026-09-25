package com.soap.soap.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
    int maximumBuckets,
    PolicyProperties login,
    PolicyProperties register,
    PolicyProperties lookup,
    PolicyProperties analyze,
    PolicyProperties reader) {

  public static final int DEFAULT_MAXIMUM_BUCKETS = 10000;
  public static final PolicyProperties DEFAULT_LOGIN =
      new PolicyProperties(10, Duration.ofMinutes(1));
  public static final PolicyProperties DEFAULT_REGISTER =
      new PolicyProperties(5, Duration.ofHours(1));
  public static final PolicyProperties DEFAULT_LOOKUP =
      new PolicyProperties(30, Duration.ofMinutes(1));
  public static final PolicyProperties DEFAULT_ANALYZE =
      new PolicyProperties(10, Duration.ofMinutes(1));
  public static final PolicyProperties DEFAULT_READER =
      new PolicyProperties(20, Duration.ofMinutes(1));

  public RateLimitProperties {
    if (maximumBuckets <= 0) {
      maximumBuckets = DEFAULT_MAXIMUM_BUCKETS;
    }
    login = mergePolicy(login, DEFAULT_LOGIN);
    register = mergePolicy(register, DEFAULT_REGISTER);
    lookup = mergePolicy(lookup, DEFAULT_LOOKUP);
    analyze = mergePolicy(analyze, DEFAULT_ANALYZE);
    reader = mergePolicy(reader, DEFAULT_READER);
  }

  private static PolicyProperties mergePolicy(PolicyProperties actual, PolicyProperties fallback) {
    if (actual == null) {
      return fallback;
    }
    int requests = actual.requests() > 0 ? actual.requests() : fallback.requests();
    Duration window = actual.window() != null ? actual.window() : fallback.window();
    return new PolicyProperties(requests, window);
  }

  public record PolicyProperties(int requests, Duration window) {}
}
