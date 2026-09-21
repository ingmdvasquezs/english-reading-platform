package com.soap.soap.application.model;

import java.util.List;

public record DiscoveryHomeResult(
    List<ContinueReadingItem> continueReading,
    List<RecommendedPlatformReading> forYou,
    LatinAmericaDiscoveryResult latinAmerica,
    List<DiscoveryShelfResult> shelves) {}
