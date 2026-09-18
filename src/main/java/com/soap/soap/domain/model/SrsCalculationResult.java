package com.soap.soap.domain.model;

import java.time.LocalDateTime;

public record SrsCalculationResult(
    SrsState srsState,
    VocabularyStatus vocabularyStatus,
    double stability,
    double difficulty,
    int repetitions,
    int lapses,
    LocalDateTime nextReviewAt,
    long intervalSeconds) {}
