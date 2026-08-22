package com.soap.soap.infrastructure.security;

import com.soap.soap.application.exception.AuthenticationRequiredException;
import com.soap.soap.application.port.out.CurrentUserPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.MethodEndpoint;
import org.springframework.ws.server.endpoint.interceptor.EndpointInterceptorAdapter;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;

public class SoapSecurityInterceptor extends EndpointInterceptorAdapter {
  private final CurrentUserPort currentUser;
  private final InMemoryRateLimiter limiter;
  private final Map<RateLimitPolicy, Limit> limits;
  private final MeterRegistry meters;

  public SoapSecurityInterceptor(
      CurrentUserPort currentUser,
      InMemoryRateLimiter limiter,
      Map<RateLimitPolicy, Limit> limits) {
    this(currentUser, limiter, limits, new SimpleMeterRegistry());
  }

  public SoapSecurityInterceptor(
      CurrentUserPort currentUser,
      InMemoryRateLimiter limiter,
      Map<RateLimitPolicy, Limit> limits,
      MeterRegistry meters) {
    this.currentUser = currentUser;
    this.limiter = limiter;
    this.limits = Map.copyOf(limits);
    this.meters = meters;
  }

  @Override
  public boolean handleRequest(MessageContext messageContext, Object endpoint) throws Exception {
    if (!(endpoint instanceof MethodEndpoint methodEndpoint)) {
      return true;
    }
    var method = methodEndpoint.getMethod();
    var publicOperation = method.isAnnotationPresent(PublicSoapOperation.class);
    java.util.UUID userId;
    try {
      userId = publicOperation ? null : currentUser.requireUserId();
    } catch (AuthenticationRequiredException exception) {
      meters
          .counter("security.authentication.failures", "reason", "missing_authentication")
          .increment();
      throw exception;
    }
    var annotation = method.getAnnotation(RateLimited.class);
    if (annotation == null) {
      return true;
    }
    var limit = limits.get(annotation.value());
    var subject = publicOperation ? clientIp() : userId.toString();
    if (limiter.tryAcquire(annotation.value(), subject, limit.requests(), limit.window())) {
      return true;
    }
    var connection = requireHttpConnection();
    meters
        .counter("security.rate_limited", "policy", annotation.value().name().toLowerCase())
        .increment();
    rejectRateLimited(connection.getHttpServletResponse());
    return false;
  }

  private void rejectRateLimited(HttpServletResponse response) throws IOException {
    // Commit the transport-level response. sendError would dispatch to /error, where Spring
    // Security can replace 429 with 403 and discard the correlation header.
    response.setStatus(429);
    response.setContentType("text/plain");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write("Too many requests");
    response.flushBuffer();
  }

  private String clientIp() {
    var connection = currentHttpConnection();
    if (connection == null) {
      return "non-http-test-transport";
    }
    HttpServletRequest request = connection.getHttpServletRequest();
    return request.getRemoteAddr();
  }

  private HttpServletConnection currentHttpConnection() {
    var context = TransportContextHolder.getTransportContext();
    if (context == null) {
      return null;
    }
    var connection = context.getConnection();
    return connection instanceof HttpServletConnection httpConnection ? httpConnection : null;
  }

  private HttpServletConnection requireHttpConnection() {
    var connection = currentHttpConnection();
    if (connection == null) {
      throw new IllegalStateException("Rate limit rejection requires an HTTP transport");
    }
    return connection;
  }

  public record Limit(int requests, Duration window) {}
}
