package com.soap.soap.application.port.out;

import com.soap.soap.domain.model.StoredObjectAttributes;
import com.soap.soap.domain.model.UploadAuthorization;
import java.time.Duration;

public interface DocumentObjectStoragePort {

  UploadAuthorization createUploadAuthorization(
      String storageKey,
      String canonicalContentType,
      String checksumSha256Hex,
      Duration presignDuration);

  StoredObjectAttributes inspectObject(String storageKey);

  void deleteObject(String storageKey);
}
