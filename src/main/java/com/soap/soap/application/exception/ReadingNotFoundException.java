package com.soap.soap.application.exception;

import java.util.UUID;

public class ReadingNotFoundException extends RuntimeException {
  public ReadingNotFoundException(UUID readingId) {
    super("Reading not found");
  }
}
