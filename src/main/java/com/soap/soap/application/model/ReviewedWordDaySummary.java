package com.soap.soap.application.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewedWordDaySummary(
    UUID userVocabularyId, LocalDateTime firstReviewedAt, LocalDateTime lastReviewedAt) {}
