package com.soap.soap.application.port.out;

import com.soap.soap.domain.model.DocumentFormat;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface DocumentAssetStoragePort {
  String storeSource(UUID userId, UUID documentId, Path source);

  default String storeSource(UUID userId, UUID documentId, Path source, DocumentFormat format) {
    return storeSource(userId, documentId, source);
  }

  String storeCover(UUID userId, UUID documentId, String mediaType, byte[] bytes);

  void delete(String assetKey);

  Path locate(String assetKey);

  int deleteUnreferenced(Set<String> referencedKeys, Instant olderThan);
}
