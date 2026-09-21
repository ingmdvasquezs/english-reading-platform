package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.UserVocabularyReviewHistoryEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaUserVocabularyReviewHistoryRepository
    extends JpaRepository<UserVocabularyReviewHistoryEntity, UUID> {

  List<UserVocabularyReviewHistoryEntity> findByUserVocabularyIdOrderByReviewedAtDesc(
      UUID userVocabularyId);

  Page<UserVocabularyReviewHistoryEntity> findByUserIdOrderByReviewedAtDesc(
      UUID userId, Pageable pageable);

  @Query(
      """
      select distinct h.userVocabulary.id
      from UserVocabularyReviewHistoryEntity h
      where h.user.id = :userId
        and h.reviewedAt >= :start
        and h.reviewedAt < :end
      """)
  List<UUID> findDistinctUserVocabularyIdsReviewedBetween(
      @Param("userId") UUID userId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      """
      select count(distinct h.userVocabulary.id)
      from UserVocabularyReviewHistoryEntity h
      where h.user.id = :userId
        and h.reviewedAt >= :start
        and h.reviewedAt < :end
      """)
  long countDistinctReviewedWordsBetween(
      @Param("userId") UUID userId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query(
      """
      select h.userVocabulary.id, min(h.reviewedAt), max(h.reviewedAt)
      from UserVocabularyReviewHistoryEntity h
      where h.user.id = :userId
        and h.reviewedAt >= :start
        and h.reviewedAt < :end
      group by h.userVocabulary.id
      order by min(h.reviewedAt) asc
      """)
  List<Object[]> findReviewedWordsSummaryBetween(
      @Param("userId") UUID userId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);
}
