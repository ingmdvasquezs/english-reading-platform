package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.port.out.ReadingCollectionRepositoryPort;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingCollection;
import com.soap.soap.infrastructure.persistence.mapper.ReadingCollectionEntityMapper;
import com.soap.soap.infrastructure.persistence.mapper.ReadingEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaReadingCollectionMembershipRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaReadingCollectionRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReadingCollectionPersistenceAdapter implements ReadingCollectionRepositoryPort {
  private final JpaReadingCollectionRepository collections;
  private final JpaReadingCollectionMembershipRepository memberships;
  private final ReadingCollectionEntityMapper mapper;
  private final ReadingEntityMapper readingMapper;

  @Override
  @Transactional(readOnly = true)
  public List<ReadingCollection> findAllActive() {
    return collections.findActiveWithPublishedReadings().stream().map(mapper::toDomain).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<com.soap.soap.application.model.CollectionSummary> findAllActiveSummaries() {
    return collections.findActiveWithPublishedReadingCounts().stream()
        .map(
            row -> {
              var entity =
                  (com.soap.soap.infrastructure.persistence.entity.ReadingCollectionEntity) row[0];
              int count = ((Number) row[1]).intValue();
              return new com.soap.soap.application.model.CollectionSummary(
                  entity.getId(),
                  entity.getKey(),
                  entity.getDisplayName(),
                  entity.getDescription(),
                  entity.getDisplayOrder(),
                  entity.isActive(),
                  entity.getCoverKey(),
                  count);
            })
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ReadingCollection> findActiveByKey(String key) {
    return collections.findByKeyAndActiveTrue(key).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<Reading> findReadings(String key, PageRequest pageRequest) {
    var page =
        memberships.findReadingsByCollectionKey(
            key,
            org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size()));
    return new PageResult<>(
        page.getContent().stream().map(readingMapper::toDomain).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());
  }

  // Language‑scoped overload (canonical language tag, no lower())
  @Override
  @Transactional(readOnly = true)
  public PageResult<Reading> findReadings(String key, String language, PageRequest pageRequest) {
    var page =
        memberships.findReadingsByCollectionKeyAndLanguage(
            key,
            language,
            org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size()));
    return new PageResult<>(
        page.getContent().stream().map(readingMapper::toDomain).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ReadingCollection> findByKey(String key) {
    return collections.findByKey(key).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.Map<java.util.UUID, List<Reading>> findTopReadingsByCollectionIds(
      List<java.util.UUID> collectionIds, String language, int limitPerCollection) {
    if (collectionIds == null || collectionIds.isEmpty() || limitPerCollection <= 0) {
      return java.util.Map.of();
    }
    List<Object[]> rows = memberships.findMembershipsByCollectionIds(collectionIds, language);
    java.util.Map<java.util.UUID, List<Reading>> result = new java.util.LinkedHashMap<>();
    for (java.util.UUID id : collectionIds) {
      result.put(id, new java.util.ArrayList<>());
    }
    for (Object[] row : rows) {
      java.util.UUID collectionId = (java.util.UUID) row[0];
      com.soap.soap.infrastructure.persistence.entity.ReadingEntity readingEntity =
          (com.soap.soap.infrastructure.persistence.entity.ReadingEntity) row[1];
      List<Reading> list = result.get(collectionId);
      if (list != null && list.size() < limitPerCollection) {
        list.add(readingMapper.toDomain(readingEntity));
      }
    }
    return result;
  }

  @Override
  @Transactional
  public ReadingCollection save(ReadingCollection collection) {
    var entity = mapper.toEntity(collection);
    var saved = collections.saveAndFlush(entity);
    return mapper.toDomain(saved);
  }

  @Override
  @Transactional
  public void replaceMemberships(
      java.util.UUID collectionId,
      List<com.soap.soap.application.model.CollectionMembershipItem> items) {
    memberships.deleteByCollectionId(collectionId);
    memberships.flush();
    if (items != null && !items.isEmpty()) {
      var entities =
          items.stream()
              .map(
                  item -> {
                    var entity =
                        new com.soap.soap.infrastructure.persistence.entity
                            .ReadingCollectionMembershipEntity();
                    entity.setId(
                        new com.soap.soap.infrastructure.persistence.entity
                            .ReadingCollectionMembershipId(collectionId, item.readingId()));
                    entity.setDisplayOrder(item.displayOrder());
                    return entity;
                  })
              .toList();
      memberships.saveAllAndFlush(entities);
    }
  }
}
