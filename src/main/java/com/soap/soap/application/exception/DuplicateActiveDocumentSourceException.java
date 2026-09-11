package com.soap.soap.application.exception;

public class DuplicateActiveDocumentSourceException extends RuntimeException {
  public DuplicateActiveDocumentSourceException(String message) {
    super(message);
  }

  public DuplicateActiveDocumentSourceException(String message, Throwable cause) {
    super(message, cause);
  }
}
