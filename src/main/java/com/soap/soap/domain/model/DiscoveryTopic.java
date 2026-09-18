package com.soap.soap.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DiscoveryTopic {
  MYTHS_AND_LEGENDS("Mitos y leyendas", 1),
  REAL_STORIES("Historias reales", 2),
  HISTORY_AND_MEMORY("Historia y memoria", 3),
  CULTURE_AND_TRADITIONS("Cultura y tradiciones", 4),
  NATURE_AND_PLACES("Naturaleza y lugares", 5),
  PEOPLE("Personajes", 6);

  private final String displayName;
  private final int displayOrder;

  public static DiscoveryTopic fromString(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");
    for (DiscoveryTopic topic : values()) {
      if (topic.name().equalsIgnoreCase(normalized)) {
        return topic;
      }
    }
    throw new IllegalArgumentException("Unknown discovery topic: " + value);
  }
}
