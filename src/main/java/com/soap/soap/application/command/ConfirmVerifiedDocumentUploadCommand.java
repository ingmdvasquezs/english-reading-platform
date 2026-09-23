package com.soap.soap.application.command;

import java.util.Objects;
import java.util.UUID;

public record ConfirmVerifiedDocumentUploadCommand(
    UUID uploadId, UUID userId, String languageOverride) {

  public ConfirmVerifiedDocumentUploadCommand {
    Objects.requireNonNull(uploadId, "uploadId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
  }
}
