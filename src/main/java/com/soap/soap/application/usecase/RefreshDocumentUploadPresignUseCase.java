package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.UploadAbortedException;
import com.soap.soap.application.exception.UploadExpiredException;
import com.soap.soap.application.exception.UploadNotFoundException;
import com.soap.soap.application.model.DocumentUploadIntentResult;
import com.soap.soap.application.port.out.DocumentObjectStoragePort;
import com.soap.soap.application.port.out.DocumentUploadRepositoryPort;
import com.soap.soap.domain.model.DocumentUpload;
import com.soap.soap.domain.model.UploadAuthorization;
import com.soap.soap.infrastructure.storage.S3DocumentStorageProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RefreshDocumentUploadPresignUseCase {

  private final DocumentUploadRepositoryPort documentUploads;
  private final DocumentObjectStoragePort storagePort;
  private final S3DocumentStorageProperties properties;
  private final Clock clock;

  public RefreshDocumentUploadPresignUseCase(
      DocumentUploadRepositoryPort documentUploads,
      DocumentObjectStoragePort storagePort,
      S3DocumentStorageProperties properties,
      Clock clock) {
    this.documentUploads = documentUploads;
    this.storagePort = storagePort;
    this.properties = properties;
    this.clock = clock;
  }

  public DocumentUploadIntentResult refreshPresign(UUID uploadId, UUID userId) {
    Objects.requireNonNull(uploadId, "uploadId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");

    DocumentUpload upload =
        documentUploads
            .findById(uploadId)
            .filter(u -> u.userId().equals(userId))
            .orElseThrow(() -> new UploadNotFoundException(uploadId));

    if (upload.isAborted()) {
      throw new UploadAbortedException(uploadId);
    }

    if (upload.isConfirmed()) {
      throw new IllegalArgumentException("Upload is already confirmed");
    }

    LocalDateTime now = LocalDateTime.now(clock);
    if (upload.isExpired(now)) {
      throw new UploadExpiredException(uploadId);
    }

    UploadAuthorization auth =
        storagePort.createUploadAuthorization(
            upload.storageKey(),
            upload.contentType(),
            upload.expectedChecksumSha256(),
            properties.presignDuration());

    return new DocumentUploadIntentResult(
        upload.id(),
        upload.documentId(),
        auth.uploadUrl(),
        auth.httpMethod(),
        auth.expiresAt(),
        auth.requiredHeaders());
  }
}
