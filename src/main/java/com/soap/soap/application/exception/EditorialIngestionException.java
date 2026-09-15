package com.soap.soap.application.exception;

public class EditorialIngestionException extends RuntimeException {
  public EditorialIngestionException(String message) {
    super(message);
  }

  public EditorialIngestionException(String message, Throwable cause) {
    super(message, cause);
  }
}
