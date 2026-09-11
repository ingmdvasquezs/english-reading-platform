package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.exception.ConcurrentVocabularyModificationException;
import com.soap.soap.application.exception.WordAlreadyInVocabularyException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.VocabularySummary;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.LanguageNormalizer;
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
  private final LanguageNormalizer languages;

  @Override
  @Transactional(readOnly = true)
  public Optional<UserVocabulary> findByUserIdAndWordId(UUID userId, UUID wordId) {
    return repository.findByUserIdAndWordId(userId, wordId).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<UserVocabulary> findByUserId(UUID userId, PageRequest pageRequest) {
    return findByUserIdAndCriteria(userId, null, null, pageRequest);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<UserVocabulary> findByUserIdAndCriteria(
      UUID userId, VocabularyStatus status, String searchPrefix, PageRequest pageRequest) {
    var pageable =
        org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size());
    String searchPattern =
        (searchPrefix != null && !searchPrefix.isEmpty()) ? searchPrefix + "%" : null;
    var page = repository.findByCriteria(userId, status, searchPattern, pageable);
    return new PageResult<>(
        page.getContent().stream().map(mapper::toDomain).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());
  }

  @Override
  @Transactional(readOnly = true)
  public VocabularySummary countSummaryByUserId(UUID userId) {
    long newCount = 0;
    long learningCount = 0;
    long knownCount = 0;
    long ignoredCount = 0;
    for (Object[] row : repository.countGroupedByStatus(userId)) {
      var status = (VocabularyStatus) row[0];
      var count = ((Number) row[1]).longValue();
      switch (status) {
        case NEW -> newCount = count;
        case LEARNING -> learningCount = count;
        case KNOWN -> knownCount = count;
        case IGNORED -> ignoredCount = count;
      }
    }
    long totalCount = newCount + learningCount + knownCount + ignoredCount;
    return new VocabularySummary(totalCount, newCount, learningCount, knownCount, ignoredCount);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, VocabularyStatus> findStatusesByNormalizedValues(
      UUID userId, String language, Collection<String> normalizedValues) {
    var canonical = languages.normalize(language);
    var result = new java.util.LinkedHashMap<String, VocabularyStatus>();
    repository
        .findByNormalizedValues(userId, languages.equivalentLanguages(canonical), normalizedValues)
        .stream()
        .sorted(
            java.util.Comparator.comparingInt(
                entry -> entry.getWord().getLanguage().equalsIgnoreCase(canonical) ? 0 : 1))
        .forEach(
            entry -> result.putIfAbsent(entry.getWord().getNormalizedValue(), entry.getStatus()));
    return Map.copyOf(result);
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

  @Override
  @Transactional(readOnly = true)
  public java.util.List<UserVocabulary> findReviewCandidates(
      UUID userId, java.time.LocalDateTime now, int limit) {
    var pageable = org.springframework.data.domain.PageRequest.of(0, limit);
    return repository.findReviewCandidates(userId, now, pageable).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countDueWords(UUID userId, java.time.LocalDateTime now) {
    return repository.countDueWords(userId, now);
  }

  @Override
  @Transactional(readOnly = true)
  public long countTotalReviewableWords(UUID userId, java.time.LocalDateTime now) {
    return repository.countTotalReviewableWords(userId, now);
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
