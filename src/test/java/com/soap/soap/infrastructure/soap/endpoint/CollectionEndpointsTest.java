package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.port.in.ListCollectionReadingsPort;
import com.soap.soap.application.port.in.ListCollectionsPort;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.ReadingCollection;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.infrastructure.soap.generated.ListCollectionReadingsRequest;
import com.soap.soap.infrastructure.soap.generated.ListCollectionsRequest;
import com.soap.soap.infrastructure.soap.mapper.CollectionSoapMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CollectionEndpointsTest {
  @Mock private ListCollectionsPort listCollections;
  @Mock private ListCollectionReadingsPort listReadings;
  private final CollectionSoapMapper mapper = new CollectionSoapMapper();

  @Test
  void mapsCollectionIncludingNullableCoverKey() {
    var collection =
        new ReadingCollection(
            UUID.randomUUID(), "nature", "Nature", "Natural worlds", 4, true, null);
    when(listCollections.listCollections()).thenReturn(List.of(collection));

    var response =
        new ListCollectionsEndpoint(listCollections, mapper)
            .listCollections(new ListCollectionsRequest());

    assertThat(response.getCollections())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.getKey()).isEqualTo("nature");
              assertThat(item.getDisplayOrder()).isEqualTo(4);
              assertThat(item.getCoverKey()).isNull();
            });
  }

  @Test
  void mapsPagedReadingCardsWithCoverAndProgress() {
    var request = new ListCollectionReadingsRequest();
    request.setCollectionKey("nature");
    request.setPage(0);
    request.setSize(5);
    var summary =
        new RecommendedPlatformReading(
            UUID.randomUUID(),
            "Trees",
            "en",
            EditorialLevel.A2,
            "Environment",
            LocalDateTime.parse("2026-09-01T08:00:00"),
            10,
            4,
            2,
            1,
            1,
            2,
            new java.math.BigDecimal("63.00"),
            new java.math.BigDecimal("80.00"),
            ReadingProgressStatus.IN_PROGRESS,
            "trees-cover",
            com.soap.soap.domain.model.RecommendationReasonCode.CONTINUE_READING);
    when(listReadings.listCollectionReadings("nature", new PageRequest(0, 5)))
        .thenReturn(new PageResult<>(List.of(summary), 0, 5, 18));

    var response =
        new ListCollectionReadingsEndpoint(listReadings, mapper).listCollectionReadings(request);

    assertThat(response.getTotalElements()).isEqualTo(18);
    assertThat(response.getReadings())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.getReadingId()).isEqualTo(summary.readingId().toString());
              assertThat(item.getCoverKey()).isEqualTo("trees-cover");
              assertThat(item.getProgressStatus().value()).isEqualTo("IN_PROGRESS");
              assertThat(item.getReasonCode().value()).isEqualTo("CONTINUE_READING");
              assertThat(item.getUniqueWords()).isEqualTo(10);
              assertThat(item.getKnownWords()).isEqualTo(4);
              assertThat(item.getLearningWords()).isEqualTo(2);
              assertThat(item.getExplicitNewWords()).isEqualTo(1);
              assertThat(item.getIgnoredWords()).isEqualTo(1);
              assertThat(item.getUnclassifiedWords()).isEqualTo(2);
              assertThat(item.getVocabularyFitPercentage()).isEqualByComparingTo("63.00");
              assertThat(item.getClassificationConfidencePercentage())
                  .isEqualByComparingTo("80.00");
            });
    verify(listReadings).listCollectionReadings("nature", new PageRequest(0, 5));
  }
}
