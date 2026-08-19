package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.infrastructure.persistence.mapper.UserVocabularyEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaUserVocabularyRepository;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserVocabularyPersistenceAdapter implements UserVocabularyRepositoryPort {
  private final JpaUserVocabularyRepository repository;
  private final UserVocabularyEntityMapper mapper;

  @Override
  @Transactional(readOnly = true)
  public Optional<UserVocabulary> findByUserIdAndWordId(UUID userId, UUID wordId) {
    return repository.findByUserIdAndWordId(userId, wordId).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<UserVocabulary> findByUserId(UUID userId, PageRequest pageRequest) {
    var page =
        repository.findByUserIdOrderByFirstSeenAtDesc(
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
  public Set<String> findKnownNormalizedValues(
      UUID userId, String language, Collection<String> normalizedValues) {
    return Set.copyOf(
        repository.findNormalizedValues(
            userId, VocabularyStatus.KNOWN, language, normalizedValues));
  }

  @Override
  @Transactional
  public UserVocabulary save(UserVocabulary vocabulary) {
    return mapper.toDomain(repository.save(mapper.toEntity(vocabulary)));
  }
}
