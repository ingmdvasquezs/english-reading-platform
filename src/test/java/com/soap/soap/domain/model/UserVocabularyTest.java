package com.soap.soap.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.domain.exception.InvalidVocabularyStateException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserVocabularyTest {

  private final User user = new User(UUID.randomUUID(), "Ada", "ada@example.com");

  @Test
  void userStringRepresentationNeverExposesThePasswordHash() {
    var securedUser =
        new User(UUID.randomUUID(), "Ada", "ada@example.com", "super-secret-hash", null);
    assertThat(securedUser.toString()).doesNotContain("super-secret-hash", "passwordHash");
  }

  private final Word word = new Word(UUID.randomUUID(), "hello", "en");
  private final LocalDateTime firstSeenAt = LocalDateTime.parse("2026-08-18T12:00:00");
  private final Clock clock = Clock.fixed(Instant.parse("2026-08-19T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void knownVocabularyRequiresALearnedDate() {
    assertThatThrownBy(
            () ->
                new UserVocabulary(
                    UUID.randomUUID(), user, word, VocabularyStatus.KNOWN, firstSeenAt, null))
        .isInstanceOf(InvalidVocabularyStateException.class);
  }

  @Test
  void vocabularyThatIsNotKnownCannotHaveALearnedDate() {
    assertThatThrownBy(
            () ->
                new UserVocabulary(
                    UUID.randomUUID(),
                    user,
                    word,
                    VocabularyStatus.LEARNING,
                    firstSeenAt,
                    firstSeenAt.plusDays(1)))
        .isInstanceOf(InvalidVocabularyStateException.class);
  }

  @Test
  void changingToKnownRecordsWhenTheWordWasLearned() {
    var vocabulary =
        new UserVocabulary(
            UUID.randomUUID(), user, word, VocabularyStatus.LEARNING, firstSeenAt, null);

    var changed = vocabulary.changeStatus(VocabularyStatus.KNOWN, clock);

    assertThat(changed.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(changed.learnedAt()).isEqualTo(LocalDateTime.now(clock));
  }

  @Test
  void changingAnAlreadyKnownWordPreservesItsLearnedDate() {
    var learnedAt = firstSeenAt.plusHours(1);
    var vocabulary =
        new UserVocabulary(
            UUID.randomUUID(), user, word, VocabularyStatus.KNOWN, firstSeenAt, learnedAt);

    assertThat(vocabulary.changeStatus(VocabularyStatus.KNOWN, clock).learnedAt())
        .isEqualTo(learnedAt);
  }

  @Test
  void ignoredIsExplicitlyDifferentFromKnownAndHasNoLearnedDate() {
    var known =
        new UserVocabulary(
            UUID.randomUUID(), user, word, VocabularyStatus.KNOWN, firstSeenAt, firstSeenAt);

    var ignored = known.changeStatus(VocabularyStatus.IGNORED, clock);

    assertThat(ignored.status()).isEqualTo(VocabularyStatus.IGNORED);
    assertThat(ignored.status()).isNotEqualTo(VocabularyStatus.KNOWN);
    assertThat(ignored.learnedAt()).isNull();
  }
}
