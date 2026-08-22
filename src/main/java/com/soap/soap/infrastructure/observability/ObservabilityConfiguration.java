package com.soap.soap.infrastructure.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class ObservabilityConfiguration {
  @Bean
  CorrelationIdFilter correlationIdFilter(
      @Value("${app.correlation-id.header:X-Correlation-ID}") String headerName,
      @Value("${app.correlation-id.maximum-length:64}") int maximumLength) {
    return new CorrelationIdFilter(headerName, maximumLength);
  }

  @Bean
  FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration(
      CorrelationIdFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setName("correlationIdFilter");
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
