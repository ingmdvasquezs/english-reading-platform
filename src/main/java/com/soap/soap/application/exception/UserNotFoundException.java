package com.soap.soap.application.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
  public UserNotFoundException(UUID userId) {
    super("User not found");
  }
}
