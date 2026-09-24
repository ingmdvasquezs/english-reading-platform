package com.soap.soap.application.exception;

import java.util.UUID;

public class UploadIntegrityMismatchException extends RuntimeException {
  private final UUID uploadId;
  private final String detail;

  public UploadIntegrityMismatchException(UUID uploadId, String detail) {
    super("Upload integrity mismatch for upload " + uploadId + ": " + detail);
    this.uploadId = uploadId;
    this.detail = detail;
  }

  public UUID getUploadId() {
    return uploadId;
  }

  public String getDetail() {
    return detail;
  }
}
