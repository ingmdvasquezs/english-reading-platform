package com.soap.soap.application.port.in;

import java.util.UUID;

public interface DeleteReadingPort {
  void deleteReading(UUID readingId);
}
