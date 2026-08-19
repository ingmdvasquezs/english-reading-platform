package com.soap.soap.application.model;

public record PageRequest(int page, int size) {

  public static final int MAX_SIZE = 100;

  public PageRequest {
    if (page < 0) {
      throw new IllegalArgumentException("Page number must not be negative");
    }
    if (size < 1 || size > MAX_SIZE) {
      throw new IllegalArgumentException("Page size must be between 1 and " + MAX_SIZE);
    }
  }
}
