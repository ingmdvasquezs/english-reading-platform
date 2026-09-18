package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.model.DiscoveryCountrySummary;
import com.soap.soap.application.model.DiscoveryHeroImageSummary;
import com.soap.soap.application.model.DiscoveryRegionDetails;
import com.soap.soap.application.model.DiscoveryRegionOverviewResult;
import com.soap.soap.application.model.DiscoveryTopicSummary;
import com.soap.soap.application.port.in.GetDiscoveryRegionOverviewPort;
import com.soap.soap.infrastructure.soap.generated.GetDiscoveryRegionOverviewRequest;
import com.soap.soap.infrastructure.soap.mapper.GetDiscoveryRegionOverviewSoapMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetDiscoveryRegionOverviewEndpointTest {

  @Mock private GetDiscoveryRegionOverviewPort port;
  private final GetDiscoveryRegionOverviewSoapMapper mapper =
      new GetDiscoveryRegionOverviewSoapMapper();
  private GetDiscoveryRegionOverviewEndpoint endpoint;

  @BeforeEach
  void setUp() {
    endpoint = new GetDiscoveryRegionOverviewEndpoint(port, mapper);
  }

  @Test
  void mapsRequestAndResponseCorrectly() {
    var request = new GetDiscoveryRegionOverviewRequest();
    request.setRegionKey("latin-america");

    var heroImage =
        new DiscoveryHeroImageSummary(
            "editorial/heroes/colombia/hero-colombia-villa-de-leyva.webp",
            "Villa de Leyva, Boyacá",
            "Villa de Leyva, Boyacá",
            1);

    var topic = new DiscoveryTopicSummary("MYTHS_AND_LEGENDS", "Mitos y leyendas", 1, 15);

    var country =
        new DiscoveryCountrySummary(
            "CO",
            "Colombia",
            "Tagline CO",
            "Description CO",
            1,
            15,
            List.of(heroImage),
            List.of(topic));

    var overviewResult =
        new DiscoveryRegionOverviewResult(
            new DiscoveryRegionDetails("latin-america", "Latinoamérica", "Subtitle"),
            List.of(country));

    when(port.getOverview("latin-america")).thenReturn(overviewResult);

    var response = endpoint.getDiscoveryRegionOverview(request);

    verify(port).getOverview("latin-america");
    assertThat(response.getRegion().getKey()).isEqualTo("latin-america");
    assertThat(response.getRegion().getDisplayName()).isEqualTo("Latinoamérica");
    assertThat(response.getRegion().getSubtitle()).isEqualTo("Subtitle");

    assertThat(response.getCountries()).hasSize(1);
    var countryType = response.getCountries().getFirst();
    assertThat(countryType.getCountryCode()).isEqualTo("CO");
    assertThat(countryType.getDisplayName()).isEqualTo("Colombia");
    assertThat(countryType.getReadingCount()).isEqualTo(15);

    assertThat(countryType.getHeroImages()).hasSize(1);
    assertThat(countryType.getHeroImages().getFirst().getAssetKey())
        .isEqualTo("editorial/heroes/colombia/hero-colombia-villa-de-leyva.webp");

    assertThat(countryType.getTopics()).hasSize(1);
    assertThat(countryType.getTopics().getFirst().getKey()).isEqualTo("MYTHS_AND_LEGENDS");
    assertThat(countryType.getTopics().getFirst().getDisplayName()).isEqualTo("Mitos y leyendas");
    assertThat(countryType.getTopics().getFirst().getReadingCount()).isEqualTo(15);
  }
}
