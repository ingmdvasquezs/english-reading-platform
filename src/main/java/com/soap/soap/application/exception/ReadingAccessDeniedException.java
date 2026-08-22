package com.soap.soap.application.exception;

import java.util.UUID;

public class ReadingAccessDeniedException extends RuntimeException {
  public ReadingAccessDeniedException(UUID readingId, UUID userId) {
    super("Reading access denied");
  }
}
