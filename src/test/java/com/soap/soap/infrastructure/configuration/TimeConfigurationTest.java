package com.soap.soap.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TimeConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TimeConfiguration.class);

  @Test
  @DisplayName("Primary Clock bean is single and configured with ZoneOffset.UTC")
  void primaryClockBeanIsUtc() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(Clock.class);
          Clock clock = context.getBean(Clock.class);
          assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
          assertThat(clock.instant()).isNotNull();
        });
  }
}
