package com.soap.soap.infrastructure.document;

import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.exception.DocumentImportException.Reason;
import com.soap.soap.application.model.DocumentImportLimits;
import java.util.function.LongSupplier;

/**
 * Per-document resource budget, checked progressively while parsing (BACK-002).
 *
 * <p>It tracks the bytes really produced by decompression, the extracted characters and a monotonic
 * deadline ({@link System#nanoTime()} by default, injectable for deterministic tests). Every
 * violation is a permanent {@link Reason#SECURITY_LIMIT_EXCEEDED} failure: the same document would
 * exceed the same limit again, so retrying would only repeat the resource consumption. Messages
 * carry counts and limits for logs, never document content.
 */
final class DocumentParseBudget {
  private final DocumentImportLimits limits;
  private final LongSupplier nanoTime;
  private final long deadlineNanos;
  private long uncompressedBytes;
  private long characters;

  DocumentParseBudget(DocumentImportLimits limits, LongSupplier nanoTime) {
    this.limits = limits;
    this.nanoTime = nanoTime;
    this.deadlineNanos = nanoTime.getAsLong() + limits.maxProcessingTime().toNanos();
  }

  /** Adds bytes actually read from a decompressed ZIP entry and fails as soon as they overflow. */
  void addUncompressedBytes(long bytes) {
    uncompressedBytes += bytes;
    if (uncompressedBytes > limits.maxExpandedBytes()) {
      throw exceeded(
          "EPUB actual uncompressed bytes exceed limit (read="
              + uncompressedBytes
              + ", limit="
              + limits.maxExpandedBytes()
              + ")");
    }
  }

  /** Adds extracted characters and fails as soon as the running total overflows the limit. */
  void addCharacters(long count) {
    characters += count;
    if (characters > limits.maxExtractedCharacters()) {
      throw exceeded(
          "Extracted characters exceed limit (extracted="
              + characters
              + ", limit="
              + limits.maxExtractedCharacters()
              + ")");
    }
  }

  void checkDeadline(String phase) {
    if (remainingNanos() <= 0) throw timeout(phase);
  }

  long remainingNanos() {
    return deadlineNanos - nanoTime.getAsLong();
  }

  DocumentImportException timeout(String phase) {
    return exceeded(
        "Document processing time limit exceeded during "
            + phase
            + " (limit="
            + limits.maxProcessingTime()
            + ")");
  }

  long uncompressedBytes() {
    return uncompressedBytes;
  }

  long characters() {
    return characters;
  }

  private static DocumentImportException exceeded(String message) {
    return new DocumentImportException(Reason.SECURITY_LIMIT_EXCEEDED, message);
  }
}
