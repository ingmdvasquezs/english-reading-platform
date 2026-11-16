package com.soap.soap.application.service;

import com.soap.soap.application.port.out.StaleImportRepositoryPort;
import com.soap.soap.application.port.out.StaleImportRepositoryPort.StaleDurableImport;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fails one abandoned durable job and its document atomically. The job update re-checks that the
 * lease is still expired, so a worker that renewed its lease after the scan is left alone. A worker
 * that later wakes up with the old lease cannot complete either: completion requires the job to
 * still be PROCESSING with its lease token (existing fencing), and a redelivered SQS message for a
 * FAILED job is acknowledged as terminal.
 */
@Component
@RequiredArgsConstructor
public class StaleDurableImportRecoverer {
  private final StaleImportRepositoryPort lifecycle;

  @Transactional
  public boolean recover(
      StaleDurableImport candidate, LocalDateTime leaseEndedBefore, LocalDateTime now) {
    if (!lifecycle.failStaleDurableJob(
        candidate.jobId(),
        leaseEndedBefore,
        now,
        StaleImportRecoveryService.STALE_ERROR_CODE,
        "Import lease expired before " + leaseEndedBefore + " without completion")) {
      return false;
    }
    lifecycle.failProcessingDocumentWithoutActiveJob(candidate.documentId(), now);
    return true;
  }
}
