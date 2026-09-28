package com.soap.soap.infrastructure.queue;

import com.soap.soap.application.service.OutboxDispatcher;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.document-import.outbox.enabled", havingValue = "true")
@ConditionalOnExpression(
    "'${APP_ROLE:${app.role:api}}'.equalsIgnoreCase('api') or '${APP_ROLE:${app.role:api}}'.equalsIgnoreCase('all')")
public class ScheduledOutboxDispatcher {

  private static final Logger log = LoggerFactory.getLogger(ScheduledOutboxDispatcher.class);

  private final OutboxDispatcher dispatcher;
  private final DocumentImportQueueProperties queueProperties;

  public ScheduledOutboxDispatcher(
      OutboxDispatcher dispatcher, DocumentImportQueueProperties queueProperties) {
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
    this.queueProperties =
        Objects.requireNonNull(queueProperties, "queueProperties must not be null");
    if (queueProperties.url() == null || queueProperties.url().isBlank()) {
      throw new IllegalStateException(
          "SQS queue URL (app.document-import.queue.url) must be configured when document import outbox is enabled");
    }
  }

  @Scheduled(
      fixedDelayString = "${app.document-import.outbox.schedule.fixed-delay:2000}",
      initialDelayString = "${app.document-import.outbox.schedule.initial-delay:5000}")
  public void dispatch() {
    if (queueProperties.url() == null || queueProperties.url().isBlank()) {
      log.trace("Scheduled outbox dispatcher skipped: SQS queue URL is not configured");
      return;
    }
    try {
      int count = dispatcher.dispatchOnce();
      if (count > 0) {
        log.debug("Scheduled outbox dispatcher dispatched {} events", count);
      }
    } catch (Exception ex) {
      log.error("Unexpected error during scheduled outbox dispatch: {}", ex.getMessage(), ex);
    }
  }
}
