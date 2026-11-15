package com.soap.soap.application.model;

import java.time.Duration;

/**
 * Resource limits for document parsing.
 *
 * <ul>
 *   <li>{@code maxSourceBytes}: uploaded file size.
 *   <li>{@code maxZipEntries}: EPUB ZIP entry count.
 *   <li>{@code maxEntryBytes}: per-entry uncompressed bytes, declared <em>and</em> actually read.
 *   <li>{@code maxExpandedBytes}: total uncompressed bytes of the EPUB, enforced on the declared
 *       sizes and on the bytes actually produced by decompression.
 *   <li>{@code maxExtractedCharacters}: total extracted text for EPUB and PDF.
 *   <li>{@code maxProcessingTime}: wall-clock budget for parsing one document.
 * </ul>
 */
public record DocumentImportLimits(
    long maxSourceBytes,
    int maxZipEntries,
    long maxEntryBytes,
    long maxExpandedBytes,
    double maxCompressionRatio,
    long maxCoverBytes,
    int maxCoverPixels,
    int maxPdfPages,
    int maxExtractedCharacters,
    Duration maxProcessingTime) {

  public static final Duration DEFAULT_MAX_PROCESSING_TIME = Duration.ofSeconds(120);

  public DocumentImportLimits {
    if (maxSourceBytes <= 0
        || maxZipEntries <= 0
        || maxEntryBytes <= 0
        || maxExpandedBytes <= 0
        || maxCompressionRatio <= 0
        || maxCoverBytes <= 0
        || maxCoverPixels <= 0
        || maxPdfPages <= 0
        || maxExtractedCharacters <= 0
        || maxProcessingTime == null
        || maxProcessingTime.isNegative()
        || maxProcessingTime.isZero()) {
      throw new IllegalArgumentException("Document import limits must be positive");
    }
    if (maxEntryBytes > maxExpandedBytes) {
      throw new IllegalArgumentException(
          "Document import per-entry byte limit must not exceed the total expanded byte limit");
    }
    if (maxEntryBytes >= Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Document import per-entry byte limit is too large");
    }
  }

  public DocumentImportLimits(
      long maxSourceBytes,
      int maxZipEntries,
      long maxEntryBytes,
      long maxExpandedBytes,
      double maxCompressionRatio,
      long maxCoverBytes,
      int maxCoverPixels,
      int maxPdfPages,
      int maxExtractedCharacters) {
    this(
        maxSourceBytes,
        maxZipEntries,
        maxEntryBytes,
        maxExpandedBytes,
        maxCompressionRatio,
        maxCoverBytes,
        maxCoverPixels,
        maxPdfPages,
        maxExtractedCharacters,
        DEFAULT_MAX_PROCESSING_TIME);
  }

  public DocumentImportLimits(
      long maxSourceBytes,
      int maxZipEntries,
      long maxEntryBytes,
      long maxExpandedBytes,
      double maxCompressionRatio,
      long maxCoverBytes,
      int maxCoverPixels) {
    this(
        maxSourceBytes,
        maxZipEntries,
        maxEntryBytes,
        maxExpandedBytes,
        maxCompressionRatio,
        maxCoverBytes,
        maxCoverPixels,
        1_000,
        5_000_000);
  }
}
