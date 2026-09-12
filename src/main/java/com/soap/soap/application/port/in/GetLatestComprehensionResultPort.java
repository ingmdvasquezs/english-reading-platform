package com.soap.soap.application.port.in;

import com.soap.soap.application.model.LatestComprehensionResult;
import java.util.UUID;

public interface GetLatestComprehensionResultPort {
  LatestComprehensionResult getLatestResult(UUID readingId);
}
