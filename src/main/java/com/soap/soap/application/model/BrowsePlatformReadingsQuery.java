package com.soap.soap.application.model;

import com.soap.soap.domain.model.DiscoveryTopic;
import com.soap.soap.domain.model.EditorialCategory;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.PlatformReadingSort;

public record BrowsePlatformReadingsQuery(
    String collectionKey,
    EditorialCategory category,
    EditorialLevel editorialLevel,
    String countryCode,
    DiscoveryTopic discoveryTopic,
    PlatformReadingSort sort,
    PageRequest pageRequest) {

  public BrowsePlatformReadingsQuery {
    if (sort == null) {
      sort = PlatformReadingSort.DEFAULT;
    }
  }

  public BrowsePlatformReadingsQuery(
      String collectionKey,
      EditorialCategory category,
      EditorialLevel editorialLevel,
      String countryCode,
      DiscoveryTopic discoveryTopic,
      PageRequest pageRequest) {
    this(
        collectionKey,
        category,
        editorialLevel,
        countryCode,
        discoveryTopic,
        PlatformReadingSort.DEFAULT,
        pageRequest);
  }

  public BrowsePlatformReadingsQuery(
      String collectionKey,
      EditorialCategory category,
      EditorialLevel editorialLevel,
      PageRequest pageRequest) {
    this(
        collectionKey,
        category,
        editorialLevel,
        null,
        null,
        PlatformReadingSort.DEFAULT,
        pageRequest);
  }
}
