package com.soap.soap.infrastructure.soap.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.ReviewRatingOption;
import com.soap.soap.application.model.VocabularyReviewItem;
import com.soap.soap.application.model.VocabularyReviewPreparation;
import com.soap.soap.application.model.VocabularyReviewRecordResult;
import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.model.SrsState;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VocabularyReviewSoapMapperTest {
  private final VocabularyReviewSoapMapper mapper = new VocabularyReviewSoapMapper();

  @Test
  @DisplayName("14.3.5 - Prove 310-minute bug and verify explicit UTC 'Z' in SOAP wire timestamps")
  void testProve310MinuteBugAndExplicitUtcWireFormat() {
    // 1. Capture base timestamps
    var nowInstant = Instant.parse("2026-09-19T06:44:00Z");
    var nowUtc = LocalDateTime.ofInstant(nowInstant, ZoneOffset.UTC);

    // Again interval: 10 minutes (600 seconds)
    var againNextReviewUtc = nowUtc.plusMinutes(10);
    long againIntervalSeconds = 600L;

    // Hard interval: 15 minutes (900 seconds)
    var hardNextReviewUtc = nowUtc.plusMinutes(15);
    long hardIntervalSeconds = 900L;

    var wordId = UUID.randomUUID();
    var user = new User(UUID.randomUUID(), "Ada", "ada@example.com");
    var word = new Word(wordId, "targetWord", "en");
    var vocab =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(1),
            null,
            0L,
            1,
            nowUtc,
            againNextReviewUtc,
            SrsState.RELEARNING,
            0.5,
            7.0,
            1,
            1);

    var ratingOptions =
        List.of(
            new ReviewRatingOption(ReviewRating.AGAIN, againNextReviewUtc, againIntervalSeconds),
            new ReviewRatingOption(ReviewRating.HARD, hardNextReviewUtc, hardIntervalSeconds),
            new ReviewRatingOption(ReviewRating.GOOD, nowUtc.plusDays(1), 86400L),
            new ReviewRatingOption(ReviewRating.EASY, nowUtc.plusDays(2), 2 * 86400L));

    var recordResult = new VocabularyReviewRecordResult(vocab, ratingOptions);

    // 2. Map to SOAP response
    var response = mapper.toResponse(recordResult);

    // 3. Verify wire XML strings contain explicit 'Z'
    var entry = response.getEntry();
    assertThat(entry.getNextReviewAt().toXMLFormat()).endsWith("Z");
    assertThat(entry.getNextReviewAt().toXMLFormat()).isEqualTo("2026-09-19T06:54:00Z");

    var soapAgainOpt =
        entry.getRatingOptions().stream()
            .filter(o -> o.getRating().value().equals("AGAIN"))
            .findFirst()
            .orElseThrow();
    assertThat(soapAgainOpt.getNextReviewAt().toXMLFormat()).endsWith("Z");
    assertThat(soapAgainOpt.getNextReviewAt().toXMLFormat()).isEqualTo("2026-09-19T06:54:00Z");
    assertThat(soapAgainOpt.getIntervalSeconds()).isEqualTo(600L);

    var soapHardOpt =
        entry.getRatingOptions().stream()
            .filter(o -> o.getRating().value().equals("HARD"))
            .findFirst()
            .orElseThrow();
    assertThat(soapHardOpt.getNextReviewAt().toXMLFormat()).endsWith("Z");
    assertThat(soapHardOpt.getNextReviewAt().toXMLFormat()).isEqualTo("2026-09-19T06:59:00Z");
    assertThat(soapHardOpt.getIntervalSeconds()).isEqualTo(900L);

    // 4. Prove why timezone-less XML caused the 310-minute bug:
    // Without 'Z', the string was "2026-09-19T06:54:00".
    // When a browser in America/Bogota (UTC-5) parsed "2026-09-19T06:54:00" without timezone,
    // JS interpreted it as 06:54:00 local time (-05:00), which is 11:54:00 UTC!
    ZoneId bogotaZone = ZoneId.of("America/Bogota");
    ZonedDateTime bogotaParsedWithoutZ =
        LocalDateTime.parse("2026-09-19T06:54:00").atZone(bogotaZone);
    Instant parsedWithoutZInstant = bogotaParsedWithoutZ.toInstant();
    long faultyDurationMinutes = Duration.between(nowInstant, parsedWithoutZInstant).toMinutes();

    // 5 hours (300m) + 10m = 310 minutes!
    assertThat(faultyDurationMinutes).isEqualTo(310L);

    // 5. With explicit 'Z', parsing preserves the exact UTC instant:
    Instant parsedWithZInstant = Instant.parse(soapAgainOpt.getNextReviewAt().toXMLFormat());
    long correctedDurationMinutes = Duration.between(nowInstant, parsedWithZInstant).toMinutes();
    assertThat(correctedDurationMinutes).isEqualTo(10L);
  }

  @Test
  @DisplayName(
      "14.3.5 - Prepare response ratingOptions and learnAheadEntries have explicit UTC 'Z'")
  void testPrepareResponseRatingOptionsHaveExplicitUtc() {
    var nowUtc = LocalDateTime.parse("2026-09-19T08:00:00");
    var wordId = UUID.randomUUID();
    var ratingOptions =
        List.of(
            new ReviewRatingOption(ReviewRating.AGAIN, nowUtc.plusMinutes(10), 600L),
            new ReviewRatingOption(ReviewRating.HARD, nowUtc.plusMinutes(15), 900L));

    var item =
        new VocabularyReviewItem(
            wordId, "example", "en", VocabularyStatus.LEARNING, SrsState.LEARNING, ratingOptions);

    var prep =
        new VocabularyReviewPreparation(1L, 10L, 15, 0, 1, 1, false, List.of(item), List.of(item));

    var response = mapper.toResponse(prep);

    var entryOpt = response.getEntries().get(0).getRatingOptions().get(0);
    assertThat(entryOpt.getNextReviewAt().toXMLFormat()).isEqualTo("2026-09-19T08:10:00Z");

    var learnAheadOpt = response.getLearnAheadEntries().get(0).getRatingOptions().get(0);
    assertThat(learnAheadOpt.getNextReviewAt().toXMLFormat()).isEqualTo("2026-09-19T08:10:00Z");
  }

  @Test
  @DisplayName(
      "14.3.5.2 - Expose pendingQueueSequence and baseOrder in SOAP prepare and record responses")
  void testExposePendingQueueSequenceAndBaseOrderInSoapTypes() {
    var nowUtc = LocalDateTime.parse("2026-09-19T12:00:00");
    var wordId1 = UUID.randomUUID();
    var wordId2 = UUID.randomUUID();

    var entryItem =
        new VocabularyReviewItem(
            wordId1, "cardBase", "en", VocabularyStatus.LEARNING, SrsState.NEW, List.of(), null, 1);
    var learnAheadItem =
        new VocabularyReviewItem(
            wordId2,
            "cardLearning",
            "en",
            VocabularyStatus.LEARNING,
            SrsState.LEARNING,
            List.of(),
            2L,
            2);

    var prep =
        new VocabularyReviewPreparation(
            1L, 10L, 15, 0, 15, 1, false, List.of(entryItem), List.of(learnAheadItem));

    var prepResponse = mapper.toResponse(prep);

    var soapEntry = prepResponse.getEntries().get(0);
    assertThat(soapEntry.getPendingQueueSequence()).isNull();
    assertThat(soapEntry.getBaseOrder()).isEqualTo(1);

    var soapLearnAhead = prepResponse.getLearnAheadEntries().get(0);
    assertThat(soapLearnAhead.getPendingQueueSequence()).isEqualTo(2L);
    assertThat(soapLearnAhead.getBaseOrder()).isEqualTo(2);

    // Record review result mapping
    var user = new User(UUID.randomUUID(), "Ada", "ada@example.com");
    var word = new Word(wordId2, "cardLearning", "en");
    var vocab =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(1),
            null,
            600L,
            1,
            nowUtc,
            nowUtc.plusMinutes(10),
            SrsState.LEARNING,
            0.5,
            7.0,
            1,
            0);

    var recordResult = new VocabularyReviewRecordResult(vocab, List.of(), 4L, 2);
    var recordResponse = mapper.toResponse(recordResult);

    assertThat(recordResponse.getEntry().getPendingQueueSequence()).isEqualTo(4L);
    assertThat(recordResponse.getEntry().getBaseOrder()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "14.3.5.2 - Frontend can merge entries + learnAheadEntries and sort by pendingQueueSequence ASC (Section 11 B)")
  void testFrontendCanMergeAndSortByPendingQueueSequenceAscPreservingFifo() {
    var wordB = UUID.randomUUID();
    var wordC = UUID.randomUUID();

    // Card B: seq=2, learn-ahead (due 12:15)
    var itemB =
        new VocabularyReviewItem(
            wordB, "wordB", "en", VocabularyStatus.LEARNING, SrsState.LEARNING, List.of(), 2L, 2);

    // Card C: seq=3, due (due 12:10)
    var itemC =
        new VocabularyReviewItem(
            wordC, "wordC", "en", VocabularyStatus.LEARNING, SrsState.LEARNING, List.of(), 3L, 3);

    var prep =
        new VocabularyReviewPreparation(1L, 2L, 15, 2, 0, 2, false, List.of(itemC), List.of(itemB));

    var response = mapper.toResponse(prep);

    // Backend returned C in entries (due) and B in learnAheadEntries (not due yet)
    assertThat(response.getEntries()).hasSize(1);
    assertThat(response.getEntries().get(0).getWord()).isEqualTo("wordC");
    assertThat(response.getEntries().get(0).getPendingQueueSequence()).isEqualTo(3L);

    assertThat(response.getLearnAheadEntries()).hasSize(1);
    assertThat(response.getLearnAheadEntries().get(0).getWord()).isEqualTo("wordB");
    assertThat(response.getLearnAheadEntries().get(0).getPendingQueueSequence()).isEqualTo(2L);

    // Frontend merges and sorts by pendingQueueSequence ASC
    var merged =
        new java.util.ArrayList<com.soap.soap.infrastructure.soap.generated.ReviewItemType>();
    merged.addAll(response.getEntries());
    merged.addAll(response.getLearnAheadEntries());
    merged.sort(
        java.util.Comparator.comparing(
            com.soap.soap.infrastructure.soap.generated.ReviewItemType::getPendingQueueSequence));

    assertThat(merged).hasSize(2);
    assertThat(merged.get(0).getWord()).isEqualTo("wordB");
    assertThat(merged.get(0).getPendingQueueSequence()).isEqualTo(2L);
    assertThat(merged.get(1).getWord()).isEqualTo("wordC");
    assertThat(merged.get(1).getPendingQueueSequence()).isEqualTo(3L);
  }
}
