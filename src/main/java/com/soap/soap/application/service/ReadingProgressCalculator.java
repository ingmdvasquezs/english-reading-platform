package com.soap.soap.application.service;

import com.soap.soap.domain.model.ReadingProgressStatus;
import org.springframework.stereotype.Component;

@Component
public class ReadingProgressCalculator {

  public Integer calculate(
      ReadingProgressStatus status,
      Integer currentPartOrdinal,
      Integer paginationVersion,
      Integer totalParts) {
    if (status == null
        || currentPartOrdinal == null
        || paginationVersion == null
        || paginationVersion != TextReaderPaginationService.TEXT_PAGINATION_VERSION
        || totalParts == null
        || totalParts <= 0
        || currentPartOrdinal <= 0
        || currentPartOrdinal > totalParts) {
      return null;
    }

    if (status == ReadingProgressStatus.COMPLETED) {
      return 100;
    }

    int percentage = (int) Math.round((double) currentPartOrdinal / (double) totalParts * 100.0);
    if (percentage >= 100) {
      return 99;
    }
    if (percentage < 0) {
      return 0;
    }
    return percentage;
  }
}
