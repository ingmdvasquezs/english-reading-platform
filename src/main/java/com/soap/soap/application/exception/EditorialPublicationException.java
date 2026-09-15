package com.soap.soap.application.exception;

public class EditorialPublicationException extends RuntimeException {
  public EditorialPublicationException(String message) {
    super(message);
  }

  public EditorialPublicationException(String message, Throwable cause) {
    super(message, cause);
  }
}
