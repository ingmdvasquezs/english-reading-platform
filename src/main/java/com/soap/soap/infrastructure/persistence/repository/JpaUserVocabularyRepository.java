package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.UserVocabularyEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaUserVocabularyRepository extends JpaRepository<UserVocabularyEntity, UUID> {

  @EntityGraph(attributePaths = {"user", "word"})
  Optional<UserVocabularyEntity> findByUserIdAndWordId(UUID userId, UUID wordId);

  @EntityGraph(attributePaths = {"user", "word"})
  Page<UserVocabularyEntity> findByUserIdOrderByFirstSeenAtDesc(UUID userId, Pageable pageable);

  @Query(
      """
      select uv
      from UserVocabularyEntity uv
      join uv.word w
      where uv.user.id = :userId
        and w.language = :language
        and w.normalizedValue in :normalizedValues
      """)
  List<UserVocabularyEntity> findByNormalizedValues(
      @Param("userId") UUID userId,
      @Param("language") String language,
      @Param("normalizedValues") Collection<String> normalizedValues);
}
