package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.VocabularyReviewSessionEntity;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaVocabularyReviewSessionRepository
    extends JpaRepository<VocabularyReviewSessionEntity, UUID> {

  @Query(
      """
      select distinct s
      from VocabularyReviewSessionEntity s
      left join fetch s.items i
      left join fetch i.userVocabulary uv
      left join fetch uv.word w
      left join fetch uv.user u
      where s.user.id = :userId and s.localReviewDate = :localReviewDate
      """)
  Optional<VocabularyReviewSessionEntity> findByUserIdAndLocalReviewDate(
      @Param("userId") UUID userId, @Param("localReviewDate") LocalDate localReviewDate);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select s
      from VocabularyReviewSessionEntity s
      where s.user.id = :userId and s.localReviewDate = :localReviewDate
      """)
  Optional<VocabularyReviewSessionEntity> findByUserIdAndLocalReviewDateForUpdate(
      @Param("userId") UUID userId, @Param("localReviewDate") LocalDate localReviewDate);
}
