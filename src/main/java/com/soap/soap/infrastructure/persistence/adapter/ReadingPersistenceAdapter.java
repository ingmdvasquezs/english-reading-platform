package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.ReadingSummary;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.infrastructure.persistence.mapper.ReadingEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaReadingRepository;
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
  @Transactional
  public Reading save(Reading reading) {
    return mapper.toDomain(repository.save(mapper.toEntity(reading)));
  }
}
