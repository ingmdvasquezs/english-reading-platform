package com.soap.soap.infrastructure.queue;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.document-import.worker")
public record DocumentImportWorkerProperties(
    Boolean enabled,
    Boolean autoStartup,
    int waitTimeSeconds,
    int visibilityTimeoutSeconds,
    int maxMessages,
    Duration pollDelay,
    Duration errorBackoff,
    Duration leaseDuration,
    Duration heartbeatInterval,
    String workerId,
    Duration stagingCleanupAge) {

  public DocumentImportWorkerProperties {
    if (enabled == null) {
      enabled = Boolean.TRUE;
    }
    if (autoStartup == null) {
      autoStartup = Boolean.TRUE;
    }
    if (waitTimeSeconds == 0) {
      waitTimeSeconds = 20;
    }
    if (waitTimeSeconds < 1 || waitTimeSeconds > 20) {
      throw new IllegalArgumentException(
          "waitTimeSeconds must be between 1 and 20, got: " + waitTimeSeconds);
    }
    if (visibilityTimeoutSeconds == 0) {
      visibilityTimeoutSeconds = 60;
    }
    if (visibilityTimeoutSeconds < 1 || visibilityTimeoutSeconds > 43200) {
      throw new IllegalArgumentException(
          "visibilityTimeoutSeconds must be between 1 and 43200, got: " + visibilityTimeoutSeconds);
    }
    if (maxMessages == 0) {
      maxMessages = 1;
    }
    if (maxMessages != 1) {
      throw new IllegalArgumentException(
          "maxMessages must be 1 for sequential worker, got: " + maxMessages);
    }
    if (pollDelay == null) {
      pollDelay = Duration.ofSeconds(1);
    }
    if (pollDelay.isNegative()) {
      throw new IllegalArgumentException("pollDelay must not be negative");
    }
    if (errorBackoff == null) {
      errorBackoff = Duration.ofSeconds(5);
    }
    if (errorBackoff.isNegative() || errorBackoff.isZero()) {
      throw new IllegalArgumentException("errorBackoff must be positive");
    }
    if (leaseDuration == null) {
      leaseDuration = Duration.ofSeconds(60);
    }
    if (leaseDuration.isNegative() || leaseDuration.isZero()) {
      throw new IllegalArgumentException("leaseDuration must be positive");
    }
    if (heartbeatInterval == null) {
      heartbeatInterval = Duration.ofSeconds(20);
    }
    if (heartbeatInterval.isNegative() || heartbeatInterval.isZero()) {
      throw new IllegalArgumentException("heartbeatInterval must be positive");
    }
    if (stagingCleanupAge == null) {
      stagingCleanupAge = Duration.ofHours(24);
    }
    if (stagingCleanupAge.isNegative() || stagingCleanupAge.isZero()) {
      throw new IllegalArgumentException("stagingCleanupAge must be positive");
    }
    if (heartbeatInterval.multipliedBy(2).compareTo(leaseDuration) > 0) {
      throw new IllegalArgumentException(
          "heartbeatInterval ("
              + heartbeatInterval
              + ") must be at most half of leaseDuration ("
              + leaseDuration
              + ")");
    }
    if (heartbeatInterval.toSeconds() * 2 > visibilityTimeoutSeconds) {
      throw new IllegalArgumentException(
          "heartbeatInterval ("
              + heartbeatInterval
              + ") must be at most half of visibilityTimeoutSeconds ("
              + visibilityTimeoutSeconds
              + "s)");
    }
    if (workerId == null || workerId.isBlank()) {
      workerId = "worker-" + UUID.randomUUID();
    }
  }

  public boolean isEnabled() {
    return enabled != null ? enabled : true;
  }

  public boolean isAutoStartup() {
    return autoStartup != null ? autoStartup : true;
  }
}
