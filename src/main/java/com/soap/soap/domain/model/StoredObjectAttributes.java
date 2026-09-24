package com.soap.soap.domain.model;

import java.util.Objects;

public record StoredObjectAttributes(
    String storageKey, long sizeBytes, String checksumSha256, String etag) {

  public StoredObjectAttributes {
    Objects.requireNonNull(storageKey, "storageKey must not be null");
    if (sizeBytes < 0) {
      throw new IllegalArgumentException("sizeBytes must not be negative");
    }
  }
}
