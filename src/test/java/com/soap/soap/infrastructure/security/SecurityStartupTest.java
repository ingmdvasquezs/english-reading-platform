package com.soap.soap.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class SecurityStartupTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(JwtSecretConfiguration.class);

  @Test
  void startupFailsSafelyWhenJwtSecretIsMissingOrBlank() {
    contextRunner
        .withPropertyValues("security.jwt.secret=")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage("JWT_SECRET must be configured and must not be blank");
            });
  }

  @Test
  void startupAcceptsAnExplicitStrongJwtSecret() {
    contextRunner
        .withPropertyValues("security.jwt.secret=test-only-secret-with-at-least-32-bytes")
        .run(context -> assertThat(context).hasNotFailed().hasSingleBean(SecretKey.class));
  }

  @Configuration(proxyBeanMethods = false)
  static class JwtSecretConfiguration {
    @Bean
    SecretKey jwtSecretKey(@Value("${security.jwt.secret}") String secret) {
      return new SecurityConfiguration().jwtSecretKey(secret);
    }
  }
}
