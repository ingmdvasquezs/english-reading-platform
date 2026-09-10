package com.soap.soap.application.exception;

import java.util.UUID;

public class DocumentUnitNotFoundException extends RuntimeException {
  public DocumentUnitNotFoundException(UUID id) {
    super("Document unit not found: " + id);
  }
}
