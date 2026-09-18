package com.soap.soap.application.model;

import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.model.SrsState;
import java.time.LocalDateTime;
import java.util.UUID;

public record VocabularyReviewHistoryEntry(
    UUID id,
    UUID userVocabularyId,
    UUID userId,
    LocalDateTime reviewedAt,
    ReviewRating rating,
    SrsState previousSrsState,
    SrsState newSrsState,
    long previousIntervalSeconds,
    long newIntervalSeconds,
    double previousStability,
    double newStability,
    double previousDifficulty,
    double newDifficulty,
    double elapsedDays,
    double scheduledDays) {}
