package com.soap.soap.application.exception;

import java.util.UUID;

public class UploadNotCompletedException extends RuntimeException {
  private final UUID uploadId;

  public UploadNotCompletedException(UUID uploadId) {
    super("Upload object has not been uploaded to storage yet for upload: " + uploadId);
    this.uploadId = uploadId;
  }

  public UUID getUploadId() {
    return uploadId;
  }
}
