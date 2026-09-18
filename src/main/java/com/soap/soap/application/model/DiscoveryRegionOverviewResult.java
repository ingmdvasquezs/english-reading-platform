package com.soap.soap.application.model;

import java.util.List;

public record DiscoveryRegionOverviewResult(
    DiscoveryRegionDetails region, List<DiscoveryCountrySummary> countries) {}
