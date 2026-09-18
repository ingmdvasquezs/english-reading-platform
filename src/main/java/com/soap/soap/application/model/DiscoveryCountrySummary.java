package com.soap.soap.application.model;

import java.util.List;

public record DiscoveryCountrySummary(
    String countryCode,
    String displayName,
    String tagline,
    String description,
    int displayOrder,
    int readingCount,
    List<DiscoveryHeroImageSummary> heroImages,
    List<DiscoveryTopicSummary> topics) {}
