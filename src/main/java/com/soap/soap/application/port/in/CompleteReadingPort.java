package com.soap.soap.application.port.in;

import com.soap.soap.domain.model.ReadingProgress;
import java.util.UUID;

public interface CompleteReadingPort {
  ReadingProgress completeReading(UUID readingId);
}
