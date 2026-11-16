package com.soap.soap.application.service;

import com.soap.soap.application.model.DocumentLifecyclePolicy;
import com.soap.soap.application.port.out.StaleImportRepositoryPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * BACK-004: moves imports that can no longer finish from PROCESSING to FAILED, so the user sees the
 * failure, can delete the document and can upload the same file again (FAILED releases the
 * deduplication hash). Two pipelines with different evidence, never mixed:
 *
 * <ul>
 *   <li><b>Legacy multipart</b> (in-memory executor, no import_jobs row, FILESYSTEM source): there
 *       is no lease, so only age is available. A document still PROCESSING long after submission
 *       (default 1h, while parsing is bounded to 120s and the executor queue to minutes) was lost
 *       to a restart, deploy or OOM.
 *   <li><b>Durable</b> (import_jobs + SQS): a PROCESSING job is stale only when its lease ended
 *       long ago (default 30 min; a live worker renews every 20s). The conditional update re-checks
 *       the lease, so a worker that renews concurrently keeps its job.
 * </ul>
 *
 * Recovered documents use the existing {@code IMPORT_FAILURE} reason; the job keeps {@code
 * STALE_PROCESSING} as its error code for diagnosis.
 */
@Service
@RequiredArgsConstructor
public class StaleImportRecoveryService {
  static final String STALE_ERROR_CODE = "STALE_PROCESSING";
  private static final Logger LOG = LoggerFactory.getLogger(StaleImportRecoveryService.class);

  private final StaleImportRepositoryPort lifecycle;
  private final StaleDurableImportRecoverer durableRecoverer;
  private final DocumentLifecyclePolicy policy;
  private final Clock clock;
  private final MeterRegistry meters;

  public Result recoverStaleImports() {
    var now = LocalDateTime.now(clock);
    return new Result(recoverLegacy(now), recoverDurable(now));
  }

  private int recoverLegacy(LocalDateTime now) {
    var updatedBefore = now.minus(policy.legacyProcessingStaleAfter());
    int recovered = 0;
    for (var candidate : lifecycle.findStaleLegacyImports(updatedBefore, policy.batchSize())) {
      try {
        if (lifecycle.failStaleLegacyImport(candidate.documentId(), updatedBefore, now)) {
          recovered++;
          meters.counter("document.cleanup.stale_processing", "pipeline", "legacy").increment();
          LOG.warn(
              "document_cleanup type=stale_processing pipeline=legacy documentId={} userId={}"
                  + " transition=PROCESSING->FAILED reason=IMPORT_FAILURE",
              candidate.documentId(),
              candidate.userId());
        }
      } catch (RuntimeException exception) {
        failure("legacy", exception);
      }
    }
    return recovered;
  }

  private int recoverDurable(LocalDateTime now) {
    var leaseEndedBefore = now.minus(policy.durableLeaseStaleAfter());
    int recovered = 0;
    for (var candidate : lifecycle.findStaleDurableImports(leaseEndedBefore, policy.batchSize())) {
      try {
        if (durableRecoverer.recover(candidate, leaseEndedBefore, now)) {
          recovered++;
          meters.counter("document.cleanup.stale_processing", "pipeline", "durable").increment();
          LOG.warn(
              "document_cleanup type=stale_processing pipeline=durable jobId={} documentId={}"
                  + " userId={} transition=PROCESSING->FAILED reason={}",
              candidate.jobId(),
              candidate.documentId(),
              candidate.userId(),
              STALE_ERROR_CODE);
        }
      } catch (RuntimeException exception) {
        failure("durable", exception);
      }
    }
    return recovered;
  }

  private void failure(String pipeline, RuntimeException exception) {
    meters.counter("document.cleanup.failure", "type", "stale_" + pipeline).increment();
    LOG.warn(
        "document_cleanup_failed type=stale_processing pipeline={} reason={}",
        pipeline,
        exception.getClass().getSimpleName());
  }

  public record Result(int legacyRecovered, int durableRecovered) {}
}
