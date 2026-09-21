package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.PlatformReadingSummary;
import com.soap.soap.application.model.ReadingSummary;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.PlatformReadingSort;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.infrastructure.persistence.mapper.ReadingEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaReadingRepository;
import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
  public List<Reading> findRecentPlatformReadings(String language, int page, int size) {
    return repository
        .findRecentPlatformReadings(
            language, org.springframework.data.domain.PageRequest.of(page, size))
        .getContent()
        .stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<Reading> browsePlatformReadings(
      String collectionKey,
      String category,
      com.soap.soap.domain.model.EditorialLevel editorialLevel,
      String countryCode,
      com.soap.soap.domain.model.DiscoveryTopic discoveryTopic,
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

  @Override
  @Transactional(readOnly = true)
  public PageResult<Reading> browsePlatformReadings(
      String collectionKey,
      String category,
      com.soap.soap.domain.model.EditorialLevel editorialLevel,
      String countryCode,
      com.soap.soap.domain.model.DiscoveryTopic discoveryTopic,
      PlatformReadingSort sort,
      String language,
      PageRequest pageRequest) {

    boolean hasCollection = collectionKey != null && !collectionKey.isBlank();
    boolean hasCategory = category != null && !category.isBlank();
    boolean hasLevel = editorialLevel != null;
    boolean hasCountry = countryCode != null && !countryCode.isBlank();
    boolean hasTopic = discoveryTopic != null;

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

    if (hasCountry) {
      dataJpql.append(" AND r.countryCode = :countryCode");
      countJpql.append(" AND r.countryCode = :countryCode");
    }

    if (hasTopic) {
      dataJpql.append(" AND r.discoveryTopic = :discoveryTopic");
      countJpql.append(" AND r.discoveryTopic = :discoveryTopic");
    }

    if (sort == PlatformReadingSort.CREATED_AT_DESC) {
      dataJpql.append(" ORDER BY r.createdAt DESC, r.id ASC");
    } else if (hasCollection) {
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
    if (hasCountry) {
      query.setParameter("countryCode", countryCode);
      countQuery.setParameter("countryCode", countryCode);
    }
    if (hasTopic) {
      query.setParameter("discoveryTopic", discoveryTopic);
      countQuery.setParameter("discoveryTopic", discoveryTopic);
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
  public java.util.Map<String, Long> countPublishedPlatformReadingsByCountryCodes(
      List<String> countryCodes) {
    if (countryCodes == null || countryCodes.isEmpty()) {
      return java.util.Map.of();
    }
    List<Object[]> rows =
        entityManager
            .createQuery(
                "SELECT r.countryCode, count(r.id) FROM ReadingEntity r "
                    + "WHERE r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM "
                    + "AND r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED "
                    + "AND r.countryCode IN (:countryCodes) "
                    + "GROUP BY r.countryCode",
                Object[].class)
            .setParameter("countryCodes", countryCodes)
            .getResultList();

    java.util.Map<String, Long> result = new java.util.HashMap<>();
    for (Object[] row : rows) {
      result.put((String) row[0], ((Number) row[1]).longValue());
    }
    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.Map<String, java.util.Map<com.soap.soap.domain.model.DiscoveryTopic, Long>>
      countPublishedPlatformReadingsByCountryCodesAndTopics(List<String> countryCodes) {
    if (countryCodes == null || countryCodes.isEmpty()) {
      return java.util.Map.of();
    }
    List<Object[]> rows =
        entityManager
            .createQuery(
                "SELECT r.countryCode, r.discoveryTopic, count(r.id) FROM ReadingEntity r "
                    + "WHERE r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM "
                    + "AND r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED "
                    + "AND r.countryCode IN (:countryCodes) "
                    + "AND r.discoveryTopic IS NOT NULL "
                    + "GROUP BY r.countryCode, r.discoveryTopic",
                Object[].class)
            .setParameter("countryCodes", countryCodes)
            .getResultList();

    java.util.Map<String, java.util.Map<com.soap.soap.domain.model.DiscoveryTopic, Long>> result =
        new java.util.HashMap<>();
    for (Object[] row : rows) {
      String country = (String) row[0];
      com.soap.soap.domain.model.DiscoveryTopic topic =
          (com.soap.soap.domain.model.DiscoveryTopic) row[1];
      Long count = ((Number) row[2]).longValue();
      result.computeIfAbsent(country, k -> new java.util.HashMap<>()).put(topic, count);
    }
    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public Set<UUID> findPublishedPlatformReadingIdsByCountryCodes(List<String> countryCodes) {
    if (countryCodes == null || countryCodes.isEmpty()) {
      return Collections.emptySet();
    }
    List<String> normalizedCodes =
        countryCodes.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toUpperCase)
            .toList();
    if (normalizedCodes.isEmpty()) {
      return Collections.emptySet();
    }
    List<UUID> ids =
        entityManager
            .createQuery(
                "SELECT r.id FROM ReadingEntity r "
                    + "WHERE r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM "
                    + "AND r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED "
                    + "AND UPPER(r.countryCode) IN (:countryCodes)",
                UUID.class)
            .setParameter("countryCodes", normalizedCodes)
            .getResultList();
    return new HashSet<>(ids);
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
