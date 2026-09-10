package com.soap.soap.domain.model;

import java.util.UUID;

public record ReadingCollection(
    UUID id,
    String key,
    String displayName,
    String description,
    int displayOrder,
    boolean active,
    String coverKey) {}
