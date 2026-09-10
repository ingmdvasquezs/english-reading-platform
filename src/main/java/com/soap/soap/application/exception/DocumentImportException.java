package com.soap.soap.application.exception;

public class DocumentImportException extends RuntimeException {
  private final Reason reason;

  public DocumentImportException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public DocumentImportException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }

  public enum Reason {
    INVALID_EPUB,
    INVALID_PDF,
    PDF_PASSWORD_PROTECTED,
    PDF_SCANNED_NOT_SUPPORTED,
    UNSUPPORTED_DRM,
    LANGUAGE_REQUIRED,
    FILE_TOO_LARGE,
    SECURITY_LIMIT_EXCEEDED,
    STORAGE_FAILURE,
    IMPORT_FAILURE,
    UNSUPPORTED_LANGUAGE
  }
}
