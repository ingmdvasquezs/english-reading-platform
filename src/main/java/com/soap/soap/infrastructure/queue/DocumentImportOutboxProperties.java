package com.soap.soap.infrastructure.queue;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.document-import.outbox")
public record DocumentImportOutboxProperties(
    boolean enabled,
    int batchSize,
    Duration lockDuration,
    Duration retryBackoff,
    String dispatcherId,
    ScheduleProperties schedule) {

  public DocumentImportOutboxProperties {
    if (batchSize <= 0) {
      batchSize = 10;
    }
    if (lockDuration == null) {
      lockDuration = Duration.ofMinutes(5);
    }
    if (retryBackoff == null) {
      retryBackoff = Duration.ofSeconds(30);
    }
    if (dispatcherId == null || dispatcherId.isBlank()) {
      dispatcherId = "dispatcher-" + UUID.randomUUID();
    }
    if (schedule == null) {
      schedule = new ScheduleProperties(Duration.ofSeconds(2), Duration.ofSeconds(5));
    }
  }

  public record ScheduleProperties(Duration fixedDelay, Duration initialDelay) {
    public ScheduleProperties {
      if (fixedDelay == null) {
        fixedDelay = Duration.ofSeconds(2);
      }
      if (initialDelay == null) {
        initialDelay = Duration.ofSeconds(5);
      }
    }
  }
}
