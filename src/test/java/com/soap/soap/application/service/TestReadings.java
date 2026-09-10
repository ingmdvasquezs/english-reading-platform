package com.soap.soap.application.service;

import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import java.time.LocalDateTime;
import java.util.UUID;

final class TestReadings {
  private TestReadings() {}

  static Reading platform() {
    return new Reading(
        UUID.randomUUID(),
        null,
        "Test",
        "Test",
        "en",
        LocalDateTime.parse("2026-09-10T10:00:00"),
        ReadingOrigin.PLATFORM,
        EditorialLevel.A1,
        "Test");
  }
}
