package com.soap.soap.application.exception;

public class ImportCapacityExceededException extends RuntimeException {
  public ImportCapacityExceededException() {
    super("Document import capacity is temporarily exhausted");
  }
}
