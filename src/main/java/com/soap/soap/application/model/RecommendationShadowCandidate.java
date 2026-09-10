package com.soap.soap.application.model;

public record RecommendationShadowCandidate(
    RecommendedPlatformReading reading,
    PedagogicalRecommendationScore v1Score,
    RecommendationEvidenceV2 v2Evidence) {}
