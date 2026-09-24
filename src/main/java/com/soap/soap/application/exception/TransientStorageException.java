package com.soap.soap.application.exception;

public class TransientStorageException extends RuntimeException {
  public TransientStorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
