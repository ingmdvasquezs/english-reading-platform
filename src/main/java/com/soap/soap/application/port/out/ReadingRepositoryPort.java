package com.soap.soap.application.port.out;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.PlatformReadingSummary;
import com.soap.soap.application.model.ReadingSummary;
import com.soap.soap.domain.model.DiscoveryTopic;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.PlatformReadingSort;
import com.soap.soap.domain.model.Reading;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ReadingRepositoryPort {

  Optional<Reading> findById(UUID id);

  PageResult<ReadingSummary> findSummariesByUserId(UUID userId, PageRequest pageRequest);

  PageResult<Reading> findUserReadingsByUserId(UUID userId, PageRequest pageRequest);

  PageResult<PlatformReadingSummary> findPlatformSummaries(PageRequest pageRequest);

  PageResult<PlatformReadingSummary> findPlatformSummaries(
      String language, PageRequest pageRequest);

  List<PlatformReadingSummary> findAllPlatformReadingSummaries();

  List<Reading> findAllPlatformReadings();

  default PageResult<Reading> browsePlatformReadings(
      String collectionKey,
      String category,
      EditorialLevel editorialLevel,
      String language,
      PageRequest pageRequest) {
    return browsePlatformReadings(
        collectionKey,
        category,
        editorialLevel,
        null,
        null,
        PlatformReadingSort.DEFAULT,
        language,
        pageRequest);
  }

  default PageResult<Reading> browsePlatformReadings(
      String collectionKey,
      String category,
      EditorialLevel editorialLevel,
      String countryCode,
      DiscoveryTopic discoveryTopic,
      String language,
      PageRequest pageRequest) {
    return browsePlatformReadings(
        collectionKey,
        category,
        editorialLevel,
        countryCode,
        discoveryTopic,
        PlatformReadingSort.DEFAULT,
        language,
        pageRequest);
  }

  PageResult<Reading> browsePlatformReadings(
      String collectionKey,
      String category,
      EditorialLevel editorialLevel,
      String countryCode,
      DiscoveryTopic discoveryTopic,
      PlatformReadingSort sort,
      String language,
      PageRequest pageRequest);

  Map<String, Long> countPublishedPlatformReadingsByCountryCodes(List<String> countryCodes);

  Map<String, Map<DiscoveryTopic, Long>> countPublishedPlatformReadingsByCountryCodesAndTopics(
      List<String> countryCodes);

  Optional<Reading> findPlatformReadingByAdaptationKey(
      String adaptationGroupKey, String language, EditorialLevel editorialLevel);

  List<Reading> findRecentPlatformReadings(String language, int page, int size);

  Reading save(Reading reading);

  Reading saveAndFlush(Reading reading);

  void deleteById(UUID id);
}
