package com.soap.soap.application.model;

import com.soap.soap.domain.model.EditorialCategory;
import com.soap.soap.domain.model.EditorialLevel;

public record BrowsePlatformReadingsQuery(
    String collectionKey,
    EditorialCategory category,
    EditorialLevel editorialLevel,
    PageRequest pageRequest) {}
