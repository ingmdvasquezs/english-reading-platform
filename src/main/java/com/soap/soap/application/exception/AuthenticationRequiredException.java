package com.soap.soap.application.exception;

public class AuthenticationRequiredException extends RuntimeException {
  public AuthenticationRequiredException() {
    super("Authentication is required");
  }
}
