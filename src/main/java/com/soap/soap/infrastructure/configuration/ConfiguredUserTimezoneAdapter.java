package com.soap.soap.infrastructure.configuration;

import com.soap.soap.application.port.out.UserTimezonePort;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves user timezone for SRS daily scheduling and day boundary determination. Currently
 * defaults to the configured product timezone (app.srs.timezone). Designed for future per-user
 * timezone preference resolution without breaking the port contract.
 */
@Component
public class ConfiguredUserTimezoneAdapter implements UserTimezonePort {

  private final ZoneId defaultZoneId;

  public ConfiguredUserTimezoneAdapter(
      @Value("${app.srs.timezone:America/Bogota}") String timezone) {
    this.defaultZoneId = ZoneId.of(timezone);
  }

  @Override
  public ZoneId resolveUserZoneId(UUID userId) {
    return defaultZoneId;
  }
}
