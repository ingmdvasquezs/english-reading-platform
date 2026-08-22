package com.soap.soap.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.ResourceAccessException;

class ObservabilityTest {
  @Test
  void correlationIdIsGeneratedReturnedAvailableInMdcAndAlwaysCleared() throws Exception {
    var filter = new CorrelationIdFilter("X-Correlation-ID", 64);
    var request = new MockHttpServletRequest("POST", "/ws");
    var response = new MockHttpServletResponse();
    var valueInsideChain = new AtomicReference<String>();

    filter.doFilter(
        request,
        response,
        (ignoredRequest, ignoredResponse) ->
            valueInsideChain.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

    assertThat(valueInsideChain.get())
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    assertThat(response.getHeader("X-Correlation-ID")).isEqualTo(valueInsideChain.get());
    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void safeReceivedCorrelationIdIsReusedAndInvalidValueIsReplaced() throws Exception {
    var filter = new CorrelationIdFilter("X-Correlation-ID", 64);
    assertThat(filter.acceptedOrGenerated("client-request_42.test"))
        .isEqualTo("client-request_42.test");
    assertThat(filter.acceptedOrGenerated("unsafe value\r\nInjected: true"))
        .matches("[0-9a-f-]{36}");
    assertThat(filter.acceptedOrGenerated("a".repeat(65))).matches("[0-9a-f-]{36}");
  }

  @Test
  void providerMetricsUseOnlyBoundedTagsAndLogsDoNotExposeExceptionSecrets() {
    var registry = new SimpleMeterRegistry();
    var observation = new ExternalProviderObservation(registry);
    var logger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ExternalProviderObservation.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThatThrownBy(
              () ->
                  observation.observe(
                      "free_dictionary",
                      () -> {
                        throw new IllegalStateException("api-key=super-secret");
                      }))
          .isInstanceOf(IllegalStateException.class);

      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .allMatch(message -> !message.contains("super-secret") && !message.contains("api-key"));
      assertThat(registry.find("external.provider.requests").meters()).hasSize(1);
      assertThat(registry.find("external.provider.failures").meters()).hasSize(1);
      assertThat(
              registry.find("external.provider.requests").meters().stream()
                  .flatMap(meter -> meter.getId().getTags().stream())
                  .map(tag -> tag.getKey()))
          .doesNotContain("userId", "word", "email", "correlationId", "readingId");
    } finally {
      logger.detachAppender(appender);
    }
  }

  @Test
  void providerTimeoutHasItsOwnBoundedMetricCategory() {
    var registry = new SimpleMeterRegistry();
    var observation = new ExternalProviderObservation(registry);

    assertThatThrownBy(
            () ->
                observation.observe(
                    "libre_translate",
                    () -> {
                      throw new ResourceAccessException(
                          "request failed", new SocketTimeoutException("timed out"));
                    }))
        .isInstanceOf(ResourceAccessException.class);

    assertThat(
            registry
                .find("external.provider.timeouts")
                .tag("provider", "libre_translate")
                .counter())
        .isNotNull()
        .extracting(counter -> counter.count())
        .isEqualTo(1.0);
  }
}
