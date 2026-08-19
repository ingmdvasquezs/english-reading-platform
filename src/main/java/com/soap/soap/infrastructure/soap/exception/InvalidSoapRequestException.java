package com.soap.soap.infrastructure.soap.exception;

public class InvalidSoapRequestException extends RuntimeException {

  public InvalidSoapRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
