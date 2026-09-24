package com.soap.soap.infrastructure.persistence.entity;

import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "imported_documents")
public class ImportedDocumentEntity implements Persistable<UUID> {
  @Id private UUID id;

  @Transient private boolean isNew = true;

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }

  @Column(name = "user_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false, length = 300)
  private String title;

  @Column(length = 300)
  private String author;

  @Column(nullable = false, length = 50)
  private String language;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DocumentFormat format;

  @Enumerated(EnumType.STRING)
  @Column(name = "import_status", nullable = false, length = 20)
  private DocumentImportStatus importStatus;

  @Column(name = "failure_reason", length = 50)
  private String failureReason;

  @Column(name = "cover_asset_key", length = 500)
  private String coverAssetKey;

  @Column(name = "source_asset_key", length = 500)
  private String sourceAssetKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_storage_provider", nullable = false, length = 20)
  private com.soap.soap.domain.model.StorageProvider sourceStorageProvider;

  @Column(name = "original_filename", length = 500)
  private String originalFilename;

  @Column(name = "source_sha256", nullable = false, columnDefinition = "char(64)")
  @JdbcTypeCode(SqlTypes.CHAR)
  private String sourceSha256;

  @Column(name = "deduplication_sha256", columnDefinition = "char(64)")
  @JdbcTypeCode(SqlTypes.CHAR)
  private String deduplicationSha256;

  @Column(name = "chunking_version", nullable = false)
  private int chunkingVersion;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
