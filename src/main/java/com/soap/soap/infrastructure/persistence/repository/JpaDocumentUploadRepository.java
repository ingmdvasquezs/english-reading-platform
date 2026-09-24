package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.DocumentUploadEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaDocumentUploadRepository extends JpaRepository<DocumentUploadEntity, UUID> {
  Optional<DocumentUploadEntity> findByDocumentId(UUID documentId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT u FROM DocumentUploadEntity u WHERE u.id = :id")
  Optional<DocumentUploadEntity> findByIdForUpdate(@Param("id") UUID id);
}
