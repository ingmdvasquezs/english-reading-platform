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

  void downloadObject(String storageKey, java.nio.file.Path destinationPath);

  /**
   * Deletes one object. Idempotent: an object that does not exist counts as deleted. Any other
   * storage failure is raised as {@link
   * com.soap.soap.application.exception.TransientStorageException} so callers decide whether the
   * deletion is best-effort or must be retried.
   */
  void deleteObject(String storageKey);
}
