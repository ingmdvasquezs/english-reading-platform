package com.soap.soap.application.model;

import java.util.List;

public record DiscoveryShelfResult(
    String key,
    String title,
    String description,
    int displayOrder,
    String coverKey,
    String type,
    int totalReadings,
    List<RecommendedPlatformReading> readings) {}
