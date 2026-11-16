package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.port.out.ExpiredUploadRepositoryPort;
import com.soap.soap.domain.model.StorageProvider;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upload-intent cleanup SQL. The status update bumps {@code version} so JPA optimistic locking on
 * {@code DocumentUploadEntity} notices the change.
 */
@Component
@RequiredArgsConstructor
public class ExpiredUploadPersistenceAdapter implements ExpiredUploadRepositoryPort {
  private static final String COLUMNS = "id, user_id, document_id, storage_key, storage_provider";

  private final NamedParameterJdbcTemplate jdbc;

  @Override
  @Transactional(readOnly = true)
  public List<ExpiredUpload> findExpiredPendingUploads(LocalDateTime expiredBefore, int limit) {
    return jdbc.query(
        "SELECT "
            + COLUMNS
            + " FROM document_uploads WHERE status = 'PENDING' AND expires_at < :expiredBefore"
            + " ORDER BY expires_at LIMIT :limit",
        new MapSqlParameterSource()
            .addValue("expiredBefore", Timestamp.valueOf(expiredBefore))
            .addValue("limit", limit),
        this::expiredUpload);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<ExpiredUpload> lockExpiredPendingUpload(
      UUID uploadId, LocalDateTime expiredBefore) {
    return jdbc
        .query(
            "SELECT "
                + COLUMNS
                + " FROM document_uploads WHERE id = :id AND status = 'PENDING'"
                + " AND expires_at < :expiredBefore FOR UPDATE SKIP LOCKED",
            new MapSqlParameterSource()
                .addValue("id", uploadId)
                .addValue("expiredBefore", Timestamp.valueOf(expiredBefore)),
            this::expiredUpload)
        .stream()
        .findFirst();
  }

  @Override
  @Transactional
  public boolean markUploadExpired(UUID uploadId, LocalDateTime now) {
    return jdbc.update(
            "UPDATE document_uploads SET status = 'EXPIRED', updated_at = :now,"
                + " version = version + 1 WHERE id = :id AND status = 'PENDING'",
            new MapSqlParameterSource()
                .addValue("id", uploadId)
                .addValue("now", Timestamp.valueOf(now)))
        == 1;
  }

  private ExpiredUpload expiredUpload(ResultSet rs, int row) throws SQLException {
    return new ExpiredUpload(
        rs.getObject("id", UUID.class),
        rs.getObject("user_id", UUID.class),
        rs.getObject("document_id", UUID.class),
        rs.getString("storage_key"),
        StorageProvider.valueOf(rs.getString("storage_provider")));
  }
}
