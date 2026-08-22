package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.exception.ConcurrentVocabularyModificationException;
import com.soap.soap.application.exception.WordAlreadyInVocabularyException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.infrastructure.persistence.mapper.UserVocabularyEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaUserVocabularyRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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
  public Map<String, VocabularyStatus> findStatusesByNormalizedValues(
      UUID userId, String language, Collection<String> normalizedValues) {
    return repository.findByNormalizedValues(userId, language, normalizedValues).stream()
        .collect(
            Collectors.toUnmodifiableMap(
                entry -> entry.getWord().getNormalizedValue(), entry -> entry.getStatus()));
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, UserVocabulary> findByUserIdAndWordIds(UUID userId, Collection<UUID> wordIds) {
    if (wordIds.isEmpty()) {
      return Map.of();
    }
    return repository.findByUserIdAndWordIdIn(userId, wordIds).stream()
        .map(mapper::toDomain)
        .collect(Collectors.toUnmodifiableMap(entry -> entry.word().id(), entry -> entry));
  }

  @Override
  @Transactional
  public UserVocabulary save(UserVocabulary vocabulary) {
    try {
      return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(vocabulary)));
    } catch (OptimisticLockingFailureException exception) {
      throw new ConcurrentVocabularyModificationException();
    } catch (DataIntegrityViolationException exception) {
      if (hasConstraint(exception, "uk_user_vocabulary_user_word")) {
        throw new WordAlreadyInVocabularyException(vocabulary.word().normalizedValue());
      }
      throw exception;
    }
  }

  @Override
  @Transactional
  public Collection<UserVocabulary> saveAll(Collection<UserVocabulary> vocabulary) {
    try {
      var entities = vocabulary.stream().map(mapper::toEntity).toList();
      return repository.saveAllAndFlush(entities).stream().map(mapper::toDomain).toList();
    } catch (OptimisticLockingFailureException exception) {
      throw new ConcurrentVocabularyModificationException();
    } catch (DataIntegrityViolationException exception) {
      if (hasConstraint(exception, "uk_user_vocabulary_user_word")) {
        throw new WordAlreadyInVocabularyException("onboarding selection");
      }
      throw exception;
    }
  }

  private boolean hasConstraint(Throwable exception, String constraint) {
    for (var cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation
          && constraint.equals(violation.getConstraintName())) {
        return true;
      }
      if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
        return true;
      }
    }
    return false;
  }
}
