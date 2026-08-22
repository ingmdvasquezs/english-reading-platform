package com.soap.soap.infrastructure.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtDecoder decoder;
  private final MeterRegistry meters;

  public JwtAuthenticationFilter(JwtDecoder decoder) {
    this(decoder, new SimpleMeterRegistry());
  }

  @Autowired
  public JwtAuthenticationFilter(JwtDecoder decoder, MeterRegistry meters) {
    this.decoder = decoder;
    this.meters = meters;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      chain.doFilter(request, response);
      return;
    }
    try {
      var jwt = decoder.decode(authorization.substring(7));
      var authentication = new UsernamePasswordAuthenticationToken(jwt, jwt, java.util.List.of());
      SecurityContextHolder.getContext().setAuthentication(authentication);
      chain.doFilter(request, response);
    } catch (JwtException exception) {
      meters.counter("security.authentication.failures", "reason", "invalid_token").increment();
      SecurityContextHolder.clearContext();
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token");
    }
  }
}
