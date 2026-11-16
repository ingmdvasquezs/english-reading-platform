package com.soap.soap.application.service;

import com.soap.soap.application.model.DocumentLifecyclePolicy;
import com.soap.soap.application.port.out.ExpiredUploadRepositoryPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * BACK-003: removes the S3 objects of upload intents that were never confirmed (for example the
 * browser was closed after the PUT) and marks the intents EXPIRED.
 *
 * <p>Cleanup is driven by database state, never by a broad bucket lifecycle rule, so sources of
 * live documents under the same {@code documents/} prefix are never touched. Only intents still
 * PENDING well after their expiry ({@code expires_at + grace}) are eligible; confirmation already
 * refuses expired intents under a row lock, so an eligible intent can no longer become a document.
 */
@Service
@RequiredArgsConstructor
public class ExpiredUploadCleanupService {
  private static final Logger LOG = LoggerFactory.getLogger(ExpiredUploadCleanupService.class);

  private final ExpiredUploadRepositoryPort lifecycle;
  private final ExpiredUploadExpirer expirer;
  private final DocumentLifecyclePolicy policy;
  private final Clock clock;
  private final MeterRegistry meters;

  /** Processes at most one batch; returns how many intents were expired. */
  public int cleanupExpiredUploads() {
    var now = LocalDateTime.now(clock);
    var expiredBefore = now.minus(policy.expiredUploadGrace());
    int expired = 0;
    for (var candidate : lifecycle.findExpiredPendingUploads(expiredBefore, policy.batchSize())) {
      try {
        if (expirer.expire(candidate.uploadId(), expiredBefore, now)) expired++;
      } catch (RuntimeException exception) {
        // The transaction rolled back: the intent stays PENDING and is retried on the next run.
        meters.counter("document.cleanup.failure", "type", "expired_upload").increment();
        LOG.warn(
            "document_cleanup_failed type=expired_upload uploadId={} documentId={} objectKey={}"
                + " reason={}",
            candidate.uploadId(),
            candidate.documentId(),
            candidate.storageKey(),
            exception.getClass().getSimpleName());
      }
    }
    return expired;
  }
}
