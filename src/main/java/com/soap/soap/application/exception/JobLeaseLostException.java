package com.soap.soap.application.exception;

public class JobLeaseLostException extends RuntimeException {
  public JobLeaseLostException(String message) {
    super(message);
  }
}
