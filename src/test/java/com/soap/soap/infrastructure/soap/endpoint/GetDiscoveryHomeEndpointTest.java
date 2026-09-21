package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.model.ContinueReadingItem;
import com.soap.soap.application.model.DiscoveryCountrySummary;
import com.soap.soap.application.model.DiscoveryHeroImageSummary;
import com.soap.soap.application.model.DiscoveryHomeResult;
import com.soap.soap.application.model.DiscoveryRegionDetails;
import com.soap.soap.application.model.DiscoveryShelfResult;
import com.soap.soap.application.model.DiscoveryTopicSummary;
import com.soap.soap.application.model.GetDiscoveryHomeQuery;
import com.soap.soap.application.model.LatinAmericaDiscoveryResult;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.port.in.GetDiscoveryHomePort;
import com.soap.soap.domain.model.DiscoveryTopic;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.RecommendationReasonCode;
import com.soap.soap.infrastructure.soap.generated.GetDiscoveryHomeRequest;
import com.soap.soap.infrastructure.soap.generated.GetDiscoveryHomeResponse;
import com.soap.soap.infrastructure.soap.mapper.GetDiscoveryHomeSoapMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetDiscoveryHomeEndpointTest {

  @Mock private GetDiscoveryHomePort port;
  private final GetDiscoveryHomeSoapMapper mapper = new GetDiscoveryHomeSoapMapper();
  private GetDiscoveryHomeEndpoint endpoint;

  @BeforeEach
  void setUp() {
    endpoint = new GetDiscoveryHomeEndpoint(port, mapper);
  }

  @Test
  @DisplayName(
      "Maps GetDiscoveryHomeRequest to Query, calls port, and maps DiscoveryHomeResult to Response")
  void mapsRequestAndResponseCorrectly() {
    var request = new GetDiscoveryHomeRequest();
    request.setMaxContinueReading(5);
    request.setMaxForYou(6);
    request.setMaxShelfReadings(7);

    var continueItem =
        new ContinueReadingItem(
            UUID.randomUUID(),
            "Reading In Progress",
            ReadingOrigin.PLATFORM,
            ReadingProgressStatus.IN_PROGRESS,
            "cover.webp",
            EditorialLevel.B1,
            "Fiction",
            LocalDateTime.of(2026, 9, 20, 10, 0),
            "Short desc",
            45);

    var forYouItem =
        new RecommendedPlatformReading(
            UUID.randomUUID(),
            "For You Title",
            "en",
            EditorialLevel.B2,
            "Adventure",
            LocalDateTime.of(2026, 9, 20, 9, 0),
            120,
            100,
            15,
            5,
            0,
            5,
            BigDecimal.valueOf(90),
            BigDecimal.valueOf(85),
            ReadingProgressStatus.IN_PROGRESS,
            "cover_fy.webp",
            RecommendationReasonCode.DISCOVERY,
            "For you desc",
            "CO",
            DiscoveryTopic.MYTHS_AND_LEGENDS);

    var region = new DiscoveryRegionDetails("latin-america", "Latinoamérica", "Región");
    var hero = new DiscoveryHeroImageSummary("hero.webp", "Bogota", "Alt text", 1);
    var topic = new DiscoveryTopicSummary("MYTHS_AND_LEGENDS", "Mitos", 1, 10);
    var country =
        new DiscoveryCountrySummary(
            "CO", "Colombia", "Tag", "Desc", 1, 10, List.of(hero), List.of(topic));

    var latamReading =
        new RecommendedPlatformReading(
            UUID.randomUUID(),
            "El Mohan",
            "en",
            EditorialLevel.A2,
            "Myths",
            LocalDateTime.of(2026, 9, 20, 8, 0),
            80,
            60,
            10,
            5,
            0,
            5,
            BigDecimal.valueOf(80),
            BigDecimal.valueOf(75),
            ReadingProgressStatus.IN_PROGRESS,
            "cover_mohan.webp",
            RecommendationReasonCode.DISCOVERY,
            "Mohan desc",
            "CO",
            DiscoveryTopic.MYTHS_AND_LEGENDS);

    var latinAmerica =
        new LatinAmericaDiscoveryResult(
            region, List.of(country), "CO", "MYTHS_AND_LEGENDS", List.of(latamReading));

    var shelf =
        new DiscoveryShelfResult(
            "classics",
            "Classics",
            "Classic stories",
            1,
            "cover_classics.webp",
            "EDITORIAL",
            1,
            List.of(latamReading));

    var homeResult =
        new DiscoveryHomeResult(
            List.of(continueItem), List.of(forYouItem), latinAmerica, List.of(shelf));

    when(port.getDiscoveryHome(any())).thenReturn(homeResult);

    GetDiscoveryHomeResponse response = endpoint.getDiscoveryHome(request);

    ArgumentCaptor<GetDiscoveryHomeQuery> queryCaptor =
        ArgumentCaptor.forClass(GetDiscoveryHomeQuery.class);
    verify(port).getDiscoveryHome(queryCaptor.capture());

    assertThat(queryCaptor.getValue().maxContinueReading()).isEqualTo(5);
    assertThat(queryCaptor.getValue().maxForYou()).isEqualTo(6);
    assertThat(queryCaptor.getValue().maxShelfReadings()).isEqualTo(7);

    assertThat(response.getContinueReading()).hasSize(1);
    assertThat(response.getContinueReading().get(0).getTitle()).isEqualTo("Reading In Progress");

    assertThat(response.getForYou()).hasSize(1);
    assertThat(response.getForYou().get(0).getTitle()).isEqualTo("For You Title");

    assertThat(response.getLatinAmerica()).isNotNull();
    assertThat(response.getLatinAmerica().getRegion().getKey()).isEqualTo("latin-america");
    assertThat(response.getLatinAmerica().getDefaultCountryCode()).isEqualTo("CO");
    assertThat(response.getLatinAmerica().getDefaultTopicKey()).isEqualTo("MYTHS_AND_LEGENDS");
    assertThat(response.getLatinAmerica().getCountries()).hasSize(1);
    assertThat(response.getLatinAmerica().getCountries().get(0).getTopics()).hasSize(1);
    assertThat(response.getLatinAmerica().getReadings()).hasSize(1);
    assertThat(response.getLatinAmerica().getReadings().get(0).getTitle()).isEqualTo("El Mohan");

    assertThat(response.getShelves()).hasSize(1);
    assertThat(response.getShelves().get(0).getKey()).isEqualTo("classics");
    assertThat(response.getShelves().get(0).getType()).isEqualTo("EDITORIAL");
    assertThat(response.getShelves().get(0).getReadings()).hasSize(1);
  }
}
