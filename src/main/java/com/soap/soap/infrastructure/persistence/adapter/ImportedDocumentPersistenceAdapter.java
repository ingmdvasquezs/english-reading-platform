package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.exception.DuplicateActiveDocumentSourceException;
import com.soap.soap.application.model.DocumentSectionNavigation;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.DocumentSection;
import com.soap.soap.domain.model.DocumentUnit;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.infrastructure.persistence.mapper.DocumentSectionEntityMapper;
import com.soap.soap.infrastructure.persistence.mapper.DocumentUnitEntityMapper;
import com.soap.soap.infrastructure.persistence.mapper.ImportedDocumentEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaDocumentProgressRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaDocumentSectionRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaDocumentUnitRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaImportedDocumentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ImportedDocumentPersistenceAdapter implements ImportedDocumentRepositoryPort {
  private final JpaImportedDocumentRepository documents;
  private final JpaDocumentSectionRepository sections;
  private final JpaDocumentUnitRepository units;
  private final JpaDocumentProgressRepository progress;
  private final ImportedDocumentEntityMapper documentMapper;
  private final DocumentSectionEntityMapper sectionMapper;
  private final DocumentUnitEntityMapper unitMapper;

  @Override
  @Transactional
  public ImportedDocument saveDocument(ImportedDocument document) {
    try {
      var entity = documentMapper.toEntity(document);
      boolean isNew;
      if (document.id() == null) {
        entity.setId(UUID.randomUUID());
        isNew = true;
      } else {
        isNew = !documents.existsById(document.id());
      }
      entity.setNew(isNew);

      if (document.importStatus() == DocumentImportStatus.FAILED) {
        entity.setDeduplicationSha256(null);
      } else if (isNew) {
        entity.setDeduplicationSha256(document.sourceSha256());
      } else {
        documents
            .findById(document.id())
            .ifPresentOrElse(
                existing -> entity.setDeduplicationSha256(existing.getDeduplicationSha256()),
                () -> entity.setDeduplicationSha256(document.sourceSha256()));
      }
      return documentMapper.toDomain(documents.saveAndFlush(entity));
    } catch (DataIntegrityViolationException exception) {
      if (hasConstraint(exception, "uk_imported_documents_user_active_source")) {
        throw new DuplicateActiveDocumentSourceException(
            "Active document source already exists for user", exception);
      }
      throw exception;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ImportedDocument> findDocumentById(UUID documentId) {
    return documents.findById(documentId).map(documentMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ImportedDocument> findDocumentByIdAndOwnerId(UUID documentId, UUID ownerId) {
    return documents.findByIdAndOwnerId(documentId, ownerId).map(documentMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ImportedDocument> findByOwnerAndSourceSha256AndStatusIn(
      UUID ownerId, String sourceSha256, java.util.Set<DocumentImportStatus> statuses) {
    return documents
        .findFirstByOwnerIdAndSourceSha256AndImportStatusInOrderByCreatedAtAsc(
            ownerId, sourceSha256, statuses)
        .map(documentMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<ImportedDocument> findDocumentsByOwner(UUID ownerId, PageRequest pageRequest) {
    var page =
        documents.findByOwnerIdAndImportStatusInOrderByCreatedAtDesc(
            ownerId,
            java.util.Set.of(DocumentImportStatus.PROCESSING, DocumentImportStatus.READY),
            org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size()));
    return new PageResult<>(
        page.getContent().stream().map(documentMapper::toDomain).toList(),
        pageRequest.page(),
        pageRequest.size(),
        page.getTotalElements());
  }

  @Override
  @Transactional
  public List<DocumentSection> saveSections(List<DocumentSection> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    java.util.Set<UUID> ids =
        values.stream()
            .map(DocumentSection::id)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    java.util.Set<UUID> existingIds =
        ids.isEmpty()
            ? java.util.Set.of()
            : new java.util.HashSet<>(
                sections.findAllById(ids).stream()
                    .map(
                        com.soap.soap.infrastructure.persistence.entity.DocumentSectionEntity
                            ::getId)
                    .toList());

    var entities =
        values.stream()
            .map(
                section -> {
                  var entity = sectionMapper.toEntity(section);
                  if (entity.getId() == null) {
                    entity.setId(UUID.randomUUID());
                    entity.setNew(true);
                  } else {
                    entity.setNew(!existingIds.contains(entity.getId()));
                  }
                  return entity;
                })
            .toList();

    return sections.saveAll(entities).stream().map(sectionMapper::toDomain).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<DocumentSection> findSections(UUID documentId) {
    return sections.findByDocumentIdOrderByOrdinalAsc(documentId).stream()
        .map(sectionMapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<DocumentSectionNavigation> findSectionNavigation(UUID documentId) {
    return sections.findNavigationByDocumentId(documentId).stream()
        .map(
            section ->
                new DocumentSectionNavigation(
                    section.getId(),
                    section.getOrdinal(),
                    section.getTitle(),
                    section.getFirstUnitId(),
                    section.getUnitCount()))
        .toList();
  }

  @Override
  @Transactional
  public List<DocumentUnit> saveUnits(List<DocumentUnit> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    java.util.Set<UUID> ids =
        values.stream()
            .map(DocumentUnit::id)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    java.util.Set<UUID> existingIds =
        ids.isEmpty()
            ? java.util.Set.of()
            : new java.util.HashSet<>(
                units.findAllById(ids).stream()
                    .map(com.soap.soap.infrastructure.persistence.entity.DocumentUnitEntity::getId)
                    .toList());

    var entities =
        values.stream()
            .map(
                unit -> {
                  var entity = unitMapper.toEntity(unit);
                  if (entity.getId() == null) {
                    entity.setId(UUID.randomUUID());
                    entity.setNew(true);
                  } else {
                    entity.setNew(!existingIds.contains(entity.getId()));
                  }
                  return entity;
                })
            .toList();

    return units.saveAll(entities).stream().map(unitMapper::toDomain).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<DocumentUnit> findUnits(UUID documentId) {
    return units.findByDocumentIdOrderByGlobalOrdinalAsc(documentId).stream()
        .map(unitMapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<DocumentUnit> findUnitsBySection(UUID documentId, UUID sectionId) {
    return units
        .findByDocumentIdAndSectionIdOrderBySectionOrdinalAsc(documentId, sectionId)
        .stream()
        .map(unitMapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DocumentUnit> findUnitByGlobalOrdinal(UUID documentId, int globalOrdinal) {
    return units
        .findByDocumentIdAndGlobalOrdinal(documentId, globalOrdinal)
        .map(unitMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DocumentUnit> findFirstUnit(UUID documentId) {
    return units.findFirstByDocumentIdOrderByGlobalOrdinalAsc(documentId).map(unitMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DocumentUnit> findUnitById(UUID documentId, UUID unitId) {
    return units.findByDocumentIdAndId(documentId, unitId).map(unitMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public long countSections(UUID documentId) {
    return sections.countByDocumentId(documentId);
  }

  @Override
  @Transactional(readOnly = true)
  public long countUnits(UUID documentId) {
    return units.countByDocumentId(documentId);
  }

  @Override
  @Transactional(readOnly = true)
  public long countUnitsBySection(UUID documentId, UUID sectionId) {
    return units.countByDocumentIdAndSectionId(documentId, sectionId);
  }

  @Override
  @Transactional
  public void deleteDocument(UUID documentId) {
    documents.deleteById(documentId);
  }

  @Override
  @Transactional
  public void replaceDocumentStructure(
      UUID documentId, List<DocumentSection> newSections, List<DocumentUnit> newUnits) {
    if (progress.existsByDocumentId(documentId)) {
      throw new IllegalStateException(
          "Cannot replace document structure: unexpected reading progress exists for document "
              + documentId);
    }
    units.deleteByDocumentId(documentId);
    sections.deleteByDocumentId(documentId);
    units.flush();
    sections.flush();
    if (newSections != null && !newSections.isEmpty()) {
      saveSections(newSections);
    }
    if (newUnits != null && !newUnits.isEmpty()) {
      saveUnits(newUnits);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.Set<String> findReferencedAssetKeys() {
    return documents.findAll().stream()
        .flatMap(
            document ->
                java.util.stream.Stream.of(
                    document.getSourceAssetKey(), document.getCoverAssetKey()))
        .filter(java.util.Objects::nonNull)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private boolean hasConstraint(Throwable exception, String constraint) {
    boolean foundStructuredConstraintName = false;
    for (var cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation
          && violation.getConstraintName() != null) {
        foundStructuredConstraintName = true;
        var name = violation.getConstraintName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex >= 0) {
          name = name.substring(dotIndex + 1);
        }
        if (name.equalsIgnoreCase(constraint)) {
          return true;
        }
      }
    }
    if (foundStructuredConstraintName) {
      return false;
    }
    var pattern =
        java.util.regex.Pattern.compile(
            "\\b" + java.util.regex.Pattern.quote(constraint) + "\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    for (var cause = exception; cause != null; cause = cause.getCause()) {
      if (cause.getMessage() != null && pattern.matcher(cause.getMessage()).find()) {
        return true;
      }
    }
    return false;
  }
}
