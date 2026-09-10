package com.soap.soap.infrastructure.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authenticationException)
      throws IOException {
    if (!request.getRequestURI().startsWith("/api/v1/")) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
    var requestId = java.util.Objects.requireNonNullElse(MDC.get("correlationId"), "");
    response
        .getWriter()
        .write(
            "{\"code\":\"AUTHENTICATION_REQUIRED\","
                + "\"message\":\"Authentication is required.\","
                + "\"timestamp\":\""
                + Instant.now()
                + "\",\"requestId\":\""
                + requestId
                + "\"}");
  }
}
