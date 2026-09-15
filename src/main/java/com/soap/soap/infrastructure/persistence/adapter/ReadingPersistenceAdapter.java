package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.PlatformReadingSummary;
import com.soap.soap.application.model.ReadingSummary;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.infrastructure.persistence.mapper.ReadingEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaReadingRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReadingPersistenceAdapter implements ReadingRepositoryPort {
  private final JpaReadingRepository repository;
  private final ReadingEntityMapper mapper;

  @Override
  @Transactional(readOnly = true)
  public Optional<Reading> findById(UUID id) {
    return repository.findById(id).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<ReadingSummary> findSummariesByUserId(UUID userId, PageRequest pageRequest) {
    var page =
        repository.findSummariesByUserId(
            userId,
            org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size()));
    return new PageResult<>(
        page.getContent().stream()
            .map(
                summary ->
                    new ReadingSummary(
                        summary.getId(),
                        summary.getTitle(),
                        summary.getLanguage(),
                        summary.getCreatedAt()))
            .toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<Reading> findUserReadingsByUserId(UUID userId, PageRequest pageRequest) {
    var page =
        repository.findUserReadingsByUserId(
            userId,
            org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size()));
    return new PageResult<>(
        page.getContent().stream().map(mapper::toDomain).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<PlatformReadingSummary> findPlatformSummaries(PageRequest pageRequest) {
    var page =
        repository.findPlatformSummaries(
            org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size()));
    return new PageResult<>(
        page.getContent().stream()
            .map(
                summary ->
                    new PlatformReadingSummary(
                        summary.getId(),
                        summary.getTitle(),
                        summary.getLanguage(),
                        summary.getEditorialLevel(),
                        summary.getCategory(),
                        summary.getCreatedAt(),
                        null,
                        summary.getCoverKey(),
                        summary.getShortDescription(),
                        summary.getContentType(),
                        summary.getCountryCode(),
                        summary.getRegion(),
                        summary.getAccessTier()))
            .toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<PlatformReadingSummary> findPlatformSummaries(
      String language, PageRequest pageRequest) {
    var page =
        repository.findPlatformSummariesByLanguage(
            language,
            org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size()));
    return new PageResult<>(
        page.getContent().stream()
            .map(
                summary ->
                    new PlatformReadingSummary(
                        summary.getId(),
                        summary.getTitle(),
                        summary.getLanguage(),
                        summary.getEditorialLevel(),
                        summary.getCategory(),
                        summary.getCreatedAt(),
                        null,
                        summary.getCoverKey(),
                        summary.getShortDescription(),
                        summary.getContentType(),
                        summary.getCountryCode(),
                        summary.getRegion(),
                        summary.getAccessTier()))
            .toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());
  }

  @Override
  @Transactional(readOnly = true)
  public List<PlatformReadingSummary> findAllPlatformReadingSummaries() {
    return repository.findAllPlatformSummaries().stream()
        .map(
            summary ->
                new PlatformReadingSummary(
                    summary.getId(),
                    summary.getTitle(),
                    summary.getLanguage(),
                    summary.getEditorialLevel(),
                    summary.getCategory(),
                    summary.getCreatedAt(),
                    null,
                    summary.getCoverKey(),
                    summary.getShortDescription(),
                    summary.getContentType(),
                    summary.getCountryCode(),
                    summary.getRegion(),
                    summary.getAccessTier()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Reading> findAllPlatformReadings() {
    return repository.findAllPlatformReadings().stream().map(mapper::toDomain).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Reading> findPlatformReadingByAdaptationKey(
      String adaptationGroupKey,
      String language,
      com.soap.soap.domain.model.EditorialLevel editorialLevel) {
    return repository
        .findByAdaptationGroupKeyAndLanguageAndEditorialLevelAndOrigin(
            adaptationGroupKey,
            language,
            editorialLevel,
            com.soap.soap.domain.model.ReadingOrigin.PLATFORM)
        .map(mapper::toDomain);
  }

  @Override
  @Transactional
  public Reading save(Reading reading) {
    return mapper.toDomain(repository.save(mapper.toEntity(reading)));
  }

  @Override
  @Transactional
  public Reading saveAndFlush(Reading reading) {
    return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(reading)));
  }

  @Override
  @Transactional
  public void deleteById(UUID id) {
    repository.deleteById(id);
  }
}
