package com.soap.soap.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.PropertyPlaceholderHelper;

class ConfigurationProfilesTest {
  private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

  @Test
  void commonConfigurationDefaultsToTheLocalProfile() throws Exception {
    assertThat(property("application.yaml", "spring.profiles.default")).isEqualTo("local");
    assertThat(property("application.yaml", "server.shutdown")).isEqualTo("graceful");
    assertThat(property("application.yaml", "spring.datasource.hikari.maximum-pool-size"))
        .isEqualTo("${DB_MAXIMUM_POOL_SIZE:10}");
    assertThat(property("application.yaml", "management.endpoint.health.group.readiness.include"))
        .isEqualTo("readinessState,db");
  }

  @Test
  void localProfileContainsOnlyDevelopmentEndpointsAndCredentials() throws Exception {
    assertThat(property("application-local.yaml", "spring.datasource.url"))
        .isEqualTo("${DB_URL:jdbc:postgresql://localhost:5432/english_reading}");
    assertThat(property("application-local.yaml", "translation.libre.base-url"))
        .isEqualTo("${LIBRETRANSLATE_BASE_URL:http://localhost:5000}");
    assertThat(property("application-local.yaml", "management.endpoints.web.exposure.include"))
        .isEqualTo("health,info,prometheus");
  }

  @Test
  void productionProfileRequiresEnvironmentValuesAndHasNoLocalPasswordFallback() throws Exception {
    var databasePassword = property("application-prod.yaml", "spring.datasource.password");
    var jwtSecret = property("application-prod.yaml", "security.jwt.secret");

    assertThat(databasePassword).isEqualTo("${DB_PASSWORD}").doesNotContain("english_password");
    assertThat(jwtSecret).isEqualTo("${JWT_SECRET}");
    assertThat(property("application-prod.yaml", "spring.datasource.url"))
        .doesNotContain("localhost");
    assertThat(property("application-prod.yaml", "management.endpoints.web.exposure.include"))
        .isEqualTo("${MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE:health,info}");
    assertThat(property("application-prod.yaml", "logging.structured.format.console"))
        .isEqualTo("logstash");
    assertThatThrownBy(
            () ->
                new PropertyPlaceholderHelper("${", "}", ":", null, false)
                    .replacePlaceholders(databasePassword, new Properties()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private String property(String resource, String name) throws Exception {
    return String.valueOf(
        loader.load(resource, new ClassPathResource(resource)).getFirst().getProperty(name));
  }
}
