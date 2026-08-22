package com.soap.soap.domain.model;

import com.soap.soap.domain.exception.InvalidVocabularyStateException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record UserVocabulary(
    UUID id,
    User user,
    Word word,
    VocabularyStatus status,
    LocalDateTime firstSeenAt,
    LocalDateTime learnedAt,
    Long version) {

  public UserVocabulary(
      UUID id,
      User user,
      Word word,
      VocabularyStatus status,
      LocalDateTime firstSeenAt,
      LocalDateTime learnedAt) {
    this(id, user, word, status, firstSeenAt, learnedAt, null);
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
  }

  public UserVocabulary changeStatus(VocabularyStatus newStatus, Clock clock) {
    Objects.requireNonNull(newStatus, "Vocabulary status must not be null");
    Objects.requireNonNull(clock, "Clock must not be null");
    var newLearnedAt =
        newStatus == VocabularyStatus.KNOWN
            ? status == VocabularyStatus.KNOWN && learnedAt != null
                ? learnedAt
                : LocalDateTime.now(clock)
            : null;
    return new UserVocabulary(id, user, word, newStatus, firstSeenAt, newLearnedAt, version);
  }
}
