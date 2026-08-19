package com.soap.soap.application.port.in;

import com.soap.soap.domain.model.ReadingAnalysis;
import java.util.UUID;

public interface AnalyzeReadingPort {
  ReadingAnalysis analyzeReading(UUID userId, UUID readingId);
}
