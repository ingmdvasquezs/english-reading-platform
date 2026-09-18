package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.DiscoveryRegionNotFoundException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.port.out.DiscoveryRegionRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.DiscoveryCountry;
import com.soap.soap.domain.model.DiscoveryHeroImage;
import com.soap.soap.domain.model.DiscoveryRegion;
import com.soap.soap.domain.model.DiscoveryTopic;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetDiscoveryRegionOverviewUseCaseTest {

  @Mock private DiscoveryRegionRepositoryPort discoveryRegions;
  @Mock private ReadingRepositoryPort readings;

  private GetDiscoveryRegionOverviewUseCase useCase;

  private final UUID regionId = UUID.randomUUID();
  private final UUID colombiaId = UUID.randomUUID();
  private final UUID peruId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    useCase = new GetDiscoveryRegionOverviewUseCase(discoveryRegions, readings);
  }

  @Test
  void returnsOverviewWithActiveCountryAndTopicWhenReadingsExist() {
    var region =
        new DiscoveryRegion(
            regionId,
            "latin-america",
            "Latinoamérica",
            "Historias, cultura y lugares de nuestra región.",
            1,
            true);

    var img1 =
        new DiscoveryHeroImage(
            UUID.randomUUID(),
            "editorial/heroes/colombia/hero-colombia-villa-de-leyva.webp",
            "Villa de Leyva, Boyacá",
            "Villa de Leyva, Boyacá",
            1);
    var img2 =
        new DiscoveryHeroImage(
            UUID.randomUUID(),
            "editorial/heroes/colombia/hero-colombia-valle-de-cocora.webp",
            "Valle de Cocora, Quindío",
            "Valle de Cocora, Quindío",
            2);

    var colombia =
        new DiscoveryCountry(
            colombiaId,
            "CO",
            regionId,
            "Colombia",
            "Tagline CO",
            "Description CO",
            1,
            true,
            List.of(img2, img1)); // Unsorted to test sorting

    var peru =
        new DiscoveryCountry(
            peruId, "PE", regionId, "Perú", "Tagline PE", "Description PE", 2, true, List.of());

    when(discoveryRegions.findActiveRegionByKey("latin-america")).thenReturn(Optional.of(region));
    when(discoveryRegions.findActiveCountriesByRegionId(regionId))
        .thenReturn(List.of(colombia, peru));

    // Aggregation mock: CO has 15 readings, PE has 0 (not in map)
    when(readings.countPublishedPlatformReadingsByCountryCodes(List.of("CO", "PE")))
        .thenReturn(Map.of("CO", 15L));

    // Topic aggregation mock: CO has 15 MYTHS_AND_LEGENDS, 0 REAL_STORIES
    when(readings.countPublishedPlatformReadingsByCountryCodesAndTopics(List.of("CO", "PE")))
        .thenReturn(
            Map.of(
                "CO",
                Map.of(DiscoveryTopic.MYTHS_AND_LEGENDS, 15L, DiscoveryTopic.REAL_STORIES, 0L)));

    var result = useCase.getOverview("latin-america");

    assertThat(result.region().key()).isEqualTo("latin-america");
    assertThat(result.region().displayName()).isEqualTo("Latinoamérica");
    assertThat(result.region().subtitle())
        .isEqualTo("Historias, cultura y lugares de nuestra región.");

    // Only CO returned because PE has 0 readings
    assertThat(result.countries()).hasSize(1);
    var coResult = result.countries().getFirst();
    assertThat(coResult.countryCode()).isEqualTo("CO");
    assertThat(coResult.displayName()).isEqualTo("Colombia");
    assertThat(coResult.readingCount()).isEqualTo(15);

    // Hero images sorted by displayOrder
    assertThat(coResult.heroImages()).hasSize(2);
    assertThat(coResult.heroImages().get(0).displayOrder()).isEqualTo(1);
    assertThat(coResult.heroImages().get(0).location()).isEqualTo("Villa de Leyva, Boyacá");
    assertThat(coResult.heroImages().get(1).displayOrder()).isEqualTo(2);

    // Only MYTHS_AND_LEGENDS returned because REAL_STORIES has 0 readings
    assertThat(coResult.topics()).hasSize(1);
    var topicResult = coResult.topics().getFirst();
    assertThat(topicResult.key()).isEqualTo("MYTHS_AND_LEGENDS");
    assertThat(topicResult.displayName()).isEqualTo("Mitos y leyendas");
    assertThat(topicResult.displayOrder()).isEqualTo(1);
    assertThat(topicResult.readingCount()).isEqualTo(15);
  }

  @Test
  void throwsExceptionWhenRegionNotFoundOrInactive() {
    when(discoveryRegions.findActiveRegionByKey("unknown")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.getOverview("unknown"))
        .isInstanceOf(DiscoveryRegionNotFoundException.class)
        .hasMessageContaining("unknown");

    verifyNoInteractions(readings);
  }

  @Test
  void throwsExceptionWhenRegionKeyIsBlank() {
    assertThatThrownBy(() -> useCase.getOverview("  "))
        .isInstanceOf(InvalidApplicationArgumentException.class);
  }

  @Test
  void returnsEmptyCountriesWhenNoCountriesHaveReadings() {
    var region =
        new DiscoveryRegion(regionId, "latin-america", "Latinoamérica", "Subtitle", 1, true);
    var colombia =
        new DiscoveryCountry(
            colombiaId, "CO", regionId, "Colombia", "Tag", "Desc", 1, true, List.of());

    when(discoveryRegions.findActiveRegionByKey("latin-america")).thenReturn(Optional.of(region));
    when(discoveryRegions.findActiveCountriesByRegionId(regionId)).thenReturn(List.of(colombia));
    when(readings.countPublishedPlatformReadingsByCountryCodes(anyList())).thenReturn(Map.of());
    when(readings.countPublishedPlatformReadingsByCountryCodesAndTopics(anyList()))
        .thenReturn(Map.of());

    var result = useCase.getOverview("latin-america");

    assertThat(result.region().key()).isEqualTo("latin-america");
    assertThat(result.countries()).isEmpty();
  }
}
