package com.soap.soap.application.port.out;

import java.time.ZoneId;
import java.util.UUID;

/** Output port for resolving the effective time zone of a user for SRS daily scheduling. */
public interface UserTimezonePort {

  /**
   * Resolves the explicit ZoneId for a user.
   *
   * @param userId the user id
   * @return the resolved ZoneId
   */
  ZoneId resolveUserZoneId(UUID userId);
}
