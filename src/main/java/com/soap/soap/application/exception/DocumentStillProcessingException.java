package com.soap.soap.application.exception;

public class DocumentStillProcessingException extends RuntimeException {
  public DocumentStillProcessingException() {
    super("Document is still processing");
  }
}
