package com.soap.soap.application.exception;

import java.util.UUID;

public class UploadExpiredException extends RuntimeException {
  public UploadExpiredException(UUID id) {
    super("Document upload has expired: " + id);
  }
}
