package com.soap.soap.application.exception;

public class DocumentNotReadyException extends RuntimeException {
  public DocumentNotReadyException() {
    super("Document is not ready");
  }
}
