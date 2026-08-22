package com.soap.soap.infrastructure.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

public class CorrelationIdFilter extends OncePerRequestFilter {
  public static final String MDC_KEY = "correlationId";
  private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]+");

  private final String headerName;
  private final int maximumLength;

  public CorrelationIdFilter(
      @Value("${app.correlation-id.header:X-Correlation-ID}") String headerName,
      @Value("${app.correlation-id.maximum-length:64}") int maximumLength) {
    this.headerName = headerName;
    this.maximumLength = maximumLength;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var correlationId = acceptedOrGenerated(request.getHeader(headerName));
    response.setHeader(headerName, correlationId);
    try (var ignored = MDC.putCloseable(MDC_KEY, correlationId)) {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  String acceptedOrGenerated(String candidate) {
    if (candidate != null
        && !candidate.isBlank()
        && candidate.length() <= maximumLength
        && SAFE_VALUE.matcher(candidate).matches()) {
      return candidate;
    }
    return UUID.randomUUID().toString();
  }
}
