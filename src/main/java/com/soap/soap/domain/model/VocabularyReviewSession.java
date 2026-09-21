package com.soap.soap.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record VocabularyReviewSession(
    UUID id,
    UUID userId,
    LocalDate localReviewDate,
    ReviewSessionStatus status,
    int dailyLimit,
    long nextQueueSequence,
    LocalDateTime createdAt,
    LocalDateTime completedAt,
    List<VocabularyReviewSessionItem> items) {

  public VocabularyReviewSession withStatus(ReviewSessionStatus status, LocalDateTime completedAt) {
    return new VocabularyReviewSession(
        id,
        userId,
        localReviewDate,
        status,
        dailyLimit,
        nextQueueSequence,
        createdAt,
        completedAt,
        items);
  }

  public VocabularyReviewSession withNextQueueSequence(long nextQueueSequence) {
    return new VocabularyReviewSession(
        id,
        userId,
        localReviewDate,
        status,
        dailyLimit,
        nextQueueSequence,
        createdAt,
        completedAt,
        items);
  }

  public VocabularyReviewSession withItems(List<VocabularyReviewSessionItem> items) {
    return new VocabularyReviewSession(
        id,
        userId,
        localReviewDate,
        status,
        dailyLimit,
        nextQueueSequence,
        createdAt,
        completedAt,
        items);
  }
}
