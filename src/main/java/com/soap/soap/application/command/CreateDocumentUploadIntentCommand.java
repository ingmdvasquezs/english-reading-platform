package com.soap.soap.application.command;

import java.util.Objects;
import java.util.UUID;

public record CreateDocumentUploadIntentCommand(
    UUID userId, String fileName, String contentType, long sizeBytes, String checksumSha256) {

  public CreateDocumentUploadIntentCommand {
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(fileName, "fileName must not be null");
    Objects.requireNonNull(checksumSha256, "checksumSha256 must not be null");
  }
}
