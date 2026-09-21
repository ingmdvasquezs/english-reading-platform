package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.model.BrowsePlatformReadingsQuery;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.port.in.BrowsePlatformReadingsPort;
import com.soap.soap.domain.model.EditorialCategory;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.RecommendationReasonCode;
import com.soap.soap.infrastructure.soap.generated.BrowsePlatformReadingsRequest;
import com.soap.soap.infrastructure.soap.generated.EditorialLevelType;
import com.soap.soap.infrastructure.soap.generated.ReadingProgressStatusType;
import com.soap.soap.infrastructure.soap.generated.RecommendationReasonCodeType;
import com.soap.soap.infrastructure.soap.mapper.BrowsePlatformReadingsSoapMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BrowsePlatformReadingsEndpointTest {

  @Mock private BrowsePlatformReadingsPort port;
  private final BrowsePlatformReadingsSoapMapper mapper = new BrowsePlatformReadingsSoapMapper();
  private BrowsePlatformReadingsEndpoint endpoint;

  @BeforeEach
  void setUp() {
    endpoint = new BrowsePlatformReadingsEndpoint(port, mapper);
  }

  @Test
  void mapsRequestWithEnumCategoryAndLevelCorrectly() {
    var request = new BrowsePlatformReadingsRequest();
    request.setCategory("CULTURE_ARTS_AND_FICTION");
    request.setEditorialLevel(EditorialLevelType.B_1);
    request.setCollectionKey("colombian-myths-legends");
    request.setPage(0);
    request.setSize(10);

    when(port.browsePlatformReadings(any())).thenReturn(new PageResult<>(List.of(), 0, 10, 0));

    endpoint.browsePlatformReadings(request);

    var captor = ArgumentCaptor.forClass(BrowsePlatformReadingsQuery.class);
    verify(port).browsePlatformReadings(captor.capture());

    var query = captor.getValue();
    assertThat(query.category()).isEqualTo(EditorialCategory.CULTURE_ARTS_AND_FICTION);
    assertThat(query.editorialLevel()).isEqualTo(EditorialLevel.B1);
    assertThat(query.collectionKey()).isEqualTo("colombian-myths-legends");
    assertThat(query.pageRequest().page()).isEqualTo(0);
    assertThat(query.pageRequest().size()).isEqualTo(10);
  }

  @Test
  void mapsRequestWithDisplayNameCategoryCorrectly() {
    var request = new BrowsePlatformReadingsRequest();
    request.setCategory("Culture, Arts & Fiction");
    request.setPage(1);
    request.setSize(5);

    when(port.browsePlatformReadings(any())).thenReturn(new PageResult<>(List.of(), 1, 5, 0));

    endpoint.browsePlatformReadings(request);

    var captor = ArgumentCaptor.forClass(BrowsePlatformReadingsQuery.class);
    verify(port).browsePlatformReadings(captor.capture());

    var query = captor.getValue();
    assertThat(query.category()).isEqualTo(EditorialCategory.CULTURE_ARTS_AND_FICTION);
    assertThat(query.editorialLevel()).isNull();
    assertThat(query.collectionKey()).isNull();
    assertThat(query.pageRequest().page()).isEqualTo(1);
    assertThat(query.pageRequest().size()).isEqualTo(5);
  }

  @Test
  void throwsClientFaultWhenCategoryIsInvalid() {
    var request = new BrowsePlatformReadingsRequest();
    request.setCategory("NON_EXISTENT_CATEGORY");
    request.setPage(0);
    request.setSize(10);

    assertThatThrownBy(() -> endpoint.browsePlatformReadings(request))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("Unknown editorial category");
  }

  @Test
  void mapsResponseCardsAndPageMetadataAccurately() {
    var request = new BrowsePlatformReadingsRequest();
    request.setPage(0);
    request.setSize(10);

    var readingId = UUID.randomUUID();
    var card =
        new RecommendedPlatformReading(
            readingId,
            "El Mohan",
            "en",
            EditorialLevel.B1,
            "Culture, Arts & Fiction",
            LocalDateTime.of(2026, 3, 10, 12, 0),
            120,
            90,
            20,
            10,
            0,
            0,
            new BigDecimal("82.50"),
            new BigDecimal("96.00"),
            ReadingProgressStatus.IN_PROGRESS,
            "colombia-mohan",
            RecommendationReasonCode.BALANCED_CHALLENGE,
            "A legendary river creature.");

    when(port.browsePlatformReadings(any())).thenReturn(new PageResult<>(List.of(card), 0, 10, 1));

    var response = endpoint.browsePlatformReadings(request);

    assertThat(response.getPage()).isEqualTo(0);
    assertThat(response.getSize()).isEqualTo(10);
    assertThat(response.getTotalElements()).isEqualTo(1);

    assertThat(response.getReadings())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.getReadingId()).isEqualTo(readingId.toString());
              assertThat(item.getTitle()).isEqualTo("El Mohan");
              assertThat(item.getLanguage()).isEqualTo("en");
              assertThat(item.getEditorialLevel()).isEqualTo(EditorialLevelType.B_1);
              assertThat(item.getCategory()).isEqualTo("Culture, Arts & Fiction");
              assertThat(item.getVocabularyFitPercentage()).isEqualTo(new BigDecimal("82.50"));
              assertThat(item.getClassificationConfidencePercentage())
                  .isEqualTo(new BigDecimal("96.00"));
              assertThat(item.getProgressStatus()).isEqualTo(ReadingProgressStatusType.IN_PROGRESS);
              assertThat(item.getCoverKey()).isEqualTo("colombia-mohan");
              assertThat(item.getReasonCode())
                  .isEqualTo(RecommendationReasonCodeType.BALANCED_CHALLENGE);
              assertThat(item.getShortDescription()).isEqualTo("A legendary river creature.");
            });
  }

  @Test
  void mapsRequestWithSortCreatedAtDescCorrectly() {
    var request = new BrowsePlatformReadingsRequest();
    request.setSort(
        com.soap.soap.infrastructure.soap.generated.PlatformReadingSortType.CREATED_AT_DESC);
    request.setPage(0);
    request.setSize(10);

    when(port.browsePlatformReadings(any())).thenReturn(new PageResult<>(List.of(), 0, 10, 0));

    endpoint.browsePlatformReadings(request);

    var captor = ArgumentCaptor.forClass(BrowsePlatformReadingsQuery.class);
    verify(port).browsePlatformReadings(captor.capture());

    var query = captor.getValue();
    assertThat(query.sort())
        .isEqualTo(com.soap.soap.domain.model.PlatformReadingSort.CREATED_AT_DESC);
  }

  @Test
  void mapsRequestWithSortDefaultCorrectly() {
    var request = new BrowsePlatformReadingsRequest();
    request.setSort(com.soap.soap.infrastructure.soap.generated.PlatformReadingSortType.DEFAULT);
    request.setPage(0);
    request.setSize(10);

    when(port.browsePlatformReadings(any())).thenReturn(new PageResult<>(List.of(), 0, 10, 0));

    endpoint.browsePlatformReadings(request);

    var captor = ArgumentCaptor.forClass(BrowsePlatformReadingsQuery.class);
    verify(port).browsePlatformReadings(captor.capture());

    var query = captor.getValue();
    assertThat(query.sort()).isEqualTo(com.soap.soap.domain.model.PlatformReadingSort.DEFAULT);
  }
}
