package com.soap.soap.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for recovering imports stuck in PROCESSING (BACK-004). Every transition is a
 * conditional update that repeats its eligibility predicate, so a candidate whose state changed
 * after the scan (for example a worker renewed its lease) is never overwritten.
 */
public interface StaleImportRepositoryPort {

  /** Durable (import_jobs) PROCESSING jobs whose lease ended before {@code leaseEndedBefore}. */
  List<StaleDurableImport> findStaleDurableImports(LocalDateTime leaseEndedBefore, int limit);

  /**
   * PROCESSING → FAILED for a durable job, only if its lease still ended before {@code
   * leaseEndedBefore}. A worker that renewed its lease meanwhile makes this a no-op.
   */
  boolean failStaleDurableJob(
      UUID jobId,
      LocalDateTime leaseEndedBefore,
      LocalDateTime now,
      String errorCode,
      String errorMessage);

  /**
   * PROCESSING → FAILED (reason IMPORT_FAILURE, deduplication released) for a document that no
   * longer has any PENDING or PROCESSING import job.
   */
  boolean failProcessingDocumentWithoutActiveJob(UUID documentId, LocalDateTime now);

  /**
   * Legacy multipart documents stuck in PROCESSING: FILESYSTEM source, no import_jobs row at all
   * (the legacy in-memory pipeline never creates one) and last updated before {@code
   * updatedBefore}.
   */
  List<StaleLegacyImport> findStaleLegacyImports(LocalDateTime updatedBefore, int limit);

  /** PROCESSING → FAILED for a legacy document, re-validating the same predicate. */
  boolean failStaleLegacyImport(UUID documentId, LocalDateTime updatedBefore, LocalDateTime now);

  record StaleDurableImport(UUID jobId, UUID documentId, UUID userId) {}

  record StaleLegacyImport(UUID documentId, UUID userId) {}
}
