package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.model.ReviewRatingOption;
import com.soap.soap.application.model.VocabularyReviewItem;
import com.soap.soap.application.model.VocabularyReviewPreparation;
import com.soap.soap.application.model.VocabularyReviewRecordResult;
import com.soap.soap.application.port.in.PrepareVocabularyReviewPort;
import com.soap.soap.application.port.in.RecordVocabularyReviewPort;
import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.model.SrsState;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import com.soap.soap.infrastructure.soap.generated.PrepareVocabularyReviewRequest;
import com.soap.soap.infrastructure.soap.generated.RecordVocabularyReviewRequest;
import com.soap.soap.infrastructure.soap.generated.ReviewAssessmentType;
import com.soap.soap.infrastructure.soap.generated.ReviewRatingType;
import com.soap.soap.infrastructure.soap.generated.SrsStateType;
import com.soap.soap.infrastructure.soap.generated.VocabularyStatusType;
import com.soap.soap.infrastructure.soap.mapper.VocabularyReviewSoapMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VocabularyReviewEndpointTest {
  @Mock private PrepareVocabularyReviewPort preparePort;
  @Mock private RecordVocabularyReviewPort recordPort;

  private VocabularyReviewEndpoint endpoint;

  @BeforeEach
  void setUp() {
    endpoint =
        new VocabularyReviewEndpoint(preparePort, recordPort, new VocabularyReviewSoapMapper());
  }

  @Test
  void mapsPrepareReviewRequestAndReturnsSrsV2Response() {
    var request = new PrepareVocabularyReviewRequest();
    request.setSize(15);

    var wordId = UUID.randomUUID();
    var ratingOptions =
        List.of(
            new ReviewRatingOption(ReviewRating.AGAIN, LocalDateTime.now().plusMinutes(10), 600L),
            new ReviewRatingOption(ReviewRating.GOOD, LocalDateTime.now().plusDays(1), 86400L));
    var preparation =
        new VocabularyReviewPreparation(
            5L,
            12L,
            15,
            0,
            15,
            1,
            false,
            List.of(
                new VocabularyReviewItem(
                    wordId,
                    "would",
                    "en",
                    VocabularyStatus.LEARNING,
                    SrsState.LEARNING,
                    ratingOptions)));

    when(preparePort.prepareReview(15)).thenReturn(preparation);

    var response = endpoint.prepareVocabularyReview(request);

    assertThat(response.getDueCount()).isEqualTo(5L);
    assertThat(response.getTotalReviewableCount()).isEqualTo(12L);
    assertThat(response.getDailyLimit()).isEqualTo(15);
    assertThat(response.getDailyBaseCompleted()).isEqualTo(0);
    assertThat(response.getDailyBaseRemaining()).isEqualTo(15);
    assertThat(response.getPendingLearningCount()).isEqualTo(1);
    assertThat(response.isDailyComplete()).isFalse();
    assertThat(response.getEntries()).hasSize(1);
    var item = response.getEntries().getFirst();
    assertThat(item.getWordId()).isEqualTo(wordId.toString());
    assertThat(item.getWord()).isEqualTo("would");
    assertThat(item.getLanguage()).isEqualTo("en");
    assertThat(item.getStatus()).isEqualTo(VocabularyStatusType.LEARNING);
    assertThat(item.getSrsState()).isEqualTo(SrsStateType.LEARNING);
    assertThat(item.getRatingOptions()).hasSize(2);

    verify(preparePort).prepareReview(15);
  }

  @Test
  void mapsRecordReviewRequestWithRatingAndReturnsReviewResultType() {
    var wordId = UUID.randomUUID();
    var userId = UUID.randomUUID();
    var request = new RecordVocabularyReviewRequest();
    request.setWordId(wordId.toString());
    request.setRating(ReviewRatingType.GOOD);

    var user = new User(userId, "Ada", "ada@example.com");
    var word = new Word(wordId, "would", "en");
    var now = LocalDateTime.now();
    var updated =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.KNOWN,
            now.minusDays(5),
            now,
            1L,
            1,
            now,
            now.plusDays(4),
            SrsState.REVIEW,
            3.7145,
            5.1618,
            2,
            0);

    var ratingOptions =
        List.of(
            new ReviewRatingOption(ReviewRating.AGAIN, now.plusMinutes(10), 600L),
            new ReviewRatingOption(ReviewRating.GOOD, now.plusDays(4), 345600L));

    var recordResult = new VocabularyReviewRecordResult(updated, ratingOptions);
    when(recordPort.recordReview(wordId, ReviewRating.GOOD)).thenReturn(recordResult);

    var response = endpoint.recordVocabularyReview(request);

    assertThat(response.getEntry()).isNotNull();
    assertThat(response.getEntry().getWordId()).isEqualTo(wordId.toString());
    assertThat(response.getEntry().getStatus()).isEqualTo(VocabularyStatusType.KNOWN);
    assertThat(response.getEntry().getSrsState()).isEqualTo(SrsStateType.REVIEW);
    assertThat(response.getEntry().getStability()).isEqualTo(BigDecimal.valueOf(3.7145));
    assertThat(response.getEntry().getDifficulty()).isEqualTo(BigDecimal.valueOf(5.1618));
    assertThat(response.getEntry().getRatingOptions()).hasSize(2);
    assertThat(response.getEntry().getRatingOptions().get(0).getRating())
        .isEqualTo(ReviewRatingType.AGAIN);
    assertThat(response.getEntry().getRatingOptions().get(0).getIntervalSeconds()).isEqualTo(600L);

    verify(recordPort).recordReview(wordId, ReviewRating.GOOD);
  }

  @Test
  void mapsRecordReviewRequestWithLegacyAssessmentFallback() {
    var wordId = UUID.randomUUID();
    var userId = UUID.randomUUID();
    var request = new RecordVocabularyReviewRequest();
    request.setWordId(wordId.toString());
    request.setAssessment(ReviewAssessmentType.REMEMBERED);

    var user = new User(userId, "Ada", "ada@example.com");
    var word = new Word(wordId, "would", "en");
    var now = LocalDateTime.now();
    var updated =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.KNOWN,
            now.minusDays(5),
            now,
            1L,
            1,
            now,
            now.plusDays(4),
            SrsState.REVIEW,
            3.7145,
            5.1618,
            2,
            0);

    when(recordPort.recordReview(wordId, ReviewRating.GOOD))
        .thenReturn(new VocabularyReviewRecordResult(updated, List.of()));

    var response = endpoint.recordVocabularyReview(request);

    assertThat(response.getEntry()).isNotNull();
    assertThat(response.getEntry().getWordId()).isEqualTo(wordId.toString());
    assertThat(response.getEntry().getStatus()).isEqualTo(VocabularyStatusType.KNOWN);

    verify(recordPort).recordReview(wordId, ReviewRating.GOOD);
  }
}
