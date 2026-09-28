package com.soap.soap.application.model;

import java.util.Objects;
import java.util.UUID;

public record DocumentImportQueueMessage(
    int schemaVersion, UUID eventId, String eventType, UUID importJobId) {

  public static final int CURRENT_SCHEMA_VERSION = 1;
  public static final String DOCUMENT_IMPORT_REQUESTED_TYPE = "DOCUMENT_IMPORT_REQUESTED";

  public DocumentImportQueueMessage {
    if (schemaVersion <= 0) {
      throw new IllegalArgumentException("schemaVersion must be positive");
    }
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(eventType, "eventType must not be null");
    if (eventType.isBlank()) {
      throw new IllegalArgumentException("eventType must not be blank");
    }
    Objects.requireNonNull(importJobId, "importJobId must not be null");
  }

  public static DocumentImportQueueMessage forImportJob(UUID eventId, UUID importJobId) {
    return new DocumentImportQueueMessage(
        CURRENT_SCHEMA_VERSION, eventId, DOCUMENT_IMPORT_REQUESTED_TYPE, importJobId);
  }
}
