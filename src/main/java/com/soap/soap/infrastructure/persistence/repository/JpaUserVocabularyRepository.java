package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.UserVocabularyEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaUserVocabularyRepository extends JpaRepository<UserVocabularyEntity, UUID> {

  @EntityGraph(attributePaths = {"user", "word"})
  Optional<UserVocabularyEntity> findByUserIdAndWordId(UUID userId, UUID wordId);

  @EntityGraph(attributePaths = {"user", "word"})
  List<UserVocabularyEntity> findByUserIdOrderByFirstSeenAtDesc(UUID userId);
}
