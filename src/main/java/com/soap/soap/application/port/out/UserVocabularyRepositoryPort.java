package com.soap.soap.application.port.out;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.VocabularySummary;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface UserVocabularyRepositoryPort {

  Optional<UserVocabulary> findByUserIdAndWordId(UUID userId, UUID wordId);

  PageResult<UserVocabulary> findByUserId(UUID userId, PageRequest pageRequest);

  PageResult<UserVocabulary> findByUserIdAndCriteria(
      UUID userId, VocabularyStatus status, String searchPrefix, PageRequest pageRequest);

  VocabularySummary countSummaryByUserId(UUID userId);

  Map<String, VocabularyStatus> findStatusesByNormalizedValues(
      UUID userId, String language, Collection<String> normalizedValues);

  Map<String, VocabularyStatus> findStatusesByUserAndLanguage(UUID userId, String language);

  Map<UUID, UserVocabulary> findByUserIdAndWordIds(UUID userId, Collection<UUID> wordIds);

  Map<UUID, UserVocabulary> findByIds(Collection<UUID> ids);

  UserVocabulary save(UserVocabulary vocabulary);

  Collection<UserVocabulary> saveAll(Collection<UserVocabulary> vocabulary);

  java.util.List<UserVocabulary> findReviewCandidates(
      UUID userId, java.time.LocalDateTime now, int limit);

  java.util.List<UserVocabulary> findLearnAheadCandidates(
      UUID userId, java.time.LocalDateTime now, java.time.LocalDateTime maxLearnAhead, int limit);

  long countDueWords(UUID userId, java.time.LocalDateTime now);

  long countTotalReviewableWords(UUID userId, java.time.LocalDateTime now);

  long countPendingLearningWords(UUID userId);

  long countClassifiedWordsByUserAndLanguage(UUID userId, String language);
}
