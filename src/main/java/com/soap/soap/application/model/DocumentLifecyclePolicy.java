package com.soap.soap.application.model;

import java.time.Duration;

/**
 * Thresholds for document lifecycle maintenance.
 *
 * @param batchSize maximum candidates handled per run and per category
 * @param expiredUploadGrace how long after {@code expires_at} a PENDING upload is cleaned up
 */
public record DocumentLifecyclePolicy(int batchSize, Duration expiredUploadGrace) {

  public DocumentLifecyclePolicy {
    if (batchSize <= 0 || batchSize > 1_000) {
      throw new IllegalArgumentException(
          "Document lifecycle batch size must be between 1 and 1000");
    }
    requirePositive(expiredUploadGrace, "expired upload grace");
  }

  private static void requirePositive(Duration value, String name) {
    if (value == null || value.isNegative() || value.isZero()) {
      throw new IllegalArgumentException("Document lifecycle " + name + " must be positive");
    }
  }
}
