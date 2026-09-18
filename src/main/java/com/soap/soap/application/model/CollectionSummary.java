package com.soap.soap.application.model;

import com.soap.soap.domain.model.ReadingCollection;
import java.util.UUID;

public record CollectionSummary(
    UUID id,
    String key,
    String displayName,
    String description,
    int displayOrder,
    boolean active,
    String coverKey,
    int readingCount) {

  public static CollectionSummary of(ReadingCollection collection, int readingCount) {
    return new CollectionSummary(
        collection.id(),
        collection.key(),
        collection.displayName(),
        collection.description(),
        collection.displayOrder(),
        collection.active(),
        collection.coverKey(),
        readingCount);
  }
}
