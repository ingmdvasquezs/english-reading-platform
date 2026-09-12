package com.soap.soap.domain.model;

import com.soap.soap.domain.exception.InvalidVocabularyStateException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

public record UserVocabulary(
    UUID id,
    User user,
    Word word,
    VocabularyStatus status,
    LocalDateTime firstSeenAt,
    LocalDateTime learnedAt,
    Long version,
    int reviewStage,
    LocalDateTime lastReviewedAt,
    LocalDateTime nextReviewAt) {

  public UserVocabulary(
      UUID id,
      User user,
      Word word,
      VocabularyStatus status,
      LocalDateTime firstSeenAt,
      LocalDateTime learnedAt) {
    this(id, user, word, status, firstSeenAt, learnedAt, null, 0, null, null);
  }

  public UserVocabulary(
      UUID id,
      User user,
      Word word,
      VocabularyStatus status,
      LocalDateTime firstSeenAt,
      LocalDateTime learnedAt,
      Long version) {
    this(id, user, word, status, firstSeenAt, learnedAt, version, 0, null, null);
  }

  public UserVocabulary {
    Objects.requireNonNull(user, "User must not be null");
    Objects.requireNonNull(word, "Word must not be null");
    Objects.requireNonNull(status, "Vocabulary status must not be null");
    Objects.requireNonNull(firstSeenAt, "First seen date must not be null");
    if (status == VocabularyStatus.KNOWN && learnedAt == null) {
      throw new InvalidVocabularyStateException("Known vocabulary must have a learned date");
    }
    if (status != VocabularyStatus.KNOWN && learnedAt != null) {
      throw new InvalidVocabularyStateException("Only known vocabulary can have a learned date");
    }
    if (reviewStage < 0 || reviewStage > 5) {
      throw new InvalidVocabularyStateException("Review stage must be between 0 and 5");
    }
  }

  public static UserVocabulary createInitial(
      User user, Word word, VocabularyStatus status, Clock clock) {
    Objects.requireNonNull(user, "User must not be null");
    Objects.requireNonNull(word, "Word must not be null");
    Objects.requireNonNull(status, "Vocabulary status must not be null");
    Objects.requireNonNull(clock, "Clock must not be null");
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    int reviewStage = (status == VocabularyStatus.KNOWN) ? 3 : 0;
    LocalDateTime nextReview =
        switch (status) {
          case LEARNING -> nowUtc;
          case KNOWN -> nowUtc.plusDays(14);
          case NEW, IGNORED -> null;
        };
    var learnedAt = status == VocabularyStatus.KNOWN ? nowUtc : null;
    return new UserVocabulary(
        null, user, word, status, nowUtc, learnedAt, null, reviewStage, null, nextReview);
  }

  public UserVocabulary changeStatus(VocabularyStatus newStatus, Clock clock) {
    Objects.requireNonNull(newStatus, "Vocabulary status must not be null");
    Objects.requireNonNull(clock, "Clock must not be null");
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    return switch (newStatus) {
      case KNOWN -> {
        var newLearnedAt =
            (status == VocabularyStatus.KNOWN && learnedAt != null) ? learnedAt : nowUtc;
        var newStage = Math.max(3, reviewStage);
        var nextReview = (newStage == 3) ? nowUtc.plusDays(14) : nowUtc.plusDays(30);
        yield new UserVocabulary(
            id,
            user,
            word,
            VocabularyStatus.KNOWN,
            firstSeenAt,
            newLearnedAt,
            version,
            newStage,
            lastReviewedAt,
            nextReview);
      }
      case LEARNING ->
          new UserVocabulary(
              id,
              user,
              word,
              VocabularyStatus.LEARNING,
              firstSeenAt,
              null,
              version,
              0,
              null,
              nowUtc);
      case NEW ->
          new UserVocabulary(
              id, user, word, VocabularyStatus.NEW, firstSeenAt, null, version, 0, null, null);
      case IGNORED ->
          new UserVocabulary(
              id, user, word, VocabularyStatus.IGNORED, firstSeenAt, null, version, 0, null, null);
    };
  }

  public UserVocabulary applyReviewAssessment(ReviewAssessment assessment, Clock clock) {
    Objects.requireNonNull(assessment, "Review assessment must not be null");
    Objects.requireNonNull(clock, "Clock must not be null");
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

    return switch (assessment) {
      case FORGOT ->
          new UserVocabulary(
              id,
              user,
              word,
              VocabularyStatus.LEARNING,
              firstSeenAt,
              null,
              version,
              0,
              nowUtc,
              nowUtc.plusDays(1));
      case STRUGGLED ->
          new UserVocabulary(
              id,
              user,
              word,
              VocabularyStatus.LEARNING,
              firstSeenAt,
              null,
              version,
              reviewStage,
              nowUtc,
              nowUtc.plusDays(1));
      case REMEMBERED -> {
        var newStage = Math.min(5, reviewStage + 1);
        var intervalDays = reviewIntervalDays(newStage);
        var nextReview = nowUtc.plusDays(intervalDays);
        var newLearnedAt =
            (status == VocabularyStatus.KNOWN && learnedAt != null) ? learnedAt : nowUtc;
        yield new UserVocabulary(
            id,
            user,
            word,
            VocabularyStatus.KNOWN,
            firstSeenAt,
            newLearnedAt,
            version,
            newStage,
            nowUtc,
            nextReview);
      }
    };
  }

  public static int reviewIntervalDays(int stage) {
    return switch (stage) {
      case 0 -> 1;
      case 1 -> 3;
      case 2 -> 7;
      case 3 -> 14;
      case 4, 5 -> 30;
      default -> 30;
    };
  }
}
