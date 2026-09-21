package com.soap.soap.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.domain.exception.InvalidVocabularyStateException;
import com.soap.soap.domain.service.FsrsScheduler;
import com.soap.soap.domain.service.VocabularyReviewSchedulingPolicy;
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

    // Case B: word already has SRS history with stability = 5.0d and srsState = REVIEW
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
            nowUtc.plusDays(5),
            SrsState.REVIEW,
            5.0,
            6.5,
            2,
            0);
    var toKnownWithHistory = withHistory.changeStatus(VocabularyStatus.KNOWN, clock);
    assertThat(toKnownWithHistory.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(toKnownWithHistory.srsState()).isEqualTo(SrsState.REVIEW);
    // Preserves existing nextReviewAt = nowUtc.plusDays(5)
    assertThat(toKnownWithHistory.nextReviewAt()).isEqualTo(nowUtc.plusDays(5));
    assertThat(toKnownWithHistory.stability()).isEqualTo(5.0);

    // Case C: word already KNOWN — learnedAt preserved, stability preserved, nextReview preserved
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
            nowUtc.plusDays(30),
            SrsState.REVIEW,
            30.0,
            4.5,
            5,
            1);
    var toKnownAgain = alreadyKnown.changeStatus(VocabularyStatus.KNOWN, clock);
    assertThat(toKnownAgain.learnedAt()).isEqualTo(firstSeenAt.plusHours(1)); // preserved
    assertThat(toKnownAgain.nextReviewAt()).isEqualTo(nowUtc.plusDays(30)); // preserved
    assertThat(toKnownAgain.reviewStage()).isEqualTo(4); // passed through, NOT incremented

    // Case D: manual → LEARNING: preserves SRS memory (SrsState=REVIEW, nextReviewAt, stability)
    var toLearning = alreadyKnown.changeStatus(VocabularyStatus.LEARNING, clock);
    assertThat(toLearning.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(toLearning.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(toLearning.stability()).isEqualTo(30.0);
    assertThat(toLearning.nextReviewAt()).isEqualTo(nowUtc.plusDays(30));
    assertThat(toLearning.lastReviewedAt()).isEqualTo(nowUtc.minusDays(30));
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

    // GOOD on LEARNING -> graduates to REVIEW, +1 day under FASE 14.3.7 binary policy;
    // status=LEARNING & learnedAt=null preserved!
    var graduated = learning.applyRating(ReviewRating.GOOD, clock, scheduler);
    assertThat(graduated.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(graduated.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(graduated.repetitions()).isEqualTo(1);
    assertThat(graduated.nextReviewAt()).isEqualTo(nowUtc.plusDays(1));
    assertThat(graduated.learnedAt()).isNull();

    // AGAIN on REVIEW -> lapses to RELEARNING, +10 min step, lapses = 1; status=LEARNING &
    // learnedAt=null preserved!
    var lapsed = graduated.applyRating(ReviewRating.AGAIN, clock, scheduler);
    assertThat(lapsed.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(lapsed.srsState()).isEqualTo(SrsState.RELEARNING);
    assertThat(lapsed.lapses()).isEqualTo(1);
    assertThat(lapsed.nextReviewAt()).isEqualTo(nowUtc.plusSeconds(600L));
    assertThat(lapsed.learnedAt()).isNull();
  }

  @Test
  void testLearningGoodPreservesLearningStatusAndLearnedAt() {
    var user = new User(UUID.randomUUID(), "User", "user@example.com");
    var word = new Word(UUID.randomUUID(), "comes", "en");
    var nowUtc = LocalDateTime.of(2026, 9, 20, 12, 0, 0);
    var clock = Clock.fixed(nowUtc.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    var scheduler = new VocabularyReviewSchedulingPolicy(new FsrsScheduler());

    var card =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(2),
            null,
            600L,
            1,
            nowUtc.minusMinutes(10),
            nowUtc,
            SrsState.LEARNING,
            0.4872,
            7.6214,
            1,
            0);

    var reviewed = card.applyRating(ReviewRating.GOOD, clock, scheduler);

    assertThat(reviewed.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(reviewed.learnedAt()).isNull();
    assertThat(reviewed.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(reviewed.nextReviewAt()).isEqualTo(nowUtc.plusDays(1));
  }

  @Test
  void testLearningMatureGoodPreservesLearningStatusAndLearnedAtWithDynamicInterval() {
    var user = new User(UUID.randomUUID(), "User", "user@example.com");
    var word = new Word(UUID.randomUUID(), "travel", "en");
    var nowUtc = LocalDateTime.of(2026, 9, 20, 12, 0, 0);
    var clock = Clock.fixed(nowUtc.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    var scheduler = new VocabularyReviewSchedulingPolicy(new FsrsScheduler());

    var matureLearningCard =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(10),
            null,
            86400L * 5,
            5,
            nowUtc.minusDays(5),
            nowUtc,
            SrsState.REVIEW,
            10.0,
            4.0,
            5,
            0);

    var reviewed = matureLearningCard.applyRating(ReviewRating.GOOD, clock, scheduler);

    assertThat(reviewed.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(reviewed.learnedAt()).isNull();
    assertThat(reviewed.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(reviewed.nextReviewAt()).isAfter(nowUtc.plusDays(1));
  }

  @Test
  void testLearningAgainPreservesLearningStatusAndLearnedAt() {
    var user = new User(UUID.randomUUID(), "User", "user@example.com");
    var word = new Word(UUID.randomUUID(), "place", "en");
    var nowUtc = LocalDateTime.of(2026, 9, 20, 12, 0, 0);
    var clock = Clock.fixed(nowUtc.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    var scheduler = new VocabularyReviewSchedulingPolicy(new FsrsScheduler());

    var card =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(1),
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

    var reviewed = card.applyRating(ReviewRating.AGAIN, clock, scheduler);

    assertThat(reviewed.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(reviewed.learnedAt()).isNull();
    assertThat(reviewed.srsState()).isEqualTo(SrsState.LEARNING);
    assertThat(reviewed.nextReviewAt()).isEqualTo(nowUtc.plusMinutes(10));
  }

  @Test
  void testKnownGoodPreservesKnownStatusAndLearnedAt() {
    var user = new User(UUID.randomUUID(), "User", "user@example.com");
    var word = new Word(UUID.randomUUID(), "apple", "en");
    var nowUtc = LocalDateTime.of(2026, 9, 20, 12, 0, 0);
    var clock = Clock.fixed(nowUtc.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    var scheduler = new VocabularyReviewSchedulingPolicy(new FsrsScheduler());
    var learnedTimestamp = nowUtc.minusDays(15);

    var knownCard =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.KNOWN,
            nowUtc.minusDays(20),
            learnedTimestamp,
            86400L * 4,
            4,
            nowUtc.minusDays(4),
            nowUtc,
            SrsState.REVIEW,
            8.0,
            4.5,
            4,
            0);

    var reviewed = knownCard.applyRating(ReviewRating.GOOD, clock, scheduler);

    assertThat(reviewed.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(reviewed.learnedAt()).isEqualTo(learnedTimestamp);
    assertThat(reviewed.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(reviewed.nextReviewAt()).isAfter(nowUtc.plusDays(1));
  }

  @Test
  void testKnownAgainPreservesKnownStatusAndLearnedAt() {
    var user = new User(UUID.randomUUID(), "User", "user@example.com");
    var word = new Word(UUID.randomUUID(), "apple", "en");
    var nowUtc = LocalDateTime.of(2026, 9, 20, 12, 0, 0);
    var clock = Clock.fixed(nowUtc.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    var scheduler = new VocabularyReviewSchedulingPolicy(new FsrsScheduler());
    var learnedTimestamp = nowUtc.minusDays(15);

    var knownCard =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.KNOWN,
            nowUtc.minusDays(20),
            learnedTimestamp,
            86400L * 4,
            4,
            nowUtc.minusDays(4),
            nowUtc,
            SrsState.REVIEW,
            8.0,
            4.5,
            4,
            0);

    var reviewed = knownCard.applyRating(ReviewRating.AGAIN, clock, scheduler);

    assertThat(reviewed.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(reviewed.learnedAt()).isEqualTo(learnedTimestamp);
    assertThat(reviewed.srsState()).isEqualTo(SrsState.RELEARNING);
    assertThat(reviewed.nextReviewAt()).isEqualTo(nowUtc.plusMinutes(10));
    assertThat(reviewed.lapses()).isEqualTo(1);
  }

  @Test
  void testReaderLearningToKnownPreservesSrsMemory() {
    var user = new User(UUID.randomUUID(), "User", "user@example.com");
    var word = new Word(UUID.randomUUID(), "comes", "en");
    var nowUtc = LocalDateTime.of(2026, 9, 20, 12, 0, 0);
    var clock = Clock.fixed(nowUtc.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    var learningCard =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(5),
            null,
            86400L * 2,
            2,
            nowUtc.minusDays(1),
            nowUtc.plusDays(1),
            SrsState.REVIEW,
            6.5,
            4.2,
            3,
            1);

    var updated = learningCard.changeStatus(VocabularyStatus.KNOWN, clock);

    assertThat(updated.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(updated.learnedAt()).isEqualTo(nowUtc);
    // All SRS engine parameters are strictly preserved
    assertThat(updated.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(updated.stability()).isEqualTo(6.5);
    assertThat(updated.difficulty()).isEqualTo(4.2);
    assertThat(updated.repetitions()).isEqualTo(3);
    assertThat(updated.lapses()).isEqualTo(1);
    assertThat(updated.lastReviewedAt()).isEqualTo(nowUtc.minusDays(1));
    assertThat(updated.nextReviewAt()).isEqualTo(nowUtc.plusDays(1));
  }

  @Test
  void testReaderKnownToLearningPreservesSrsMemory() {
    var user = new User(UUID.randomUUID(), "User", "user@example.com");
    var word = new Word(UUID.randomUUID(), "comes", "en");
    var nowUtc = LocalDateTime.of(2026, 9, 20, 12, 0, 0);
    var clock = Clock.fixed(nowUtc.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    var knownCard =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.KNOWN,
            nowUtc.minusDays(10),
            nowUtc.minusDays(5),
            86400L * 3,
            3,
            nowUtc.minusDays(2),
            nowUtc.plusDays(2),
            SrsState.REVIEW,
            9.0,
            3.8,
            4,
            0);

    var updated = knownCard.changeStatus(VocabularyStatus.LEARNING, clock);

    assertThat(updated.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(updated.learnedAt()).isNull();
    // All SRS engine parameters are strictly preserved
    assertThat(updated.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(updated.stability()).isEqualTo(9.0);
    assertThat(updated.difficulty()).isEqualTo(3.8);
    assertThat(updated.repetitions()).isEqualTo(4);
    assertThat(updated.lapses()).isEqualTo(0);
    assertThat(updated.lastReviewedAt()).isEqualTo(nowUtc.minusDays(2));
    assertThat(updated.nextReviewAt()).isEqualTo(nowUtc.plusDays(2));
  }
}
