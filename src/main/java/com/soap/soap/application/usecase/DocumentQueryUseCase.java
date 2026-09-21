package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.DocumentNotFoundException;
import com.soap.soap.application.exception.DocumentNotReadyException;
import com.soap.soap.application.exception.DocumentUnitNotFoundException;
import com.soap.soap.application.model.DocumentStructureView;
import com.soap.soap.application.model.DocumentUnitReaderData;
import com.soap.soap.application.model.DocumentView;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.DocumentProgressRepositoryPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.service.ReaderContentPreparer;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.domain.service.DocumentSectionTitleSanitizer;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DocumentQueryUseCase {
  private final CurrentUserPort currentUser;
  private final ImportedDocumentRepositoryPort documents;
  private final DocumentProgressRepositoryPort progress;
  private final ReaderContentPreparer readerContent;

  @Transactional(readOnly = true)
  public DocumentView get(UUID documentId) {
    var userId = currentUser.requireUserId();
    return view(owned(documentId, userId), userId);
  }

  @Transactional(readOnly = true)
  public PageResult<DocumentView> list(int page, int size) {
    var userId = currentUser.requireUserId();
    var result = documents.findDocumentsByOwner(userId, new PageRequest(page, size));
    return new PageResult<>(
        result.content().stream().map(document -> view(document, userId)).toList(),
        result.page(),
        result.size(),
        result.totalElements());
  }

  @Transactional(readOnly = true)
  public DocumentStructureView structure(UUID documentId) {
    var userId = currentUser.requireUserId();
    requireReady(owned(documentId, userId));
    var sections = documents.findSectionNavigation(documentId);
    return new DocumentStructureView(
        documentId,
        documents
            .findFirstUnit(documentId)
            .map(com.soap.soap.domain.model.DocumentUnit::id)
            .orElse(null),
        sections.stream()
            .map(
                section ->
                    new DocumentStructureView.Section(
                        section.id(),
                        section.ordinal(),
                        DocumentSectionTitleSanitizer.sanitize(section.title()),
                        section.firstUnitId(),
                        section.unitCount()))
            .toList(),
        sections.stream()
            .mapToLong(com.soap.soap.application.model.DocumentSectionNavigation::unitCount)
            .sum());
  }

  @Transactional(readOnly = true)
  public DocumentUnitReaderData unit(UUID documentId, UUID unitId) {
    var userId = currentUser.requireUserId();
    var document = owned(documentId, userId);
    requireReady(document);
    var unit =
        documents
            .findUnitById(documentId, unitId)
            .orElseThrow(() -> new DocumentUnitNotFoundException(unitId));
    var sections = documents.findSections(documentId);
    var section =
        sections.stream()
            .filter(value -> value.id().equals(unit.sectionId()))
            .findFirst()
            .orElseThrow(() -> new DocumentUnitNotFoundException(unitId));
    int totalUnits = Math.toIntExact(documents.countUnits(documentId));
    var previous =
        unit.globalOrdinal() == 1
            ? null
            : documents
                .findUnitByGlobalOrdinal(documentId, unit.globalOrdinal() - 1)
                .map(com.soap.soap.domain.model.DocumentUnit::id)
                .orElse(null);
    var next =
        unit.globalOrdinal() == totalUnits
            ? null
            : documents
                .findUnitByGlobalOrdinal(documentId, unit.globalOrdinal() + 1)
                .map(com.soap.soap.domain.model.DocumentUnit::id)
                .orElse(null);
    var status =
        progress
            .findByUserIdAndDocumentId(userId, documentId)
            .map(com.soap.soap.domain.model.DocumentProgress::status)
            .orElse(null);
    return new DocumentUnitReaderData(
        documentId,
        section.id(),
        DocumentSectionTitleSanitizer.sanitize(section.title()),
        section.ordinal(),
        sections.size(),
        unit.id(),
        unit.sectionOrdinal(),
        Math.toIntExact(documents.countUnitsBySection(documentId, section.id())),
        unit.globalOrdinal(),
        totalUnits,
        previous,
        next,
        unit.content(),
        readerContent.prepare(userId, document.language(), unit.content()),
        status);
  }

  ImportedDocument owned(UUID documentId, UUID userId) {
    return documents
        .findDocumentById(documentId)
        .filter(document -> document.ownerId().equals(userId))
        .orElseThrow(() -> new DocumentNotFoundException(documentId));
  }

  void requireReady(ImportedDocument document) {
    if (document.importStatus() != DocumentImportStatus.READY)
      throw new DocumentNotReadyException();
  }

  private DocumentView view(ImportedDocument document, UUID userId) {
    var current = progress.findByUserIdAndDocumentId(userId, document.id()).orElse(null);
    boolean ready = document.importStatus() == DocumentImportStatus.READY;
    return new DocumentView(
        document.id(),
        document.title(),
        document.author(),
        document.language(),
        document.format(),
        document.importStatus(),
        document.failureReason(),
        document.coverAssetKey() != null,
        ready ? documents.countSections(document.id()) : 0,
        ready ? documents.countUnits(document.id()) : 0,
        current == null ? null : current.status(),
        current == null ? null : current.lastReadAt(),
        document.createdAt(),
        document.updatedAt());
  }
}
