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
    return collections.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
        .map(mapper::toDomain)
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
}
