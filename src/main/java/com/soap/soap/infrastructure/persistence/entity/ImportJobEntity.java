package com.soap.soap.infrastructure.persistence.entity;

import com.soap.soap.domain.model.ImportJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "import_jobs")
public class ImportJobEntity {

  @Id private UUID id;

  @Column(name = "document_id", nullable = false)
  private UUID documentId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ImportJobStatus status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts;

  @Column(name = "source_asset_key", nullable = false, length = 500)
  private String sourceAssetKey;

  @Column(name = "language_override", length = 50)
  private String languageOverride;

  @Column(name = "worker_id", length = 100)
  private String workerId;

  @Column(name = "lease_token")
  private UUID leaseToken;

  @Column(name = "lease_until")
  private LocalDateTime leaseUntil;

  @Column(name = "next_attempt_at")
  private LocalDateTime nextAttemptAt;

  @Column(name = "heartbeat_at")
  private LocalDateTime heartbeatAt;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "finished_at")
  private LocalDateTime finishedAt;

  @Column(name = "last_error_code", length = 50)
  private String lastErrorCode;

  @Column(name = "last_error_message", columnDefinition = "TEXT")
  private String lastErrorMessage;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Version
  @Column(nullable = false)
  private long version;
}
