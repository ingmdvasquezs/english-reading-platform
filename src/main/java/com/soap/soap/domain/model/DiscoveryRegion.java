package com.soap.soap.domain.model;

import java.util.UUID;

public record DiscoveryRegion(
    UUID id, String key, String displayName, String subtitle, int displayOrder, boolean active) {}
