package com.soap.soap.application.service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SpecializedDiscoveryBlockRegistry {

  private static final Map<String, Set<String>> SPECIALIZED_BLOCK_COLLECTIONS =
      Map.of("latin-america", Set.of("colombian-myths-legends"));

  public boolean isOwnedBySpecializedBlock(String collectionKey) {
    if (collectionKey == null || collectionKey.isBlank()) {
      return false;
    }
    return SPECIALIZED_BLOCK_COLLECTIONS.values().stream()
        .anyMatch(keys -> keys.contains(collectionKey));
  }

  public Set<String> getOwnedCollectionKeys(String blockKey) {
    if (blockKey == null || blockKey.isBlank()) {
      return Collections.emptySet();
    }
    return SPECIALIZED_BLOCK_COLLECTIONS.getOrDefault(blockKey, Collections.emptySet());
  }

  public Set<String> getAllSpecializedCollectionKeys() {
    return SPECIALIZED_BLOCK_COLLECTIONS.values().stream()
        .flatMap(Set::stream)
        .collect(Collectors.toUnmodifiableSet());
  }
}
