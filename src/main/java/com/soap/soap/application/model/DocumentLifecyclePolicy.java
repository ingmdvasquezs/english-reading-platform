package com.soap.soap.application.model;

import java.time.Duration;

/**
 * Thresholds for document lifecycle maintenance.
 *
 * @param batchSize maximum candidates handled per run and per category
 * @param expiredUploadGrace how long after {@code expires_at} a PENDING upload is cleaned up
 * @param legacyProcessingStaleAfter age after which a legacy (in-memory executor) PROCESSING
 *     document is considered interrupted
 * @param durableLeaseStaleAfter how long after its lease ended a durable PROCESSING job is
 *     considered abandoned
 */
public record DocumentLifecyclePolicy(
    int batchSize,
    Duration expiredUploadGrace,
    Duration legacyProcessingStaleAfter,
    Duration durableLeaseStaleAfter) {

  public DocumentLifecyclePolicy {
    if (batchSize <= 0 || batchSize > 1_000) {
      throw new IllegalArgumentException(
          "Document lifecycle batch size must be between 1 and 1000");
    }
    requirePositive(expiredUploadGrace, "expired upload grace");
    requirePositive(legacyProcessingStaleAfter, "legacy processing stale-after");
    requirePositive(durableLeaseStaleAfter, "durable lease stale-after");
  }

  private static void requirePositive(Duration value, String name) {
    if (value == null || value.isNegative() || value.isZero()) {
      throw new IllegalArgumentException("Document lifecycle " + name + " must be positive");
    }
  }
}
