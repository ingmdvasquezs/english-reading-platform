package com.soap.soap.application.exception;

public class CollectionNotFoundException extends RuntimeException {
  public CollectionNotFoundException(String collectionKey) {
    super("Collection not found");
  }
}
