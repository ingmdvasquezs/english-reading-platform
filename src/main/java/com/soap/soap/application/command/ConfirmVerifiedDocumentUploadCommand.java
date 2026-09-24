package com.soap.soap.application.command;

import com.soap.soap.domain.model.StorageProvider;
import java.util.Objects;
import java.util.UUID;

public record ConfirmVerifiedDocumentUploadCommand(
    UUID uploadId,
    UUID userId,
    String languageOverride,
    String verifiedChecksumSha256,
    Long verifiedSizeBytes,
    StorageProvider storageProvider) {

  public ConfirmVerifiedDocumentUploadCommand {
    Objects.requireNonNull(uploadId, "uploadId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    if (storageProvider == null) {
      storageProvider = StorageProvider.S3;
    }
  }

  public ConfirmVerifiedDocumentUploadCommand(UUID uploadId, UUID userId, String languageOverride) {
    this(uploadId, userId, languageOverride, null, null, StorageProvider.S3);
  }
}
