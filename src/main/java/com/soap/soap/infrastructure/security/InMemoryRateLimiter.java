package com.soap.soap.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-instance fixed-window limiter. Production multi-instance deployments need a gateway limiter.
 */
public class InMemoryRateLimiter {
  private final Clock clock;
  private final int maximumBuckets;
  private final Map<String, Window> windows;

  public InMemoryRateLimiter(Clock clock, int maximumBuckets) {
    this.clock = clock;
    this.maximumBuckets = maximumBuckets;
    this.windows =
        new LinkedHashMap<>(128, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Window> eldest) {
            return size() > InMemoryRateLimiter.this.maximumBuckets;
          }
        };
  }

  public synchronized boolean tryAcquire(
      RateLimitPolicy policy, String subject, int maximumRequests, Duration windowDuration) {
    var key = policy.name() + ':' + subject;
    var now = clock.millis();
    var current = windows.get(key);
    if (current == null || now - current.startedAtMillis() >= windowDuration.toMillis()) {
      windows.put(key, new Window(now, 1));
      return true;
    }
    if (current.requests() >= maximumRequests) {
      return false;
    }
    windows.put(key, new Window(current.startedAtMillis(), current.requests() + 1));
    return true;
  }

  private record Window(long startedAtMillis, int requests) {}
}
