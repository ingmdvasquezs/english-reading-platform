package com.soap.soap.application.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {
  public EmailAlreadyRegisteredException() {
    super("Email is already registered");
  }
}
