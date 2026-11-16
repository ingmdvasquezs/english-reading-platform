package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.port.out.StaleImportRepositoryPort;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stale-import recovery SQL, kept apart from the worker/outbox repositories so their lease and
 * fencing queries stay untouched. Every UPDATE repeats its eligibility predicate (status plus age
 * or lease cut-off) so a concurrent change between the candidate scan and the update is never
 * overwritten. {@code import_jobs.version} is bumped so JPA optimistic locking notices.
 */
@Component
@RequiredArgsConstructor
public class StaleImportPersistenceAdapter implements StaleImportRepositoryPort {
  private final NamedParameterJdbcTemplate jdbc;

  @Override
  @Transactional(readOnly = true)
  public List<StaleDurableImport> findStaleDurableImports(
      LocalDateTime leaseEndedBefore, int limit) {
    return jdbc.query(
        """
        SELECT id, document_id, user_id
        FROM import_jobs
        WHERE status = 'PROCESSING' AND COALESCE(lease_until, updated_at) < :cutoff
        ORDER BY COALESCE(lease_until, updated_at)
        LIMIT :limit
        """,
        new MapSqlParameterSource()
            .addValue("cutoff", Timestamp.valueOf(leaseEndedBefore))
            .addValue("limit", limit),
        (rs, row) ->
            new StaleDurableImport(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getObject("user_id", UUID.class)));
  }

  @Override
  @Transactional
  public boolean failStaleDurableJob(
      UUID jobId,
      LocalDateTime leaseEndedBefore,
      LocalDateTime now,
      String errorCode,
      String errorMessage) {
    return jdbc.update(
            """
            UPDATE import_jobs
            SET status = 'FAILED',
                finished_at = :now,
                last_error_code = :errorCode,
                last_error_message = :errorMessage,
                updated_at = :now,
                version = version + 1
            WHERE id = :id
              AND status = 'PROCESSING'
              AND COALESCE(lease_until, updated_at) < :cutoff
            """,
            new MapSqlParameterSource()
                .addValue("id", jobId)
                .addValue("cutoff", Timestamp.valueOf(leaseEndedBefore))
                .addValue("now", Timestamp.valueOf(now))
                .addValue("errorCode", errorCode)
                .addValue("errorMessage", errorMessage))
        == 1;
  }

  @Override
  @Transactional
  public boolean failProcessingDocumentWithoutActiveJob(UUID documentId, LocalDateTime now) {
    return jdbc.update(
            """
            UPDATE imported_documents d
            SET import_status = 'FAILED',
                failure_reason = 'IMPORT_FAILURE',
                deduplication_sha256 = NULL,
                updated_at = :now
            WHERE d.id = :id
              AND d.import_status = 'PROCESSING'
              AND NOT EXISTS (
                  SELECT 1 FROM import_jobs j
                  WHERE j.document_id = d.id AND j.status IN ('PENDING', 'PROCESSING'))
            """,
            new MapSqlParameterSource()
                .addValue("id", documentId)
                .addValue("now", Timestamp.valueOf(now)))
        == 1;
  }

  @Override
  @Transactional(readOnly = true)
  public List<StaleLegacyImport> findStaleLegacyImports(LocalDateTime updatedBefore, int limit) {
    return jdbc.query(
        """
        SELECT d.id, d.user_id
        FROM imported_documents d
        WHERE d.import_status = 'PROCESSING'
          AND d.source_storage_provider = 'FILESYSTEM'
          AND d.updated_at < :cutoff
          AND NOT EXISTS (SELECT 1 FROM import_jobs j WHERE j.document_id = d.id)
        ORDER BY d.updated_at
        LIMIT :limit
        """,
        new MapSqlParameterSource()
            .addValue("cutoff", Timestamp.valueOf(updatedBefore))
            .addValue("limit", limit),
        (rs, row) ->
            new StaleLegacyImport(
                rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class)));
  }

  @Override
  @Transactional
  public boolean failStaleLegacyImport(
      UUID documentId, LocalDateTime updatedBefore, LocalDateTime now) {
    return jdbc.update(
            """
            UPDATE imported_documents d
            SET import_status = 'FAILED',
                failure_reason = 'IMPORT_FAILURE',
                deduplication_sha256 = NULL,
                updated_at = :now
            WHERE d.id = :id
              AND d.import_status = 'PROCESSING'
              AND d.source_storage_provider = 'FILESYSTEM'
              AND d.updated_at < :cutoff
              AND NOT EXISTS (SELECT 1 FROM import_jobs j WHERE j.document_id = d.id)
            """,
            new MapSqlParameterSource()
                .addValue("id", documentId)
                .addValue("cutoff", Timestamp.valueOf(updatedBefore))
                .addValue("now", Timestamp.valueOf(now)))
        == 1;
  }
}
