package com.soap.soap.application.port.in;

import com.soap.soap.domain.model.Reading;
import java.util.UUID;

public interface GetReadingPort {
  Reading getReading(UUID readingId);
}
