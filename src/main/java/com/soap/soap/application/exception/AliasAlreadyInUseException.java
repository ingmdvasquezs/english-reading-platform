package com.soap.soap.application.exception;

public class AliasAlreadyInUseException extends RuntimeException {
  public AliasAlreadyInUseException() {
    super("Alias is already in use");
  }
}
