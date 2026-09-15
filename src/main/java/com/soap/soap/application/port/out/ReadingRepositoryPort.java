package com.soap.soap.application.port.out;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.PlatformReadingSummary;
import com.soap.soap.application.model.ReadingSummary;
import com.soap.soap.domain.model.Reading;
import java.util.List;
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

  Optional<Reading> findPlatformReadingByAdaptationKey(
      String adaptationGroupKey,
      String language,
      com.soap.soap.domain.model.EditorialLevel editorialLevel);

  Reading save(Reading reading);

  Reading saveAndFlush(Reading reading);

  void deleteById(UUID id);
}
