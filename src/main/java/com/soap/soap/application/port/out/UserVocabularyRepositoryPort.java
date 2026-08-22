package com.soap.soap.application.port.out;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.domain.model.UserVocabulary;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface UserVocabularyRepositoryPort {

  Optional<UserVocabulary> findByUserIdAndWordId(UUID userId, UUID wordId);

  PageResult<UserVocabulary> findByUserId(UUID userId, PageRequest pageRequest);

  Map<String, com.soap.soap.domain.model.VocabularyStatus> findStatusesByNormalizedValues(
      UUID userId, String language, Collection<String> normalizedValues);

  Map<UUID, UserVocabulary> findByUserIdAndWordIds(UUID userId, Collection<UUID> wordIds);

  UserVocabulary save(UserVocabulary vocabulary);

  Collection<UserVocabulary> saveAll(Collection<UserVocabulary> vocabulary);
}
