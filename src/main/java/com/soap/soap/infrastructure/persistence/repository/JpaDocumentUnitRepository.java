package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.DocumentUnitEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaDocumentUnitRepository extends JpaRepository<DocumentUnitEntity, UUID> {
  List<DocumentUnitEntity> findByDocumentIdOrderByGlobalOrdinalAsc(UUID documentId);

  List<DocumentUnitEntity> findByDocumentIdAndSectionIdOrderBySectionOrdinalAsc(
      UUID documentId, UUID sectionId);

  Optional<DocumentUnitEntity> findByDocumentIdAndGlobalOrdinal(UUID documentId, int globalOrdinal);

  Optional<DocumentUnitEntity> findFirstByDocumentIdOrderByGlobalOrdinalAsc(UUID documentId);

  Optional<DocumentUnitEntity> findByDocumentIdAndId(UUID documentId, UUID id);

  long countByDocumentId(UUID documentId);

  long countByDocumentIdAndSectionId(UUID documentId, UUID sectionId);
}
