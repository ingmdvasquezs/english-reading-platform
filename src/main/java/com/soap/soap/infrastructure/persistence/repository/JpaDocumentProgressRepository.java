package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.DocumentProgressEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaDocumentProgressRepository extends JpaRepository<DocumentProgressEntity, UUID> {
  Optional<DocumentProgressEntity> findByUserIdAndDocumentId(UUID userId, UUID documentId);
}
