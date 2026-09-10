package com.soap.soap.application.model;

import java.math.BigDecimal;

public record PedagogicalRecommendationScore(
    BigDecimal score,
    BigDecimal knownPercentage,
    BigDecimal learningPercentage,
    BigDecimal accessiblePercentage,
    BigDecimal challengePercentage,
    BigDecimal explicitNewPercentage,
    BigDecimal unclassifiedPercentage) {}
