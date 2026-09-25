package com.soap.soap.infrastructure.soap.configuration;

import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.infrastructure.security.InMemoryRateLimiter;
import com.soap.soap.infrastructure.security.RateLimitPolicy;
import com.soap.soap.infrastructure.security.RateLimitProperties;
import com.soap.soap.infrastructure.security.SoapSecurityInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.EnumMap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class SoapRateLimitConfiguration {

  @Bean
  public InMemoryRateLimiter inMemoryRateLimiter(Clock clock, RateLimitProperties properties) {
    return new InMemoryRateLimiter(clock, properties.maximumBuckets());
  }

  @Bean
  public SoapSecurityInterceptor soapSecurityInterceptor(
      CurrentUserPort currentUser,
      InMemoryRateLimiter limiter,
      RateLimitProperties properties,
      MeterRegistry meters) {
    var limits = new EnumMap<RateLimitPolicy, SoapSecurityInterceptor.Limit>(RateLimitPolicy.class);
    limits.put(
        RateLimitPolicy.LOGIN,
        new SoapSecurityInterceptor.Limit(
            properties.login().requests(), properties.login().window()));
    limits.put(
        RateLimitPolicy.REGISTER,
        new SoapSecurityInterceptor.Limit(
            properties.register().requests(), properties.register().window()));
    limits.put(
        RateLimitPolicy.LOOKUP,
        new SoapSecurityInterceptor.Limit(
            properties.lookup().requests(), properties.lookup().window()));
    limits.put(
        RateLimitPolicy.ANALYZE,
        new SoapSecurityInterceptor.Limit(
            properties.analyze().requests(), properties.analyze().window()));
    limits.put(
        RateLimitPolicy.READER,
        new SoapSecurityInterceptor.Limit(
            properties.reader().requests(), properties.reader().window()));
    return new SoapSecurityInterceptor(currentUser, limiter, limits, meters);
  }
}
