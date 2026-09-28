package com.soap.soap.infrastructure.queue;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.service.OutboxDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScheduledOutboxDispatcherTest {

  private final OutboxDispatcher dispatcher = mock(OutboxDispatcher.class);

  @Test
  @DisplayName("Dispatches outbox events when queue URL is configured")
  void dispatchesWhenConfigured() {
    DocumentImportQueueProperties properties =
        new DocumentImportQueueProperties("https://sqs.url", "us-east-1", null);
    ScheduledOutboxDispatcher scheduler = new ScheduledOutboxDispatcher(dispatcher, properties);

    when(dispatcher.dispatchOnce()).thenReturn(3);

    scheduler.dispatch();

    verify(dispatcher).dispatchOnce();
  }

  @Test
  @DisplayName("Fails fast on construction when queue URL is missing or blank")
  void failsFastWhenMissingQueueUrl() {
    DocumentImportQueueProperties blankProperties =
        new DocumentImportQueueProperties("   ", "us-east-1", null);
    assertThatThrownBy(() -> new ScheduledOutboxDispatcher(dispatcher, blankProperties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SQS queue URL");

    DocumentImportQueueProperties nullProperties =
        new DocumentImportQueueProperties(null, "us-east-1", null);
    assertThatThrownBy(() -> new ScheduledOutboxDispatcher(dispatcher, nullProperties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SQS queue URL");
  }

  @Test
  @DisplayName("Catches and logs dispatcher exceptions without propagating to scheduled executor")
  void handlesDispatcherExceptionsCleanly() {
    DocumentImportQueueProperties properties =
        new DocumentImportQueueProperties("https://sqs.url", "us-east-1", null);
    ScheduledOutboxDispatcher scheduler = new ScheduledOutboxDispatcher(dispatcher, properties);

    doThrow(new RuntimeException("Database error during dispatch")).when(dispatcher).dispatchOnce();

    // Does not throw
    scheduler.dispatch();

    verify(dispatcher).dispatchOnce();
  }
}
