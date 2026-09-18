package com.soap.soap.domain.model;

import java.util.UUID;

public record DiscoveryHeroImage(
    UUID id, String assetKey, String location, String alt, int displayOrder) {}
