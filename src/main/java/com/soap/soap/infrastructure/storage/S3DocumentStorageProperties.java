package com.soap.soap.infrastructure.storage;

import com.soap.soap.domain.model.StorageProvider;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.document-storage")
public record S3DocumentStorageProperties(
    StorageProvider provider,
    Duration uploadIntentDuration,
    Duration presignDuration,
    S3Properties s3) {

  public S3DocumentStorageProperties {
    if (provider == null) {
      provider = StorageProvider.S3;
    }
    if (uploadIntentDuration == null) {
      uploadIntentDuration = Duration.ofHours(24);
    }
    if (presignDuration == null) {
      presignDuration = Duration.ofMinutes(10);
    }
    if (s3 == null) {
      s3 = new S3Properties(null, "us-east-1", null, false);
    }
  }

  public record S3Properties(
      String bucket, String region, URI endpointOverride, boolean pathStyleAccess) {

    public S3Properties {
      if (region == null || region.isBlank()) {
        region = "us-east-1";
      }
    }
  }
}
