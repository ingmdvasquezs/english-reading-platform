package com.soap.soap.application.usecase;

import com.soap.soap.application.command.CreateDocumentUploadIntentCommand;
import com.soap.soap.application.model.DocumentImportLimits;
import com.soap.soap.application.model.DocumentUploadIntentResult;
import com.soap.soap.application.port.out.DocumentObjectStoragePort;
import com.soap.soap.application.port.out.DocumentUploadRepositoryPort;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentUpload;
import com.soap.soap.domain.model.DocumentUploadStatus;
import com.soap.soap.domain.model.StorageProvider;
import com.soap.soap.domain.model.UploadAuthorization;
import com.soap.soap.infrastructure.storage.S3DocumentStorageProperties;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CreateDocumentUploadIntentUseCase {

  private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

  private final DocumentUploadRepositoryPort documentUploads;
  private final DocumentObjectStoragePort storagePort;
  private final DocumentImportLimits limits;
  private final S3DocumentStorageProperties properties;
  private final Clock clock;

  public CreateDocumentUploadIntentUseCase(
      DocumentUploadRepositoryPort documentUploads,
      DocumentObjectStoragePort storagePort,
      DocumentImportLimits limits,
      S3DocumentStorageProperties properties,
      Clock clock) {
    this.documentUploads = documentUploads;
    this.storagePort = storagePort;
    this.limits = limits;
    this.properties = properties;
    this.clock = clock;
  }

  public DocumentUploadIntentResult createIntent(CreateDocumentUploadIntentCommand command) {
    Objects.requireNonNull(command, "command must not be null");

    String rawFilename = command.fileName().strip();
    if (rawFilename.isEmpty() || rawFilename.length() > 500) {
      throw new IllegalArgumentException("fileName must be non-blank and at most 500 characters");
    }

    String safeFilename = Path.of(rawFilename.replace('\\', '/')).getFileName().toString().strip();
    String lowerName = safeFilename.toLowerCase(Locale.ROOT);

    DocumentFormat format;
    String canonicalContentType;
    String extension;
    if (lowerName.endsWith(".epub")) {
      format = DocumentFormat.EPUB;
      canonicalContentType = "application/epub+zip";
      extension = "epub";
    } else if (lowerName.endsWith(".pdf")) {
      format = DocumentFormat.PDF;
      canonicalContentType = "application/pdf";
      extension = "pdf";
    } else {
      throw new IllegalArgumentException("Only .epub and .pdf files are supported");
    }

    if (command.contentType() != null && !command.contentType().isBlank()) {
      String hint = command.contentType().strip();
      if (!hint.equals("application/octet-stream")
          && !hint.equalsIgnoreCase(canonicalContentType)) {
        throw new IllegalArgumentException(
            "Content-Type '"
                + hint
                + "' does not match extension expected type '"
                + canonicalContentType
                + "'");
      }
    }

    if (command.sizeBytes() <= 0) {
      throw new IllegalArgumentException("sizeBytes must be strictly positive");
    }

    if (command.sizeBytes() > limits.maxSourceBytes()) {
      throw new IllegalArgumentException(
          "sizeBytes ("
              + command.sizeBytes()
              + ") exceeds maximum limit: "
              + limits.maxSourceBytes());
    }

    String normalizedChecksum = command.checksumSha256().strip().toLowerCase(Locale.ROOT);
    if (!SHA256_PATTERN.matcher(normalizedChecksum).matches()) {
      throw new IllegalArgumentException(
          "checksumSha256 must contain exactly 64 lowercase hexadecimal characters");
    }

    UUID uploadId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    String storageKey = "documents/" + command.userId() + "/" + documentId + "/source." + extension;

    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime expiresAt = now.plus(properties.uploadIntentDuration());

    DocumentUpload upload =
        new DocumentUpload(
            uploadId,
            documentId,
            command.userId(),
            safeFilename,
            format,
            canonicalContentType,
            command.sizeBytes(),
            normalizedChecksum,
            storageKey,
            StorageProvider.S3,
            DocumentUploadStatus.PENDING,
            expiresAt,
            null,
            null,
            now,
            now,
            0);

    documentUploads.save(upload);

    UploadAuthorization auth =
        storagePort.createUploadAuthorization(
            storageKey, canonicalContentType, normalizedChecksum, properties.presignDuration());

    return new DocumentUploadIntentResult(
        uploadId,
        documentId,
        auth.uploadUrl(),
        auth.httpMethod(),
        auth.expiresAt(),
        auth.requiredHeaders());
  }
}
