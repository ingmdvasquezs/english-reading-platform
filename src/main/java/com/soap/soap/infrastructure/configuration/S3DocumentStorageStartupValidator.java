package com.soap.soap.infrastructure.configuration;

import com.soap.soap.domain.model.StorageProvider;
import com.soap.soap.infrastructure.storage.S3BucketNames;
import com.soap.soap.infrastructure.storage.S3DocumentStorageProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Fails application startup when the S3 document storage provider is active but the bucket is
 * missing, blank, an unresolved placeholder or otherwise not a valid bucket name (BACK-022).
 *
 * <p>The provider is {@code S3} by default and pinned to {@code S3} in the {@code prod} profile.
 * The {@code local} profile defaults to {@code FILESYSTEM} so everyday development does not need
 * AWS configuration; set {@code DOCUMENT_STORAGE_PROVIDER=S3} plus a bucket to exercise direct S3
 * uploads locally.
 */
@Component
public class S3DocumentStorageStartupValidator implements InitializingBean {
  private final S3DocumentStorageProperties properties;

  public S3DocumentStorageStartupValidator(S3DocumentStorageProperties properties) {
    this.properties = properties;
  }

  @Override
  public void afterPropertiesSet() {
    if (properties.provider() == StorageProvider.S3) {
      S3BucketNames.requireValid(properties.s3().bucket());
    }
  }
}
