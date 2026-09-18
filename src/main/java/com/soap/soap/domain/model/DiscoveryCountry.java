package com.soap.soap.domain.model;

import java.util.List;
import java.util.UUID;

public record DiscoveryCountry(
    UUID id,
    String countryCode,
    UUID regionId,
    String displayName,
    String tagline,
    String description,
    int displayOrder,
    boolean active,
    List<DiscoveryHeroImage> heroImages) {}
