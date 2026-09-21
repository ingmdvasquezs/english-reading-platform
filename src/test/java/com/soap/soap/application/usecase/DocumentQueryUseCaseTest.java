package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.DocumentNotFoundException;
import com.soap.soap.application.model.DocumentSectionNavigation;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.DocumentProgressRepositoryPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.service.ReaderContentPreparer;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.DocumentUnit;
import com.soap.soap.domain.model.DocumentUnitKind;
import com.soap.soap.domain.model.ImportedDocument;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentQueryUseCaseTest {
  @Mock CurrentUserPort currentUser;
  @Mock ImportedDocumentRepositoryPort documents;
  @Mock DocumentProgressRepositoryPort progress;
  @Mock ReaderContentPreparer readerContent;

  @Test
  void structureUsesRepositoryFirstUnitAndKeepsResponseSmall() {
    var ownerId = UUID.randomUUID();
    var documentId = UUID.randomUUID();
    var sectionId = UUID.randomUUID();
    var firstUnitId = UUID.randomUUID();
    when(currentUser.requireUserId()).thenReturn(ownerId);
    when(documents.findDocumentById(documentId))
        .thenReturn(Optional.of(document(documentId, ownerId)));
    when(documents.findSectionNavigation(documentId))
        .thenReturn(List.of(new DocumentSectionNavigation(sectionId, 1, "First", firstUnitId, 1)));
    when(documents.findFirstUnit(documentId))
        .thenReturn(Optional.of(unit(firstUnitId, documentId, sectionId)));

    var result = useCase().structure(documentId);

    assertThat(result.firstUnitId()).isEqualTo(firstUnitId);
    assertThat(result.sections()).hasSize(1);
    assertThat(result.sections().getFirst().firstUnitId()).isEqualTo(firstUnitId);
    assertThat(result.sections().getFirst().unitCount()).isEqualTo(1);
    assertThat(result.totalUnits()).isEqualTo(1);
    verify(documents, never()).findUnits(documentId);
  }

  @Test
  void structureAllowsNullFirstUnitDefensively() {
    var ownerId = UUID.randomUUID();
    var documentId = UUID.randomUUID();
    when(currentUser.requireUserId()).thenReturn(ownerId);
    when(documents.findDocumentById(documentId))
        .thenReturn(Optional.of(document(documentId, ownerId)));
    when(documents.findSectionNavigation(documentId)).thenReturn(List.of());
    when(documents.findFirstUnit(documentId)).thenReturn(Optional.empty());

    assertThat(useCase().structure(documentId).firstUnitId()).isNull();
  }

  @Test
  void structureNeverExposesTechnicalIdentifiersAsDisplayTitles() {
    var ownerId = UUID.randomUUID();
    var documentId = UUID.randomUUID();
    var unitId = UUID.randomUUID();
    when(currentUser.requireUserId()).thenReturn(ownerId);
    when(documents.findDocumentById(documentId))
        .thenReturn(Optional.of(document(documentId, ownerId)));
    when(documents.findSectionNavigation(documentId))
        .thenReturn(
            List.of(
                new DocumentSectionNavigation(
                    UUID.randomUUID(), 1, "id-idp140489238277312", unitId, 1),
                new DocumentSectionNavigation(UUID.randomUUID(), 2, "htmltoc", unitId, 1),
                new DocumentSectionNavigation(UUID.randomUUID(), 3, "Scene II", unitId, 1)));
    when(documents.findFirstUnit(documentId))
        .thenReturn(Optional.of(unit(unitId, documentId, UUID.randomUUID())));

    assertThat(useCase().structure(documentId).sections())
        .extracting(section -> section.title())
        .containsExactly(null, null, "Scene II");
  }

  @Test
  void unitNeverExposesTechnicalIdentifiersAsDisplayTitles() {
    var ownerId = UUID.randomUUID();
    var documentId = UUID.randomUUID();
    var sectionId = UUID.randomUUID();
    var unitId = UUID.randomUUID();
    when(currentUser.requireUserId()).thenReturn(ownerId);
    when(documents.findDocumentById(documentId))
        .thenReturn(Optional.of(document(documentId, ownerId)));
    when(documents.findUnitById(documentId, unitId))
        .thenReturn(Optional.of(unit(unitId, documentId, sectionId)));
    when(documents.findSections(documentId))
        .thenReturn(
            List.of(
                new com.soap.soap.domain.model.DocumentSection(
                    sectionId, documentId, 5, "id-idp140489363296560", "OEBPS/ch01s02.xhtml")));
    when(documents.countUnits(documentId)).thenReturn(5L);
    when(documents.countUnitsBySection(documentId, sectionId)).thenReturn(5L);
    when(progress.findByUserIdAndDocumentId(ownerId, documentId)).thenReturn(Optional.empty());

    var result = useCase().unit(documentId, unitId);

    assertThat(result.sectionTitle()).isNull();
    assertThat(result.sectionOrdinal()).isEqualTo(5);
    assertThat(result.sectionUnitOrdinal()).isEqualTo(1);
    assertThat(result.sectionUnitCount()).isEqualTo(5);
  }

  @Test
  void foreignOwnerCannotReachFirstUnitQuery() {
    var userId = UUID.randomUUID();
    var documentId = UUID.randomUUID();
    when(currentUser.requireUserId()).thenReturn(userId);
    when(documents.findDocumentById(documentId))
        .thenReturn(Optional.of(document(documentId, UUID.randomUUID())));

    assertThatThrownBy(() -> useCase().structure(documentId))
        .isInstanceOf(DocumentNotFoundException.class);
    verify(documents, never()).findFirstUnit(documentId);
    verify(documents, never()).findSectionNavigation(documentId);
  }

  private DocumentQueryUseCase useCase() {
    return new DocumentQueryUseCase(currentUser, documents, progress, readerContent);
  }

  private ImportedDocument document(UUID id, UUID ownerId) {
    var now = LocalDateTime.now();
    return new ImportedDocument(
        id,
        ownerId,
        "Title",
        null,
        "en",
        DocumentFormat.EPUB,
        null,
        "source",
        "book.epub",
        "a".repeat(64),
        DocumentImportStatus.READY,
        1,
        now,
        now);
  }

  private DocumentUnit unit(UUID id, UUID documentId, UUID sectionId) {
    return new DocumentUnit(
        id,
        documentId,
        sectionId,
        1,
        1,
        DocumentUnitKind.LOGICAL_CHUNK,
        "content",
        1,
        "chapter.xhtml",
        "b".repeat(64));
  }
}
