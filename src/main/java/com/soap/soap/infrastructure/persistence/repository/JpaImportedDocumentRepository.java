package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.infrastructure.persistence.entity.ImportedDocumentEntity;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaImportedDocumentRepository extends JpaRepository<ImportedDocumentEntity, UUID> {
  Page<ImportedDocumentEntity> findByOwnerIdAndImportStatusInOrderByCreatedAtDesc(
      UUID ownerId, Set<DocumentImportStatus> statuses, Pageable pageable);

  Optional<ImportedDocumentEntity>
      findFirstByOwnerIdAndSourceSha256AndImportStatusInOrderByCreatedAtAsc(
          UUID ownerId, String sourceSha256, Set<DocumentImportStatus> statuses);
}
