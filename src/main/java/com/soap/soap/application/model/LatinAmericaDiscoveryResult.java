package com.soap.soap.application.model;

import java.util.List;

public record LatinAmericaDiscoveryResult(
    DiscoveryRegionDetails region,
    List<DiscoveryCountrySummary> countries,
    String defaultCountryCode,
    String defaultTopicKey,
    List<RecommendedPlatformReading> readings) {}
