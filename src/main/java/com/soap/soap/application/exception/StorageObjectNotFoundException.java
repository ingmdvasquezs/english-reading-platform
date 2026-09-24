package com.soap.soap.application.exception;

public class StorageObjectNotFoundException extends RuntimeException {
  private final String storageKey;

  public StorageObjectNotFoundException(String storageKey) {
    super("Object not found in storage: " + storageKey);
    this.storageKey = storageKey;
  }

  public String getStorageKey() {
    return storageKey;
  }
}
