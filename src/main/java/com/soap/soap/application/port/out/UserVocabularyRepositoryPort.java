package com.soap.soap.application.port.out;

import com.soap.soap.domain.model.UserVocabulary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserVocabularyRepositoryPort {

  Optional<UserVocabulary> findByUserIdAndWordId(UUID userId, UUID wordId);

  List<UserVocabulary> findByUserId(UUID userId);

  UserVocabulary save(UserVocabulary vocabulary);
}
