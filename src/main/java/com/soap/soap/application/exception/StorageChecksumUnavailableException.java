package com.soap.soap.application.exception;

import java.util.UUID;

public class StorageChecksumUnavailableException extends RuntimeException {
  private final UUID uploadId;

  public StorageChecksumUnavailableException(UUID uploadId) {
    super("Storage provider did not return a verified SHA-256 checksum for uploadId=" + uploadId);
    this.uploadId = uploadId;
  }

  public UUID getUploadId() {
    return uploadId;
  }
}
