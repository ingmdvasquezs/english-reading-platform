package com.soap.soap.application.port.in;

import com.soap.soap.application.model.ReadingReaderData;
import java.util.UUID;

public interface GetReadingReaderDataPort {
  ReadingReaderData getReadingReaderData(UUID readingId);
}
