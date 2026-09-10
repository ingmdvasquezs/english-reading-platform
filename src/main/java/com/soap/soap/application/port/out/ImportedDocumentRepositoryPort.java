package com.soap.soap.application.port.out;

import com.soap.soap.application.model.DocumentSectionNavigation;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.DocumentSection;
import com.soap.soap.domain.model.DocumentUnit;
import com.soap.soap.domain.model.ImportedDocument;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ImportedDocumentRepositoryPort {
  ImportedDocument saveDocument(ImportedDocument document);

  Optional<ImportedDocument> findDocumentById(UUID documentId);

  Optional<ImportedDocument> findByOwnerAndSourceSha256AndStatusIn(
      UUID ownerId, String sourceSha256, Set<DocumentImportStatus> statuses);

  PageResult<ImportedDocument> findDocumentsByOwner(UUID ownerId, PageRequest pageRequest);

  List<DocumentSection> saveSections(List<DocumentSection> sections);

  List<DocumentSection> findSections(UUID documentId);

  List<DocumentSectionNavigation> findSectionNavigation(UUID documentId);

  List<DocumentUnit> saveUnits(List<DocumentUnit> units);

  List<DocumentUnit> findUnits(UUID documentId);

  List<DocumentUnit> findUnitsBySection(UUID documentId, UUID sectionId);

  Optional<DocumentUnit> findUnitByGlobalOrdinal(UUID documentId, int globalOrdinal);

  Optional<DocumentUnit> findFirstUnit(UUID documentId);

  Optional<DocumentUnit> findUnitById(UUID documentId, UUID unitId);

  long countSections(UUID documentId);

  long countUnits(UUID documentId);

  long countUnitsBySection(UUID documentId, UUID sectionId);

  void deleteDocument(UUID documentId);

  Set<String> findReferencedAssetKeys();
}
