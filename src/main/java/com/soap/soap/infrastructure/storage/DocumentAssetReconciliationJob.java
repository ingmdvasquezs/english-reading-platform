package com.soap.soap.infrastructure.storage;

import com.soap.soap.application.port.out.DocumentAssetStoragePort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DocumentAssetReconciliationJob {
  private static final Logger LOG = LoggerFactory.getLogger(DocumentAssetReconciliationJob.class);
  private final ImportedDocumentRepositoryPort documents;
  private final DocumentAssetStoragePort storage;
  private final MeterRegistry meters;
  private final Clock clock;
  private final Duration minimumAge;

  public DocumentAssetReconciliationJob(
      ImportedDocumentRepositoryPort documents,
      DocumentAssetStoragePort storage,
      MeterRegistry meters,
      Clock clock,
      @Value("${app.documents.orphan-minimum-age:24h}") Duration minimumAge) {
    this.documents = documents;
    this.storage = storage;
    this.meters = meters;
    this.clock = clock;
    this.minimumAge = minimumAge;
  }

  @Scheduled(fixedDelayString = "${app.documents.reconciliation-delay:24h}")
  public void reconcile() {
    try {
      int deleted =
          storage.deleteUnreferenced(
              documents.findReferencedAssetKeys(), Instant.now(clock).minus(minimumAge));
      meters.counter("documents.assets.reconciled", "outcome", "deleted").increment(deleted);
      if (deleted > 0) LOG.info("document_asset_reconciliation deleted={}", deleted);
    } catch (RuntimeException exception) {
      meters.counter("documents.assets.reconciled", "outcome", "failed").increment();
      LOG.warn("document_asset_reconciliation_failed", exception);
    }
  }
}
