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
  List<UserVocabularyEntity> findByUserIdAndWordIdIn(UUID userId, Collection<UUID> wordIds);

  @EntityGraph(attributePaths = {"user", "word"})
  Page<UserVocabularyEntity> findByUserIdOrderByFirstSeenAtDesc(UUID userId, Pageable pageable);

  @Query(
      """
      select uv
      from UserVocabularyEntity uv
      join uv.word w
      where uv.user.id = :userId
        and lower(w.language) in :languages
        and w.normalizedValue in :normalizedValues
      """)
  List<UserVocabularyEntity> findByNormalizedValues(
      @Param("userId") UUID userId,
      @Param("languages") Collection<String> languages,
      @Param("normalizedValues") Collection<String> normalizedValues);

  @Query(
      """
      select uv.status, count(uv)
      from UserVocabularyEntity uv
      where uv.user.id = :userId
      group by uv.status
      """)
  List<Object[]> countGroupedByStatus(@Param("userId") UUID userId);

  @EntityGraph(attributePaths = {"user", "word"})
  @Query(
      value =
          """
          select uv
          from UserVocabularyEntity uv
          join uv.word w
          where uv.user.id = :userId
            and (:status is null or uv.status = :status)
            and (:searchPattern is null or w.normalizedValue like :searchPattern)
          order by uv.firstSeenAt desc, w.normalizedValue asc
          """,
      countQuery =
          """
          select count(uv)
          from UserVocabularyEntity uv
          join uv.word w
          where uv.user.id = :userId
            and (:status is null or uv.status = :status)
            and (:searchPattern is null or w.normalizedValue like :searchPattern)
          """)
  Page<UserVocabularyEntity> findByCriteria(
      @Param("userId") UUID userId,
      @Param("status") com.soap.soap.domain.model.VocabularyStatus status,
      @Param("searchPattern") String searchPattern,
      Pageable pageable);

  @EntityGraph(attributePaths = {"user", "word"})
  @Query(
      """
      select uv
      from UserVocabularyEntity uv
      join uv.word w
      where uv.user.id = :userId
        and (
          (uv.nextReviewAt is not null and uv.nextReviewAt <= :now and uv.status not in (com.soap.soap.domain.model.VocabularyStatus.IGNORED, com.soap.soap.domain.model.VocabularyStatus.NEW))
          or (uv.status = com.soap.soap.domain.model.VocabularyStatus.LEARNING and uv.nextReviewAt is null)
        )
      order by
        case
          when (uv.nextReviewAt is not null and uv.nextReviewAt <= :now and uv.status not in (com.soap.soap.domain.model.VocabularyStatus.IGNORED, com.soap.soap.domain.model.VocabularyStatus.NEW)) then 1
          when (uv.status = com.soap.soap.domain.model.VocabularyStatus.LEARNING and uv.nextReviewAt is null) then 2
          else 99
        end asc,
        uv.nextReviewAt asc nulls last,
        uv.firstSeenAt desc,
        w.normalizedValue asc
      """)
  List<UserVocabularyEntity> findReviewCandidates(
      @Param("userId") UUID userId, @Param("now") java.time.LocalDateTime now, Pageable pageable);

  @Query(
      """
      select count(uv)
      from UserVocabularyEntity uv
      where uv.user.id = :userId
        and uv.nextReviewAt is not null
        and uv.nextReviewAt <= :now
        and uv.status not in (com.soap.soap.domain.model.VocabularyStatus.IGNORED, com.soap.soap.domain.model.VocabularyStatus.NEW)
      """)
  long countDueWords(@Param("userId") UUID userId, @Param("now") java.time.LocalDateTime now);

  @Query(
      """
      select count(uv)
      from UserVocabularyEntity uv
      where uv.user.id = :userId
        and (
          (uv.nextReviewAt is not null and uv.nextReviewAt <= :now and uv.status not in (com.soap.soap.domain.model.VocabularyStatus.IGNORED, com.soap.soap.domain.model.VocabularyStatus.NEW))
          or (uv.status = com.soap.soap.domain.model.VocabularyStatus.LEARNING and uv.nextReviewAt is null)
        )
      """)
  long countTotalReviewableWords(
      @Param("userId") UUID userId, @Param("now") java.time.LocalDateTime now);
}
