package com.soap.soap.infrastructure.storage;

import com.soap.soap.application.service.ExpiredUploadCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic, database-driven document lifecycle maintenance inside the existing runtime (no Lambda,
 * EventBridge or extra task). It only delegates: each category lives in its own application
 * service, processes one bounded batch per run and is safe to run concurrently on several instances
 * because every transition is a conditional, row-locked update.
 */
@Component
@ConditionalOnProperty(name = "app.document-lifecycle.enabled", havingValue = "true")
@ConditionalOnExpression(
    "'${APP_ROLE:${app.role:api}}'.equalsIgnoreCase('api') or '${APP_ROLE:${app.role:api}}'.equalsIgnoreCase('all')")
public class DocumentLifecycleMaintenanceJob {
  private static final Logger LOG = LoggerFactory.getLogger(DocumentLifecycleMaintenanceJob.class);

  private final ExpiredUploadCleanupService expiredUploads;

  public DocumentLifecycleMaintenanceJob(ExpiredUploadCleanupService expiredUploads) {
    this.expiredUploads = expiredUploads;
  }

  @Scheduled(
      fixedDelayString = "${app.document-lifecycle.interval:15m}",
      initialDelayString = "${app.document-lifecycle.initial-delay:5m}")
  public void run() {
    try {
      var expired = expiredUploads.cleanupExpiredUploads();
      if (expired > 0) LOG.info("document_lifecycle_run expiredUploads={}", expired);
    } catch (RuntimeException exception) {
      LOG.warn(
          "document_lifecycle_run_failed category=expired_uploads reason={}",
          exception.getClass().getSimpleName());
    }
  }
}
