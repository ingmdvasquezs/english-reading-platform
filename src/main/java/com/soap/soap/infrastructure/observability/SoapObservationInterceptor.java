package com.soap.soap.infrastructure.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.MethodEndpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.interceptor.EndpointInterceptorAdapter;
import org.springframework.ws.soap.SoapMessage;

public class SoapObservationInterceptor extends EndpointInterceptorAdapter {
  private static final Logger LOGGER = LoggerFactory.getLogger(SoapObservationInterceptor.class);
  private static final String START_NANOS = SoapObservationInterceptor.class.getName() + ".start";
  private static final String OPERATION = SoapObservationInterceptor.class.getName() + ".operation";
  private static final String RECORDED = SoapObservationInterceptor.class.getName() + ".recorded";

  private final MeterRegistry meters;

  public SoapObservationInterceptor(MeterRegistry meters) {
    this.meters = meters;
  }

  @Override
  public boolean handleRequest(MessageContext context, Object endpoint) {
    var operation = operation(endpoint);
    context.setProperty(START_NANOS, System.nanoTime());
    context.setProperty(OPERATION, operation);
    LOGGER.info("soap.request.started operation={} authenticated={}", operation, isAuthenticated());
    return true;
  }

  @Override
  public boolean handleResponse(MessageContext context, Object endpoint) {
    record(context, "success", "none");
    return true;
  }

  @Override
  public boolean handleFault(MessageContext context, Object endpoint) {
    record(context, "fault", faultCategory(context));
    return true;
  }

  @Override
  public void afterCompletion(MessageContext context, Object endpoint, Exception exception) {
    if (exception != null) {
      record(context, "error", "internal");
    }
  }

  private void record(MessageContext context, String outcome, String category) {
    if (Boolean.TRUE.equals(context.getProperty(RECORDED))) {
      return;
    }
    context.setProperty(RECORDED, true);
    var operation = String.valueOf(context.getProperty(OPERATION));
    var start = (Long) context.getProperty(START_NANOS);
    var durationNanos = start == null ? 0 : System.nanoTime() - start;
    Timer.builder("soap.request.duration")
        .description("SOAP request duration")
        .tags("operation", operation, "outcome", outcome)
        .register(meters)
        .record(durationNanos, TimeUnit.NANOSECONDS);
    meters
        .counter(
            "soap.requests", "operation", operation, "outcome", outcome, "error.category", category)
        .increment();
    recordOperationMetric(operation, outcome, durationNanos);
    LOGGER.info(
        "soap.request.completed operation={} outcome={} errorCategory={} durationMs={}",
        operation,
        outcome,
        category,
        TimeUnit.NANOSECONDS.toMillis(durationNanos));
  }

  private void recordOperationMetric(String operation, String outcome, long durationNanos) {
    if ("login".equals(operation)) {
      meters.counter("security.login", "outcome", outcome).increment();
    } else if ("analyzeReading".equals(operation)) {
      meters
          .timer("reading.analysis.duration", "outcome", outcome)
          .record(durationNanos, TimeUnit.NANOSECONDS);
    } else if ("getReadingReaderData".equals(operation)) {
      meters
          .timer("reading.reader_data.duration", "outcome", outcome)
          .record(durationNanos, TimeUnit.NANOSECONDS);
    }
  }

  private String operation(Object endpoint) {
    if (!(endpoint instanceof MethodEndpoint methodEndpoint)) {
      return "unknown";
    }
    var annotation = methodEndpoint.getMethod().getAnnotation(PayloadRoot.class);
    var name = annotation == null ? methodEndpoint.getMethod().getName() : annotation.localPart();
    return name.endsWith("Request") ? name.substring(0, name.length() - 7) : name;
  }

  private String faultCategory(MessageContext context) {
    if (context.getResponse() instanceof SoapMessage soapMessage
        && soapMessage.getSoapBody().hasFault()) {
      var faultCode = soapMessage.getSoapBody().getFault().getFaultCode();
      return faultCode != null && "Client".equalsIgnoreCase(faultCode.getLocalPart())
          ? "client"
          : "internal";
    }
    return "internal";
  }

  private boolean isAuthenticated() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken);
  }
}
