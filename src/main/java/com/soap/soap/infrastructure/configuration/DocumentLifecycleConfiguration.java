package com.soap.soap.infrastructure.configuration;

import com.soap.soap.application.model.DocumentLifecyclePolicy;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Thresholds for document lifecycle maintenance; validated at startup by the policy record. */
@Configuration
public class DocumentLifecycleConfiguration {

  @Bean
  DocumentLifecyclePolicy documentLifecyclePolicy(
      @Value("${app.document-lifecycle.batch-size:100}") int batchSize,
      @Value("${app.document-lifecycle.expired-upload-grace:1h}") Duration expiredUploadGrace,
      @Value("${app.document-lifecycle.legacy-processing-stale-after:1h}")
          Duration legacyProcessingStaleAfter,
      @Value("${app.document-lifecycle.durable-lease-stale-after:30m}")
          Duration durableLeaseStaleAfter) {
    return new DocumentLifecyclePolicy(
        batchSize, expiredUploadGrace, legacyProcessingStaleAfter, durableLeaseStaleAfter);
  }
}
