package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.UpdateDocumentProgressCommand;
import com.soap.soap.application.exception.DocumentProgressConflictException;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.DocumentProgressRepositoryPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.DocumentProgress;
import com.soap.soap.domain.model.DocumentUnit;
import com.soap.soap.domain.model.DocumentUnitKind;
import com.soap.soap.domain.model.ImportedDocument;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentProgressUseCaseTest {
  @Mock CurrentUserPort currentUser;
  @Mock ImportedDocumentRepositoryPort documents;
  @Mock DocumentProgressRepositoryPort repository;
  @Mock DocumentQueryUseCase queries;
  private DocumentProgressUseCase useCase;
  private UUID userId;
  private UUID documentId;
  private UUID unitId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    documentId = UUID.randomUUID();
    unitId = UUID.randomUUID();
    useCase =
        new DocumentProgressUseCase(
            currentUser,
            documents,
            repository,
            queries,
            Clock.fixed(Instant.parse("2026-09-07T19:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void absenceIsNotStartedWithoutCreatingARow() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(repository.findByUserIdAndDocumentId(userId, documentId)).thenReturn(Optional.empty());
    assertThat(useCase.get(documentId).status()).isEqualTo("NOT_STARTED");
  }

  @Test
  void startsAndCompletesExplicitly() {
    var document = document();
    when(currentUser.requireUserId()).thenReturn(userId);
    when(queries.owned(documentId, userId)).thenReturn(document);
    when(documents.findUnitById(documentId, unitId)).thenReturn(Optional.of(unit()));
    when(repository.findByUserIdAndDocumentId(userId, documentId)).thenReturn(Optional.empty());
    when(repository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    assertThat(
            useCase
                .update(new UpdateDocumentProgressCommand(documentId, unitId, true, null))
                .status())
        .isEqualTo("COMPLETED");
  }

  @Test
  void rejectsStaleOptimisticVersionBeforeSave() {
    var document = document();
    var existing =
        new DocumentProgress(
            UUID.randomUUID(),
            userId,
            documentId,
            unitId,
            com.soap.soap.domain.model.DocumentProgressStatus.IN_PROGRESS,
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            3L);
    when(currentUser.requireUserId()).thenReturn(userId);
    when(queries.owned(documentId, userId)).thenReturn(document);
    when(documents.findUnitById(documentId, unitId)).thenReturn(Optional.of(unit()));
    when(repository.findByUserIdAndDocumentId(userId, documentId))
        .thenReturn(Optional.of(existing));
    assertThatThrownBy(
            () -> useCase.update(new UpdateDocumentProgressCommand(documentId, unitId, false, 2L)))
        .isInstanceOf(DocumentProgressConflictException.class);
  }

  private ImportedDocument document() {
    var now = LocalDateTime.now();
    return new ImportedDocument(
        documentId,
        userId,
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

  private DocumentUnit unit() {
    return new DocumentUnit(
        unitId,
        documentId,
        UUID.randomUUID(),
        1,
        1,
        DocumentUnitKind.LOGICAL_CHUNK,
        "Text",
        1,
        "chapter.xhtml",
        "b".repeat(64));
  }
}
