package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.OutboxEventEntity;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaOutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

  @Query(
      value =
          """
          SELECT id FROM outbox_events
          WHERE (
            (status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= :now))
            OR (status = 'SENDING' AND locked_until <= :now)
          )
          ORDER BY created_at ASC
          LIMIT :batchSize
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<UUID> findClaimableIds(@Param("now") LocalDateTime now, @Param("batchSize") int batchSize);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      UPDATE OutboxEventEntity e
      SET e.status = com.soap.soap.domain.model.OutboxEventStatus.SENDING,
          e.lockedBy = :dispatcherId,
          e.lockedUntil = :lockedUntil,
          e.attemptCount = e.attemptCount + 1,
          e.updatedAt = :now,
          e.version = e.version + 1
      WHERE e.id IN (:ids)
      """)
  int lockBatch(
      @Param("ids") Collection<UUID> ids,
      @Param("dispatcherId") String dispatcherId,
      @Param("lockedUntil") LocalDateTime lockedUntil,
      @Param("now") LocalDateTime now);

  List<OutboxEventEntity> findByIdInOrderByCreatedAtAsc(Collection<UUID> ids);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      UPDATE OutboxEventEntity e
      SET e.status = com.soap.soap.domain.model.OutboxEventStatus.PUBLISHED,
          e.publishedAt = :now,
          e.lockedBy = NULL,
          e.lockedUntil = NULL,
          e.updatedAt = :now,
          e.version = e.version + 1
      WHERE e.id = :eventId
        AND e.lockedBy = :dispatcherId
        AND e.status = com.soap.soap.domain.model.OutboxEventStatus.SENDING
        AND e.lockedUntil >= :now
      """)
  int markPublished(
      @Param("eventId") UUID eventId,
      @Param("dispatcherId") String dispatcherId,
      @Param("now") LocalDateTime now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      UPDATE OutboxEventEntity e
      SET e.status = com.soap.soap.domain.model.OutboxEventStatus.PENDING,
          e.nextAttemptAt = :nextAttemptAt,
          e.lockedBy = NULL,
          e.lockedUntil = NULL,
          e.lastError = :error,
          e.updatedAt = :now,
          e.version = e.version + 1
      WHERE e.id = :eventId
        AND e.lockedBy = :dispatcherId
        AND e.status = com.soap.soap.domain.model.OutboxEventStatus.SENDING
        AND e.lockedUntil >= :now
        AND e.attemptCount < e.maxAttempts
      """)
  int markFailedRetry(
      @Param("eventId") UUID eventId,
      @Param("dispatcherId") String dispatcherId,
      @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
      @Param("error") String error,
      @Param("now") LocalDateTime now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      UPDATE OutboxEventEntity e
      SET e.status = com.soap.soap.domain.model.OutboxEventStatus.FAILED,
          e.lockedBy = NULL,
          e.lockedUntil = NULL,
          e.lastError = :error,
          e.updatedAt = :now,
          e.version = e.version + 1
      WHERE e.id = :eventId
        AND e.lockedBy = :dispatcherId
        AND e.status = com.soap.soap.domain.model.OutboxEventStatus.SENDING
        AND e.lockedUntil >= :now
        AND e.attemptCount >= e.maxAttempts
      """)
  int markFailedFinal(
      @Param("eventId") UUID eventId,
      @Param("dispatcherId") String dispatcherId,
      @Param("error") String error,
      @Param("now") LocalDateTime now);
}
