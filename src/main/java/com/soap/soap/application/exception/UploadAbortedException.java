package com.soap.soap.application.exception;

import java.util.UUID;

public class UploadAbortedException extends RuntimeException {
  public UploadAbortedException(UUID id) {
    super("Document upload was aborted: " + id);
  }
}
