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
    String workerId) {

  public DocumentImportWorkerProperties {
    if (enabled == null) {
      enabled = Boolean.TRUE;
    }
    if (autoStartup == null) {
      autoStartup = Boolean.TRUE;
    }
    if (waitTimeSeconds <= 0) {
      waitTimeSeconds = 20;
    }
    if (visibilityTimeoutSeconds <= 0) {
      visibilityTimeoutSeconds = 60;
    }
    if (maxMessages <= 0) {
      maxMessages = 1;
    }
    if (pollDelay == null) {
      pollDelay = Duration.ofSeconds(1);
    }
    if (errorBackoff == null) {
      errorBackoff = Duration.ofSeconds(5);
    }
    if (leaseDuration == null) {
      leaseDuration = Duration.ofSeconds(60);
    }
    if (heartbeatInterval == null) {
      heartbeatInterval = Duration.ofSeconds(20);
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
