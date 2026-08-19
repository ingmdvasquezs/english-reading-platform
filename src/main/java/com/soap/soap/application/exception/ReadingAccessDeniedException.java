package com.soap.soap.application.exception;

import java.util.UUID;

public class ReadingAccessDeniedException extends RuntimeException {
  public ReadingAccessDeniedException(UUID readingId, UUID userId) {
    super("Reading %s does not belong to user %s".formatted(readingId, userId));
  }
}
