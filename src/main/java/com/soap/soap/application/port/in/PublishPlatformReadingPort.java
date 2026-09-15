package com.soap.soap.application.port.in;

import com.soap.soap.domain.model.Reading;
import java.util.UUID;

public interface PublishPlatformReadingPort {
  Reading publish(UUID readingId);
}
