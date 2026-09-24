package com.soap.soap.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record ImportedDocument(
    UUID id,
    UUID ownerId,
    String title,
    String author,
    String language,
    DocumentFormat format,
    String coverAssetKey,
    String sourceAssetKey,
    StorageProvider sourceStorageProvider,
    String originalFilename,
    String sourceSha256,
    DocumentImportStatus importStatus,
    String failureReason,
    int chunkingVersion,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  private static final Pattern LANGUAGE = Pattern.compile("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$");
  private static final Pattern SHA_256 = Pattern.compile("^[a-f0-9]{64}$");

  public ImportedDocument {
    Objects.requireNonNull(ownerId, "Document owner id must not be null");
    title = requireText(title, "Document title");
    language = requireText(language, "Document language");
    if (!LANGUAGE.matcher(language).matches()) {
      throw new IllegalArgumentException("Document language must be a valid BCP 47 tag");
    }
    Objects.requireNonNull(format, "Document format must not be null");
    Objects.requireNonNull(sourceStorageProvider, "sourceStorageProvider must not be null");
    sourceSha256 = requireText(sourceSha256, "Document source SHA-256");
    if (!SHA_256.matcher(sourceSha256).matches()) {
      throw new IllegalArgumentException(
          "Document source SHA-256 must contain 64 lowercase hex digits");
    }
    Objects.requireNonNull(importStatus, "Document import status must not be null");
    failureReason = optionalText(failureReason, "Document failure reason");
    if (importStatus == DocumentImportStatus.FAILED && failureReason == null) {
      throw new IllegalArgumentException("Failed document must have a failure reason");
    }
    if (importStatus != DocumentImportStatus.FAILED && failureReason != null) {
      throw new IllegalArgumentException("Only failed documents may have a failure reason");
    }
    if (chunkingVersion <= 0) {
      throw new IllegalArgumentException("Document chunking version must be positive");
    }
    Objects.requireNonNull(createdAt, "Document createdAt must not be null");
    Objects.requireNonNull(updatedAt, "Document updatedAt must not be null");
    if (updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("Document updatedAt must not precede createdAt");
    }
    author = optionalText(author, "Document author");
    coverAssetKey = optionalText(coverAssetKey, "Document cover asset key");
    sourceAssetKey = optionalText(sourceAssetKey, "Document source asset key");
    originalFilename = optionalText(originalFilename, "Document original filename");
  }

  public ImportedDocument(
      UUID id,
      UUID ownerId,
      String title,
      String author,
      String language,
      DocumentFormat format,
      String coverAssetKey,
      String sourceAssetKey,
      String originalFilename,
      String sourceSha256,
      DocumentImportStatus importStatus,
      String failureReason,
      int chunkingVersion,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this(
        id,
        ownerId,
        title,
        author,
        language,
        format,
        coverAssetKey,
        sourceAssetKey,
        StorageProvider.FILESYSTEM,
        originalFilename,
        sourceSha256,
        importStatus,
        failureReason,
        chunkingVersion,
        createdAt,
        updatedAt);
  }

  public ImportedDocument(
      UUID id,
      UUID ownerId,
      String title,
      String author,
      String language,
      DocumentFormat format,
      String coverAssetKey,
      String sourceAssetKey,
      String originalFilename,
      String sourceSha256,
      DocumentImportStatus importStatus,
      int chunkingVersion,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this(
        id,
        ownerId,
        title,
        author,
        language,
        format,
        coverAssetKey,
        sourceAssetKey,
        StorageProvider.FILESYSTEM,
        originalFilename,
        sourceSha256,
        importStatus,
        null,
        chunkingVersion,
        createdAt,
        updatedAt);
  }

  private static String requireText(String value, String field) {
    Objects.requireNonNull(value, field + " must not be null");
    var normalized = value.strip();
    if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
    return normalized;
  }

  private static String optionalText(String value, String field) {
    if (value == null) return null;
    return requireText(value, field);
  }
}
