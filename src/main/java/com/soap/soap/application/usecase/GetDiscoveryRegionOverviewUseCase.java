package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.DiscoveryRegionNotFoundException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.model.DiscoveryCountrySummary;
import com.soap.soap.application.model.DiscoveryHeroImageSummary;
import com.soap.soap.application.model.DiscoveryRegionDetails;
import com.soap.soap.application.model.DiscoveryRegionOverviewResult;
import com.soap.soap.application.model.DiscoveryTopicSummary;
import com.soap.soap.application.port.in.GetDiscoveryRegionOverviewPort;
import com.soap.soap.application.port.out.DiscoveryRegionRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.DiscoveryCountry;
import com.soap.soap.domain.model.DiscoveryRegion;
import com.soap.soap.domain.model.DiscoveryTopic;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetDiscoveryRegionOverviewUseCase implements GetDiscoveryRegionOverviewPort {

  private final DiscoveryRegionRepositoryPort discoveryRegions;
  private final ReadingRepositoryPort readings;

  @Override
  @Transactional(readOnly = true)
  public DiscoveryRegionOverviewResult getOverview(String regionKey) {
    if (regionKey == null || regionKey.isBlank()) {
      throw new InvalidApplicationArgumentException("Region key must not be blank");
    }

    DiscoveryRegion region =
        discoveryRegions
            .findActiveRegionByKey(regionKey.trim())
            .orElseThrow(() -> new DiscoveryRegionNotFoundException(regionKey.trim()));

    List<DiscoveryCountry> candidateCountries =
        discoveryRegions.findActiveCountriesByRegionId(region.id());

    if (candidateCountries.isEmpty()) {
      return new DiscoveryRegionOverviewResult(
          new DiscoveryRegionDetails(region.key(), region.displayName(), region.subtitle()),
          List.of());
    }

    List<String> countryCodes =
        candidateCountries.stream().map(DiscoveryCountry::countryCode).toList();

    // Aggregated query 1: Published platform reading count by country
    Map<String, Long> readingCountsByCountry =
        readings.countPublishedPlatformReadingsByCountryCodes(countryCodes);

    // Aggregated query 2: Published platform reading count by country and discovery topic
    Map<String, Map<DiscoveryTopic, Long>> topicCountsByCountry =
        readings.countPublishedPlatformReadingsByCountryCodesAndTopics(countryCodes);

    List<DiscoveryCountrySummary> countrySummaries = new ArrayList<>();

    for (DiscoveryCountry country : candidateCountries) {
      long totalCount = readingCountsByCountry.getOrDefault(country.countryCode(), 0L);
      if (totalCount <= 0) {
        // Exclude countries without published platform readings
        continue;
      }

      // Hero images
      List<DiscoveryHeroImageSummary> heroImages =
          country.heroImages() == null
              ? List.of()
              : country.heroImages().stream()
                  .sorted(Comparator.comparingInt(img -> img.displayOrder()))
                  .map(
                      img ->
                          new DiscoveryHeroImageSummary(
                              img.assetKey(), img.location(), img.alt(), img.displayOrder()))
                  .toList();

      // Topics with at least 1 published reading
      Map<DiscoveryTopic, Long> topicCounts =
          topicCountsByCountry.getOrDefault(country.countryCode(), Map.of());

      List<DiscoveryTopicSummary> topicSummaries =
          topicCounts.entrySet().stream()
              .filter(entry -> entry.getValue() > 0)
              .sorted(
                  Comparator.comparingInt(
                          (Map.Entry<DiscoveryTopic, Long> e) -> e.getKey().getDisplayOrder())
                      .thenComparing(e -> e.getKey().name()))
              .map(
                  entry ->
                      new DiscoveryTopicSummary(
                          entry.getKey().name(),
                          entry.getKey().getDisplayName(),
                          entry.getKey().getDisplayOrder(),
                          entry.getValue().intValue()))
              .toList();

      countrySummaries.add(
          new DiscoveryCountrySummary(
              country.countryCode(),
              country.displayName(),
              country.tagline(),
              country.description(),
              country.displayOrder(),
              (int) totalCount,
              heroImages,
              topicSummaries));
    }

    // Sort countries by displayOrder ASC, then countryCode ASC
    countrySummaries.sort(
        Comparator.comparingInt(DiscoveryCountrySummary::displayOrder)
            .thenComparing(DiscoveryCountrySummary::countryCode));

    return new DiscoveryRegionOverviewResult(
        new DiscoveryRegionDetails(region.key(), region.displayName(), region.subtitle()),
        countrySummaries);
  }
}
