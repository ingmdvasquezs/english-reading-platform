package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.ReadingEntity;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaReadingRepository extends JpaRepository<ReadingEntity, UUID> {

  @Override
  @EntityGraph(attributePaths = "user")
  Optional<ReadingEntity> findById(UUID id);

  @Query(
      value =
          """
          select r.id as id, r.title as title, r.language as language, r.createdAt as createdAt
          from ReadingEntity r
          where r.user.id = :userId
          order by r.createdAt desc
          """,
      countQuery = "select count(r) from ReadingEntity r where r.user.id = :userId")
  Page<ReadingSummaryView> findSummariesByUserId(@Param("userId") UUID userId, Pageable pageable);

  interface ReadingSummaryView {
    UUID getId();

    String getTitle();

    String getLanguage();

    LocalDateTime getCreatedAt();
  }
}
