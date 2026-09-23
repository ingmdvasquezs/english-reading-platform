package com.soap.soap.application.exception;

import java.util.UUID;

public class UploadNotFoundException extends RuntimeException {
  public UploadNotFoundException(UUID id) {
    super("Document upload not found: " + id);
  }
}
