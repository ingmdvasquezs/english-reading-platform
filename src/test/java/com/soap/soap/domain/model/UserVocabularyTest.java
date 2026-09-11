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

  @Test
  void manualStatusTransitionsRespectAgreedRules() {
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var pastReview = nowUtc.minusDays(2);
    var base =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            firstSeenAt,
            null,
            0L,
            3,
            pastReview,
            nowUtc.plusDays(1));

    // manual -> KNOWN: reviewStage = max(3, actual), nextReviewAt scheduled (+14d for stage 3, +30d
    // for stage 4/5), lastReviewedAt preserved
    var toKnown = base.changeStatus(VocabularyStatus.KNOWN, clock);
    assertThat(toKnown.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(toKnown.reviewStage()).isEqualTo(3);
    assertThat(toKnown.nextReviewAt()).isEqualTo(nowUtc.plusDays(14));
    assertThat(toKnown.lastReviewedAt()).isEqualTo(pastReview);
    assertThat(toKnown.learnedAt()).isEqualTo(nowUtc);

    // manual -> KNOWN with stage 4 preserves stage 4 and schedules +30 days
    var stage4Base =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            firstSeenAt,
            null,
            0L,
            4,
            pastReview,
            nowUtc);
    var toKnownStage4 = stage4Base.changeStatus(VocabularyStatus.KNOWN, clock);
    assertThat(toKnownStage4.reviewStage()).isEqualTo(4);
    assertThat(toKnownStage4.nextReviewAt()).isEqualTo(nowUtc.plusDays(30));

    // manual -> KNOWN with stage 0 promotes to stage 3 and schedules +14 days
    var stage0Base =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            firstSeenAt,
            null,
            0L,
            0,
            null,
            nowUtc);
    var toKnownStage0 = stage0Base.changeStatus(VocabularyStatus.KNOWN, clock);
    assertThat(toKnownStage0.reviewStage()).isEqualTo(3);
    assertThat(toKnownStage0.nextReviewAt()).isEqualTo(nowUtc.plusDays(14));

    // manual -> LEARNING: reviewStage = 0, nextReviewAt = nowUtc, lastReviewedAt = null
    var toLearning = toKnown.changeStatus(VocabularyStatus.LEARNING, clock);
    assertThat(toLearning.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(toLearning.reviewStage()).isEqualTo(0);
    assertThat(toLearning.nextReviewAt()).isEqualTo(nowUtc);
    assertThat(toLearning.lastReviewedAt()).isNull();
    assertThat(toLearning.learnedAt()).isNull();

    // manual -> NEW: reviewStage = 0, nextReviewAt = null, lastReviewedAt = null
    var toNew = toLearning.changeStatus(VocabularyStatus.NEW, clock);
    assertThat(toNew.status()).isEqualTo(VocabularyStatus.NEW);
    assertThat(toNew.reviewStage()).isEqualTo(0);
    assertThat(toNew.nextReviewAt()).isNull();
    assertThat(toNew.lastReviewedAt()).isNull();
    assertThat(toNew.learnedAt()).isNull();

    // manual -> IGNORED: reviewStage = 0, nextReviewAt = null, lastReviewedAt = null
    var toIgnored = toNew.changeStatus(VocabularyStatus.IGNORED, clock);
    assertThat(toIgnored.status()).isEqualTo(VocabularyStatus.IGNORED);
    assertThat(toIgnored.reviewStage()).isEqualTo(0);
    assertThat(toIgnored.nextReviewAt()).isNull();
    assertThat(toIgnored.lastReviewedAt()).isNull();
    assertThat(toIgnored.learnedAt()).isNull();
  }

  @Test
  void applyReviewAssessmentForgotResetsStageAndSchedulesNextDay() {
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var item =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.KNOWN,
            firstSeenAt,
            firstSeenAt,
            0L,
            4,
            nowUtc.minusDays(10),
            nowUtc);

    var forgot = item.applyReviewAssessment(ReviewAssessment.FORGOT, clock);

    assertThat(forgot.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(forgot.reviewStage()).isEqualTo(0);
    assertThat(forgot.lastReviewedAt()).isEqualTo(nowUtc);
    assertThat(forgot.nextReviewAt()).isEqualTo(nowUtc.plusDays(1));
    assertThat(forgot.learnedAt()).isNull();
  }

  @Test
  void applyReviewAssessmentStruggledPreservesStageAndSchedulesNextDay() {
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var item =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.KNOWN,
            firstSeenAt,
            firstSeenAt,
            0L,
            3,
            nowUtc.minusDays(5),
            nowUtc);

    var struggled = item.applyReviewAssessment(ReviewAssessment.STRUGGLED, clock);

    assertThat(struggled.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(struggled.reviewStage()).isEqualTo(3);
    assertThat(struggled.lastReviewedAt()).isEqualTo(nowUtc);
    assertThat(struggled.nextReviewAt()).isEqualTo(nowUtc.plusDays(1));
    assertThat(struggled.learnedAt()).isNull();
  }

  @Test
  void applyReviewAssessmentRememberedFollowsSpacedRepetitionLadder() {
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var initial =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            firstSeenAt,
            null,
            0L,
            0,
            null,
            nowUtc);

    // 0 -> 1: +3 days
    var r1 = initial.applyReviewAssessment(ReviewAssessment.REMEMBERED, clock);
    assertThat(r1.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(r1.reviewStage()).isEqualTo(1);
    assertThat(r1.nextReviewAt()).isEqualTo(nowUtc.plusDays(3));
    assertThat(r1.lastReviewedAt()).isEqualTo(nowUtc);
    assertThat(r1.learnedAt()).isEqualTo(nowUtc);

    // 1 -> 2: +7 days
    var r2 = r1.applyReviewAssessment(ReviewAssessment.REMEMBERED, clock);
    assertThat(r2.reviewStage()).isEqualTo(2);
    assertThat(r2.nextReviewAt()).isEqualTo(nowUtc.plusDays(7));
    assertThat(r2.learnedAt()).isEqualTo(nowUtc); // Preserved

    // 2 -> 3: +14 days
    var r3 = r2.applyReviewAssessment(ReviewAssessment.REMEMBERED, clock);
    assertThat(r3.reviewStage()).isEqualTo(3);
    assertThat(r3.nextReviewAt()).isEqualTo(nowUtc.plusDays(14));

    // 3 -> 4: +30 days
    var r4 = r3.applyReviewAssessment(ReviewAssessment.REMEMBERED, clock);
    assertThat(r4.reviewStage()).isEqualTo(4);
    assertThat(r4.nextReviewAt()).isEqualTo(nowUtc.plusDays(30));

    // 4 -> 5: +30 days
    var r5 = r4.applyReviewAssessment(ReviewAssessment.REMEMBERED, clock);
    assertThat(r5.reviewStage()).isEqualTo(5);
    assertThat(r5.nextReviewAt()).isEqualTo(nowUtc.plusDays(30));

    // 5 -> 5: +30 days (capped at 5)
    var r6 = r5.applyReviewAssessment(ReviewAssessment.REMEMBERED, clock);
    assertThat(r6.reviewStage()).isEqualTo(5);
    assertThat(r6.nextReviewAt()).isEqualTo(nowUtc.plusDays(30));
  }
}
