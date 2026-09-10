package com.soap.soap.application.exception;

public class DocumentProgressConflictException extends RuntimeException {
  public DocumentProgressConflictException() {
    super("Document progress version is stale");
  }
}
