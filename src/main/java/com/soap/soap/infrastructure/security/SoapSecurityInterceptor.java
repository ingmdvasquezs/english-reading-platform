package com.soap.soap.infrastructure.security;

import com.soap.soap.application.port.out.CurrentUserPort;
import jakarta.servlet.http.HttpServletRequest;
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

  public SoapSecurityInterceptor(
      CurrentUserPort currentUser,
      InMemoryRateLimiter limiter,
      Map<RateLimitPolicy, Limit> limits) {
    this.currentUser = currentUser;
    this.limiter = limiter;
    this.limits = Map.copyOf(limits);
  }

  @Override
  public boolean handleRequest(MessageContext messageContext, Object endpoint) throws Exception {
    if (!(endpoint instanceof MethodEndpoint methodEndpoint)) {
      return true;
    }
    var method = methodEndpoint.getMethod();
    var publicOperation = method.isAnnotationPresent(PublicSoapOperation.class);
    var userId = publicOperation ? null : currentUser.requireUserId();
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
    connection.getHttpServletResponse().sendError(429, "Too many requests");
    return false;
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
