package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.model.VocabularyReviewItem;
import com.soap.soap.application.model.VocabularyReviewPreparation;
import com.soap.soap.application.port.in.PrepareVocabularyReviewPort;
import com.soap.soap.application.port.in.RecordVocabularyReviewPort;
import com.soap.soap.domain.model.ReviewAssessment;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import com.soap.soap.infrastructure.soap.generated.PrepareVocabularyReviewRequest;
import com.soap.soap.infrastructure.soap.generated.RecordVocabularyReviewRequest;
import com.soap.soap.infrastructure.soap.generated.ReviewAssessmentType;
import com.soap.soap.infrastructure.soap.generated.ReviewResultType;
import com.soap.soap.infrastructure.soap.generated.VocabularyStatusType;
import com.soap.soap.infrastructure.soap.mapper.VocabularyReviewSoapMapper;
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
  void mapsPrepareReviewRequestAndReturnsMinimalResponse() {
    var request = new PrepareVocabularyReviewRequest();
    request.setSize(20);

    var wordId = UUID.randomUUID();
    var preparation =
        new VocabularyReviewPreparation(
            5L,
            12L,
            List.of(new VocabularyReviewItem(wordId, "would", "en", VocabularyStatus.LEARNING)));

    when(preparePort.prepareReview(20)).thenReturn(preparation);

    var response = endpoint.prepareVocabularyReview(request);

    assertThat(response.getDueCount()).isEqualTo(5L);
    assertThat(response.getTotalReviewableCount()).isEqualTo(12L);
    assertThat(response.getEntries()).hasSize(1);
    var item = response.getEntries().getFirst();
    assertThat(item.getWordId()).isEqualTo(wordId.toString());
    assertThat(item.getWord()).isEqualTo("would");
    assertThat(item.getLanguage()).isEqualTo("en");
    assertThat(item.getStatus()).isEqualTo(VocabularyStatusType.LEARNING);

    // Verify internal fields are NOT exposed on reviewItemType
    assertThat(item.getClass().getMethods())
        .noneMatch(m -> m.getName().equalsIgnoreCase("getReviewStage"))
        .noneMatch(m -> m.getName().equalsIgnoreCase("getNextReviewAt"))
        .noneMatch(m -> m.getName().equalsIgnoreCase("getUserId"));

    verify(preparePort).prepareReview(20);
  }

  @Test
  void mapsRecordReviewRequestAndReturnsMinimalReviewResultType() {
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
            now.plusDays(3));

    when(recordPort.recordReview(wordId, ReviewAssessment.REMEMBERED)).thenReturn(updated);

    var response = endpoint.recordVocabularyReview(request);

    assertThat(response.getEntry()).isNotNull();
    assertThat(response.getEntry().getWordId()).isEqualTo(wordId.toString());
    assertThat(response.getEntry().getStatus()).isEqualTo(VocabularyStatusType.KNOWN);

    // Verify ReviewResultType encapsulates scheduling and exposes ONLY wordId and status
    assertThat(ReviewResultType.class.getDeclaredFields())
        .extracting("name")
        .containsExactlyInAnyOrder("wordId", "status");

    verify(recordPort).recordReview(wordId, ReviewAssessment.REMEMBERED);
  }
}
