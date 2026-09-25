package com.soap.soap.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.infrastructure.soap.configuration.SoapRateLimitConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class RateLimitPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              SoapRateLimitConfiguration.class, TestDependenciesConfiguration.class);

  @Test
  void loadsExactDefaultValuesWhenNoPropertiesAreConfigured() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(RateLimitProperties.class);
          var properties = context.getBean(RateLimitProperties.class);

          assertThat(properties.maximumBuckets())
              .isEqualTo(RateLimitProperties.DEFAULT_MAXIMUM_BUCKETS);
          assertThat(properties.login().requests()).isEqualTo(10);
          assertThat(properties.login().window()).isEqualTo(Duration.ofMinutes(1));
          assertThat(properties.register().requests()).isEqualTo(5);
          assertThat(properties.register().window()).isEqualTo(Duration.ofHours(1));
          assertThat(properties.lookup().requests()).isEqualTo(30);
          assertThat(properties.lookup().window()).isEqualTo(Duration.ofMinutes(1));
          assertThat(properties.analyze().requests()).isEqualTo(10);
          assertThat(properties.analyze().window()).isEqualTo(Duration.ofMinutes(1));
          assertThat(properties.reader().requests()).isEqualTo(20);
          assertThat(properties.reader().window()).isEqualTo(Duration.ofMinutes(1));
        });
  }

  @Test
  void bindsCustomPropertiesAndPreservesUnsetPolicyFallbacks() {
    contextRunner
        .withPropertyValues(
            "app.rate-limit.maximum-buckets=5000",
            "app.rate-limit.login.requests=2",
            "app.rate-limit.login.window=2m",
            "app.rate-limit.register.requests=8")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var properties = context.getBean(RateLimitProperties.class);

              assertThat(properties.maximumBuckets()).isEqualTo(5000);
              assertThat(properties.login().requests()).isEqualTo(2);
              assertThat(properties.login().window()).isEqualTo(Duration.ofMinutes(2));
              // register requests overridden to 8, window preserved to 1h
              assertThat(properties.register().requests()).isEqualTo(8);
              assertThat(properties.register().window()).isEqualTo(Duration.ofHours(1));
              // other policies kept defaults
              assertThat(properties.lookup().requests()).isEqualTo(30);
              assertThat(properties.lookup().window()).isEqualTo(Duration.ofMinutes(1));
              assertThat(properties.analyze().requests()).isEqualTo(10);
              assertThat(properties.reader().requests()).isEqualTo(20);
            });
  }

  @Test
  void createsRequiredRateLimitingBeansSuccessfully() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(InMemoryRateLimiter.class);
          assertThat(context).hasSingleBean(SoapSecurityInterceptor.class);
        });
  }

  @Configuration(proxyBeanMethods = false)
  static class TestDependenciesConfiguration {
    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    CurrentUserPort currentUserPort() {
      return mock(CurrentUserPort.class);
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
