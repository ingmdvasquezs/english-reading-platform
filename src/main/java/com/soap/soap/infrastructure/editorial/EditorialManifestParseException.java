package com.soap.soap.infrastructure.editorial;

public class EditorialManifestParseException extends RuntimeException {
  public EditorialManifestParseException(String message) {
    super(message);
  }

  public EditorialManifestParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
