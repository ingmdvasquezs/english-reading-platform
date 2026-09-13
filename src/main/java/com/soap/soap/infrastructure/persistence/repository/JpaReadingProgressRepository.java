package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.infrastructure.persistence.entity.ReadingProgressEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaReadingProgressRepository extends JpaRepository<ReadingProgressEntity, UUID> {
  @Query(
      "select p from ReadingProgressEntity p where p.user.id = :userId and p.reading.id = :readingId")
  Optional<ReadingProgressEntity> findForUserAndReading(UUID userId, UUID readingId);

  @Query(
      "select p from ReadingProgressEntity p where p.user.id = :userId and p.reading.id in :readingIds")
  List<ReadingProgressEntity> findForUserAndReadingIds(UUID userId, Set<UUID> readingIds);

  @Query(
      value =
          """
          select p.reading.id as readingId, p.reading.title as title,
                 p.reading.origin as origin, p.status as progressStatus,
                 p.reading.coverKey as coverKey, p.reading.editorialLevel as editorialLevel,
                 p.reading.category as category, p.startedAt as startedAt
          from ReadingProgressEntity p
          where p.user.id = :userId
            and p.status = com.soap.soap.domain.model.ReadingProgressStatus.IN_PROGRESS
            and ((p.reading.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM
                  and p.reading.editorialStatus in (
                      com.soap.soap.domain.model.EditorialStatus.PUBLISHED,
                      com.soap.soap.domain.model.EditorialStatus.ARCHIVED
                  ))
                 or p.reading.user.id = :userId)
          order by p.startedAt desc, p.reading.id asc
          """,
      countQuery =
          """
          select count(p)
          from ReadingProgressEntity p
          where p.user.id = :userId
            and p.status = com.soap.soap.domain.model.ReadingProgressStatus.IN_PROGRESS
            and ((p.reading.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM
                  and p.reading.editorialStatus in (
                      com.soap.soap.domain.model.EditorialStatus.PUBLISHED,
                      com.soap.soap.domain.model.EditorialStatus.ARCHIVED
                  ))
                 or p.reading.user.id = :userId)
          """)
  Page<ContinueReadingView> findInProgressReadings(@Param("userId") UUID userId, Pageable pageable);

  interface ContinueReadingView {
    UUID getReadingId();

    String getTitle();

    ReadingOrigin getOrigin();

    ReadingProgressStatus getProgressStatus();

    String getCoverKey();

    EditorialLevel getEditorialLevel();

    String getCategory();

    LocalDateTime getStartedAt();
  }

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO reading_progress(id, user_id, reading_id, status, started_at, completed_at)
          VALUES (:id, :userId, :readingId, 'IN_PROGRESS', :startedAt, NULL)
          ON CONFLICT (user_id, reading_id) DO NOTHING
          """,
      nativeQuery = true)
  int insertInProgressIfAbsent(
      @Param("id") UUID id,
      @Param("userId") UUID userId,
      @Param("readingId") UUID readingId,
      @Param("startedAt") LocalDateTime startedAt);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO reading_progress(id, user_id, reading_id, status, started_at, completed_at)
          VALUES (:id, :userId, :readingId, 'COMPLETED', :completedAt, :completedAt)
          ON CONFLICT (user_id, reading_id) DO NOTHING
          """,
      nativeQuery = true)
  int insertCompletedIfAbsent(
      @Param("id") UUID id,
      @Param("userId") UUID userId,
      @Param("readingId") UUID readingId,
      @Param("completedAt") LocalDateTime completedAt);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update ReadingProgressEntity p
         set p.status = com.soap.soap.domain.model.ReadingProgressStatus.COMPLETED,
             p.completedAt = :completedAt
       where p.user.id = :userId and p.reading.id = :readingId
         and p.status = com.soap.soap.domain.model.ReadingProgressStatus.IN_PROGRESS
      """)
  int completeIfInProgress(UUID userId, UUID readingId, LocalDateTime completedAt);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update ReadingProgressEntity p
         set p.currentPartOrdinal = :currentPartOrdinal,
             p.paginationVersion = :paginationVersion
       where p.user.id = :userId and p.reading.id = :readingId
      """)
  int updatePosition(UUID userId, UUID readingId, int currentPartOrdinal, int paginationVersion);
}
