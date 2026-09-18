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
  void manualStatusToKnownUsesSrsV2NotLeitnerStageIntervals() {
    // SRS V2 policy: when the user manually marks a word KNOWN, nextReviewAt is derived from the
    // word's current stability — NOT from the legacy Leitner reviewStage ladder (+14d / +30d).
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

    // Case A: word with no prior SRS history (stability = 0.0 → falls back to default 13.8206d)
    // Use the full constructor with stability=0.0 to simulate a brand-new word without SRS history.
    var noHistory =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            firstSeenAt,
            null,
            0L,
            3,
            null,
            nowUtc,
            SrsState.NEW,
            0.0, // no SRS history — triggers fallback to 13.8206 in changeStatus(KNOWN)
            5.0,
            0,
            0);
    var toKnownNoHistory = noHistory.changeStatus(VocabularyStatus.KNOWN, clock);
    assertThat(toKnownNoHistory.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(toKnownNoHistory.srsState()).isEqualTo(SrsState.REVIEW);
    // stability=0.0 → falls back to 13.8206 → round → 14 days
    assertThat(toKnownNoHistory.nextReviewAt()).isEqualTo(nowUtc.plusDays(14));
    // reviewStage must NOT be mutated by SRS V2 runtime (no +14d/+30d stage arithmetic)
    assertThat(toKnownNoHistory.reviewStage()).isEqualTo(3); // passed through unchanged

    // Case B: word already has SRS history with stability = 5.0d
    var withHistory =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            firstSeenAt,
            null,
            0L,
            0,
            nowUtc.minusDays(1),
            nowUtc,
            SrsState.LEARNING,
            5.0,
            6.5,
            2,
            0);
    var toKnownWithHistory = withHistory.changeStatus(VocabularyStatus.KNOWN, clock);
    assertThat(toKnownWithHistory.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(toKnownWithHistory.srsState()).isEqualTo(SrsState.REVIEW);
    // stability = 5.0 → round → 5 days (NOT +14d or +30d from Leitner)
    assertThat(toKnownWithHistory.nextReviewAt()).isEqualTo(nowUtc.plusDays(5));
    assertThat(toKnownWithHistory.stability()).isEqualTo(5.0);

    // Case C: word already KNOWN — learnedAt preserved, stability preserved, nextReview
    // recalculated
    var alreadyKnown =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.KNOWN,
            firstSeenAt,
            firstSeenAt.plusHours(1),
            0L,
            4,
            nowUtc.minusDays(30),
            nowUtc,
            SrsState.REVIEW,
            30.0,
            4.5,
            5,
            1);
    var toKnownAgain = alreadyKnown.changeStatus(VocabularyStatus.KNOWN, clock);
    assertThat(toKnownAgain.learnedAt()).isEqualTo(firstSeenAt.plusHours(1)); // preserved
    assertThat(toKnownAgain.nextReviewAt()).isEqualTo(nowUtc.plusDays(30)); // stability=30→+30d
    assertThat(toKnownAgain.reviewStage()).isEqualTo(4); // passed through, NOT incremented

    // Case D: manual → LEARNING: reviewStage = 0, SrsState = LEARNING, nextReviewAt = nowUtc
    var toLearning = alreadyKnown.changeStatus(VocabularyStatus.LEARNING, clock);
    assertThat(toLearning.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(toLearning.srsState()).isEqualTo(SrsState.LEARNING);
    assertThat(toLearning.reviewStage()).isEqualTo(0);
    assertThat(toLearning.nextReviewAt()).isEqualTo(nowUtc);
    assertThat(toLearning.lastReviewedAt()).isNull();
    assertThat(toLearning.learnedAt()).isNull();

    // Case E: manual → NEW: reviewStage = 0, no dates
    var toNew = toLearning.changeStatus(VocabularyStatus.NEW, clock);
    assertThat(toNew.status()).isEqualTo(VocabularyStatus.NEW);
    assertThat(toNew.srsState()).isEqualTo(SrsState.NEW);
    assertThat(toNew.reviewStage()).isEqualTo(0);
    assertThat(toNew.nextReviewAt()).isNull();

    // Case F: manual → IGNORED: reviewStage = 0, no dates
    var toIgnored = toNew.changeStatus(VocabularyStatus.IGNORED, clock);
    assertThat(toIgnored.status()).isEqualTo(VocabularyStatus.IGNORED);
    assertThat(toIgnored.srsState()).isEqualTo(SrsState.NEW);
    assertThat(toIgnored.reviewStage()).isEqualTo(0);
    assertThat(toIgnored.nextReviewAt()).isNull();
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

  @Test
  void applyRatingFollowsFsrsV2Policies() {
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var scheduler = new com.soap.soap.domain.service.FsrsScheduler();
    var learning =
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
            nowUtc,
            SrsState.LEARNING,
            0.4872,
            7.6214,
            0,
            0);

    // GOOD on LEARNING -> graduates to REVIEW/KNOWN, +4 days
    var graduated = learning.applyRating(ReviewRating.GOOD, clock, scheduler);
    assertThat(graduated.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(graduated.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(graduated.repetitions()).isEqualTo(1);
    assertThat(graduated.nextReviewAt()).isEqualTo(nowUtc.plusDays(4));
    assertThat(graduated.learnedAt()).isEqualTo(nowUtc);

    // AGAIN on REVIEW -> lapses to RELEARNING/LEARNING, +10 min step, lapses = 1
    var lapsed = graduated.applyRating(ReviewRating.AGAIN, clock, scheduler);
    assertThat(lapsed.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(lapsed.srsState()).isEqualTo(SrsState.RELEARNING);
    assertThat(lapsed.lapses()).isEqualTo(1);
    assertThat(lapsed.nextReviewAt()).isEqualTo(nowUtc.plusSeconds(600L));
    assertThat(lapsed.learnedAt()).isNull();
  }
}
