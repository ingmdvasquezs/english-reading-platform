package com.soap.soap.application.model;

public record DocumentImportLimits(
    long maxSourceBytes,
    int maxZipEntries,
    long maxEntryBytes,
    long maxExpandedBytes,
    double maxCompressionRatio,
    long maxCoverBytes,
    int maxCoverPixels,
    int maxPdfPages,
    int maxExtractedCharacters) {

  public DocumentImportLimits {
    if (maxSourceBytes <= 0
        || maxZipEntries <= 0
        || maxEntryBytes <= 0
        || maxExpandedBytes <= 0
        || maxCompressionRatio <= 0
        || maxCoverBytes <= 0
        || maxCoverPixels <= 0
        || maxPdfPages <= 0
        || maxExtractedCharacters <= 0) {
      throw new IllegalArgumentException("Document import limits must be positive");
    }
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
