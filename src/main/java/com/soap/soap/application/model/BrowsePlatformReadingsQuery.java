package com.soap.soap.application.model;

import com.soap.soap.domain.model.DiscoveryTopic;
import com.soap.soap.domain.model.EditorialCategory;
import com.soap.soap.domain.model.EditorialLevel;

public record BrowsePlatformReadingsQuery(
    String collectionKey,
    EditorialCategory category,
    EditorialLevel editorialLevel,
    String countryCode,
    DiscoveryTopic discoveryTopic,
    PageRequest pageRequest) {

  public BrowsePlatformReadingsQuery(
      String collectionKey,
      EditorialCategory category,
      EditorialLevel editorialLevel,
      PageRequest pageRequest) {
    this(collectionKey, category, editorialLevel, null, null, pageRequest);
  }
}
