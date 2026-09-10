package com.soap.soap.infrastructure.observability;

import com.soap.soap.application.exception.DictionaryInvalidResponseException;
import com.soap.soap.application.exception.DictionaryTimeoutException;
import com.soap.soap.application.exception.DictionaryUnavailableException;
import com.soap.soap.application.exception.WordNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientResponseException;

public class ExternalProviderObservation {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExternalProviderObservation.class);

  private final MeterRegistry meters;

  public ExternalProviderObservation(MeterRegistry meters) {
    this.meters = meters;
  }

  public <T> T observe(String provider, Supplier<T> request) {
    var startedAt = System.nanoTime();
    try {
      var result = request.get();
      record(provider, "success", "none", startedAt);
      return result;
    } catch (RuntimeException exception) {
      var category = category(exception);
      record(provider, "failure", category, startedAt);
      meters
          .counter("external.provider.failures", "provider", provider, "error.category", category)
          .increment();
      if ("timeout".equals(category)) {
        meters.counter("external.provider.timeouts", "provider", provider).increment();
      }
      if ("not_found".equals(category)) {
        LOGGER.info(
            "external.provider.completed provider={} outcome=not_found status={} durationMs={}",
            provider,
            status(exception),
            elapsedMillis(startedAt));
      } else {
        LOGGER.warn(
            "external.provider.completed provider={} outcome=failure errorCategory={} status={} durationMs={}",
            provider,
            category,
            status(exception),
            elapsedMillis(startedAt));
      }
      throw exception;
    }
  }

  private void record(String provider, String outcome, String category, long startedAt) {
    var durationNanos = System.nanoTime() - startedAt;
    meters
        .counter(
            "external.provider.requests",
            "provider",
            provider,
            "outcome",
            outcome,
            "error.category",
            category)
        .increment();
    Timer.builder("external.provider.duration")
        .tags("provider", provider, "outcome", outcome)
        .register(meters)
        .record(durationNanos, TimeUnit.NANOSECONDS);
    if ("success".equals(outcome)) {
      LOGGER.info(
          "external.provider.completed provider={} outcome=success status={} durationMs={}",
          provider,
          "2xx",
          TimeUnit.NANOSECONDS.toMillis(durationNanos));
    }
  }

  private String category(Throwable throwable) {
    if (throwable instanceof WordNotFoundException) {
      return "not_found";
    }
    if (throwable instanceof DictionaryTimeoutException) {
      return "timeout";
    }
    if (throwable instanceof DictionaryInvalidResponseException) {
      return "invalid_response";
    }
    if (throwable instanceof DictionaryUnavailableException) {
      var responseException = findCause(throwable, RestClientResponseException.class);
      return responseException != null
              && isTransientStatus(responseException.getStatusCode().value())
          ? "transient_provider_error"
          : "provider_error";
    }
    if (hasCause(throwable, SocketTimeoutException.class)) {
      return "timeout";
    }
    var responseException = findCause(throwable, RestClientResponseException.class);
    if (responseException != null) {
      return responseException.getStatusCode().is4xxClientError() ? "http_4xx" : "http_5xx";
    }
    return "provider_error";
  }

  private boolean isTransientStatus(int status) {
    return status == 500 || status == 502 || status == 503 || status == 504 || status == 522;
  }

  private String status(Throwable throwable) {
    var responseException = findCause(throwable, RestClientResponseException.class);
    return responseException == null
        ? "none"
        : Integer.toString(responseException.getStatusCode().value());
  }

  private long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }

  private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
    return findCause(throwable, type) != null;
  }

  private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
    var current = throwable;
    while (current != null) {
      if (type.isInstance(current)) {
        return type.cast(current);
      }
      current = current.getCause();
    }
    return null;
  }
}
