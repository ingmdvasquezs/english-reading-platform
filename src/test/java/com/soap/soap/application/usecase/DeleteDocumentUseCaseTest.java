package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.DocumentNotFoundException;
import com.soap.soap.application.exception.DocumentStillProcessingException;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.DocumentAssetStoragePort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.ImportedDocument;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteDocumentUseCaseTest {
  @Mock CurrentUserPort currentUser;
  @Mock ImportedDocumentRepositoryPort documents;
  @Mock DocumentAssetStoragePort storage;
  private UUID userId;
  private SimpleMeterRegistry meters;
  private DeleteDocumentUseCase useCase;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    meters = new SimpleMeterRegistry();
    useCase = new DeleteDocumentUseCase(currentUser, documents, storage, meters);
    when(currentUser.requireUserId()).thenReturn(userId);
  }

  @Test
  void deletesOwnedReadyDocumentThenItsSourceAndCover() {
    var document = document(userId, DocumentImportStatus.READY, "source", "cover");
    when(documents.findDocumentById(document.id())).thenReturn(Optional.of(document));

    useCase.delete(document.id());

    var ordered = org.mockito.Mockito.inOrder(documents, storage);
    ordered.verify(documents).deleteDocument(document.id());
    ordered.verify(storage).delete("source");
    ordered.verify(storage).delete("cover");
    assertThat(meters.find("documents.deletes").tag("outcome", "deleted").counter().count())
        .isEqualTo(1);
  }

  @Test
  void deletesFailedDocumentWithMissingAssets() {
    var document = document(userId, DocumentImportStatus.FAILED, null, null);
    when(documents.findDocumentById(document.id())).thenReturn(Optional.of(document));

    useCase.delete(document.id());

    verify(documents).deleteDocument(document.id());
    verify(storage, never()).delete(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void deletesOwnedReadyEpubWithoutCover() {
    var document =
        document(userId, DocumentImportStatus.READY, "source-epub", null, DocumentFormat.EPUB);
    when(documents.findDocumentById(document.id())).thenReturn(Optional.of(document));

    useCase.delete(document.id());

    verify(documents).deleteDocument(document.id());
    verify(storage).delete("source-epub");
  }

  @Test
  void blocksProcessingWithoutDeletingAnything() {
    var document = document(userId, DocumentImportStatus.PROCESSING, "source", null);
    when(documents.findDocumentById(document.id())).thenReturn(Optional.of(document));

    assertThatThrownBy(() -> useCase.delete(document.id()))
        .isInstanceOf(DocumentStillProcessingException.class);
    verify(documents, never()).deleteDocument(document.id());
    verify(storage, never()).delete(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void hidesForeignAndMissingDocuments() {
    var foreign = document(UUID.randomUUID(), DocumentImportStatus.READY, "source", null);
    when(documents.findDocumentById(foreign.id())).thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> useCase.delete(foreign.id()))
        .isInstanceOf(DocumentNotFoundException.class);
    var missingId = UUID.randomUUID();
    assertThatThrownBy(() -> useCase.delete(missingId))
        .isInstanceOf(DocumentNotFoundException.class);
    verify(documents, never()).deleteDocument(foreign.id());
  }

  @Test
  void storageCleanupFailureDoesNotResurrectCommittedDocument() {
    var document = document(userId, DocumentImportStatus.READY, "source", null);
    when(documents.findDocumentById(document.id())).thenReturn(Optional.of(document));
    org.mockito.Mockito.doThrow(new RuntimeException("storage unavailable"))
        .when(storage)
        .delete("source");

    useCase.delete(document.id());

    verify(documents).deleteDocument(document.id());
    assertThat(meters.find("documents.deletes").tag("outcome", "cleanup_failed").counter().count())
        .isEqualTo(1);
  }

  private ImportedDocument document(
      UUID owner, DocumentImportStatus status, String source, String cover) {
    return document(owner, status, source, cover, DocumentFormat.PDF);
  }

  private ImportedDocument document(
      UUID owner, DocumentImportStatus status, String source, String cover, DocumentFormat format) {
    var now = LocalDateTime.now();
    return new ImportedDocument(
        UUID.randomUUID(),
        owner,
        "Book",
        null,
        "en",
        format,
        cover,
        source,
        "book.pdf",
        "a".repeat(64),
        status,
        status == DocumentImportStatus.FAILED ? "LANGUAGE_REQUIRED" : null,
        3,
        now,
        now);
  }
}
