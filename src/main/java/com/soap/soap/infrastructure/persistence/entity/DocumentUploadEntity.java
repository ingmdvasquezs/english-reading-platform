package com.soap.soap.infrastructure.persistence.entity;

import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentUploadStatus;
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
@Table(name = "document_uploads")
public class DocumentUploadEntity {

  @Id private UUID id;

  @Column(name = "document_id", nullable = false)
  private UUID documentId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "original_filename", nullable = false, length = 500)
  private String originalFilename;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DocumentFormat format;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(name = "expected_size_bytes", nullable = false)
  private long expectedSizeBytes;

  @Column(name = "expected_checksum_sha256", length = 64)
  private String expectedChecksumSha256;

  @Column(name = "storage_key", nullable = false, length = 500)
  private String storageKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "storage_provider", nullable = false, length = 20)
  private com.soap.soap.domain.model.StorageProvider storageProvider;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DocumentUploadStatus status;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "confirmed_at")
  private LocalDateTime confirmedAt;

  @Column(name = "confirmed_job_id")
  private UUID confirmedJobId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Version
  @Column(nullable = false)
  private long version;
}
