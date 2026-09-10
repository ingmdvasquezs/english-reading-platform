package com.soap.soap.spike.epub;

record EpubSpikeLimits(
    long maxArchiveBytes,
    int maxEntries,
    long maxEntryBytes,
    long maxExpandedBytes,
    double maxCompressionRatio,
    int maxImagePixels) {

  static EpubSpikeLimits defaults() {
    return new EpubSpikeLimits(50L << 20, 2_000, 10L << 20, 150L << 20, 100, 25_000_000);
  }
}
