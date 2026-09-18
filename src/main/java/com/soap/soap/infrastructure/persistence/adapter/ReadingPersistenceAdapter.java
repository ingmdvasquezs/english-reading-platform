package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.PlatformReadingSummary;
import com.soap.soap.application.model.ReadingSummary;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.infrastructure.persistence.mapper.ReadingEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaReadingRepository;
import jakarta.persistence.EntityManager;
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
  private final EntityManager entityManager;

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
  public PageResult<Reading> browsePlatformReadings(
      String collectionKey,
      String category,
      com.soap.soap.domain.model.EditorialLevel editorialLevel,
      String language,
      PageRequest pageRequest) {

    boolean hasCollection = collectionKey != null && !collectionKey.isBlank();
    boolean hasCategory = category != null && !category.isBlank();
    boolean hasLevel = editorialLevel != null;

    StringBuilder dataJpql = new StringBuilder();
    StringBuilder countJpql = new StringBuilder();

    if (hasCollection) {
      dataJpql.append(
          "SELECT r FROM ReadingCollectionMembershipEntity m "
              + "JOIN ReadingCollectionEntity c ON c.id = m.id.collectionId "
              + "JOIN ReadingEntity r ON r.id = m.id.readingId "
              + "WHERE c.key = :collectionKey AND c.active = true "
              + "AND r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM "
              + "AND r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED "
              + "AND r.language = :language");
      countJpql.append(
          "SELECT count(r) FROM ReadingCollectionMembershipEntity m "
              + "JOIN ReadingCollectionEntity c ON c.id = m.id.collectionId "
              + "JOIN ReadingEntity r ON r.id = m.id.readingId "
              + "WHERE c.key = :collectionKey AND c.active = true "
              + "AND r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM "
              + "AND r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED "
              + "AND r.language = :language");
    } else {
      dataJpql.append(
          "SELECT r FROM ReadingEntity r "
              + "WHERE r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM "
              + "AND r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED "
              + "AND r.language = :language");
      countJpql.append(
          "SELECT count(r) FROM ReadingEntity r "
              + "WHERE r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM "
              + "AND r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED "
              + "AND r.language = :language");
    }

    if (hasCategory) {
      dataJpql.append(" AND r.category = :category");
      countJpql.append(" AND r.category = :category");
    }

    if (hasLevel) {
      dataJpql.append(" AND r.editorialLevel = :editorialLevel");
      countJpql.append(" AND r.editorialLevel = :editorialLevel");
    }

    if (hasCollection) {
      dataJpql.append(" ORDER BY m.displayOrder ASC, r.id ASC");
    } else {
      dataJpql.append(" ORDER BY r.createdAt DESC, r.id ASC");
    }

    var query =
        entityManager.createQuery(
            dataJpql.toString(),
            com.soap.soap.infrastructure.persistence.entity.ReadingEntity.class);
    var countQuery = entityManager.createQuery(countJpql.toString(), Long.class);

    query.setParameter("language", language);
    countQuery.setParameter("language", language);

    if (hasCollection) {
      query.setParameter("collectionKey", collectionKey);
      countQuery.setParameter("collectionKey", collectionKey);
    }
    if (hasCategory) {
      query.setParameter("category", category);
      countQuery.setParameter("category", category);
    }
    if (hasLevel) {
      query.setParameter("editorialLevel", editorialLevel);
      countQuery.setParameter("editorialLevel", editorialLevel);
    }

    query.setFirstResult(pageRequest.page() * pageRequest.size());
    query.setMaxResults(pageRequest.size());

    List<com.soap.soap.infrastructure.persistence.entity.ReadingEntity> entities =
        query.getResultList();
    Long total = countQuery.getSingleResult();

    List<Reading> content = entities.stream().map(mapper::toDomain).toList();
    return new PageResult<>(content, pageRequest.page(), pageRequest.size(), total);
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
