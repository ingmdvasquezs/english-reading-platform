package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.DocumentAlreadyImportedException;
import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.exception.DuplicateActiveDocumentSourceException;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.DocumentAssetStoragePort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.service.DocumentChunker;
import com.soap.soap.application.service.DocumentLanguagePolicy;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.domain.model.User;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AcceptDocumentImportUseCaseTest {
  @Mock CurrentUserPort currentUser;
  @Mock UserRepositoryPort users;
  @Mock ImportedDocumentRepositoryPort documents;
  @Mock DocumentAssetStoragePort storage;
  @Mock ImportEpubUseCase processor;
  private UUID userId;
  private AtomicReference<Runnable> submitted;
  private SimpleMeterRegistry meters;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    submitted = new AtomicReference<>();
    meters = new SimpleMeterRegistry();
    when(currentUser.requireUserId()).thenReturn(userId);
    when(users.findById(userId))
        .thenReturn(Optional.of(new User(userId, "Owner", "owner@example.com", "hash", null)));
    org.mockito.Mockito.lenient()
        .when(documents.saveDocument(any()))
        .thenAnswer(
            invocation -> {
              var value = invocation.<ImportedDocument>getArgument(0);
              return value.id() == null ? withId(value, UUID.randomUUID()) : value;
            });
    org.mockito.Mockito.lenient()
        .when(storage.storeSource(any(), any(), any()))
        .thenReturn("documents/source.epub");
  }

  @Test
  void acceptanceCreatesProcessingBeforeWorkerRuns(@TempDir Path directory) throws Exception {
    var source = Files.writeString(directory.resolve("book.epub"), "source");
    var useCase = useCase(submitted::set);

    var accepted = useCase.accept(source, "../book.epub", "en");

    assertThat(accepted.status()).isEqualTo(DocumentImportStatus.PROCESSING);
    assertThat(accepted.documentId()).isNotNull();
    assertThat(submitted.get()).isNotNull();
    verify(documents).saveDocument(argThat(document -> document.id() == null));
    verify(storage).storeSource(userId, accepted.documentId(), source, DocumentFormat.EPUB);
    verify(processor, org.mockito.Mockito.never()).processAccepted(any(), any(), any());
    submitted.get().run();
    verify(processor)
        .processAccepted(
            any(ImportedDocument.class),
            org.mockito.ArgumentMatchers.eq("en"),
            any(DocumentLanguagePolicy.class));
  }

  @Test
  void pdfAcceptancePersistsFormatAndUsesFormatAwareStorage(@TempDir Path directory)
      throws Exception {
    var source = Files.writeString(directory.resolve("paper.pdf"), "%PDF-1.7");
    var useCase = useCase(submitted::set);

    var accepted =
        useCase.accept(source, "english_reading_sample_5_pages.pdf", null, DocumentFormat.PDF);

    verify(documents, org.mockito.Mockito.atLeastOnce())
        .saveDocument(
            argThat(
                document ->
                    document.format() == DocumentFormat.PDF
                        && document.title().equals("English reading sample 5 pages")));
    verify(storage).storeSource(userId, accepted.documentId(), source, DocumentFormat.PDF);
  }

  @Test
  void workerFailureIsContainedAndMetered(@TempDir Path directory) throws Exception {
    var source = Files.writeString(directory.resolve("book.epub"), "source");
    when(processor.processAccepted(any(), any(), any()))
        .thenThrow(
            new DocumentImportException(DocumentImportException.Reason.INVALID_EPUB, "invalid"));
    var useCase = useCase(submitted::set);

    useCase.accept(source, "book.epub", null);
    submitted.get().run();

    assertThat(
            meters
                .find("documents.imports")
                .tag("outcome", "failed")
                .tag("reason", "INVALID_EPUB")
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void readyDuplicateIsRejectedBeforeCreatingRowOrStoringSource(@TempDir Path directory)
      throws Exception {
    var source = Files.writeString(directory.resolve("book.epub"), "same source");
    when(documents.findByOwnerAndSourceSha256AndStatusIn(any(), any(), any()))
        .thenReturn(Optional.of(existing(DocumentImportStatus.READY)));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> useCase(submitted::set).accept(source, "book.epub", null))
        .isInstanceOf(DocumentAlreadyImportedException.class);

    verify(storage, never()).storeSource(any(), any(), any());
    verify(documents, never()).saveDocument(any());
  }

  @Test
  void processingDuplicateIsRejected(@TempDir Path directory) throws Exception {
    var source = Files.writeString(directory.resolve("book.epub"), "same source");
    when(documents.findByOwnerAndSourceSha256AndStatusIn(any(), any(), any()))
        .thenReturn(Optional.of(existing(DocumentImportStatus.PROCESSING)));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> useCase(submitted::set).accept(source, "book.epub", null))
        .isInstanceOfSatisfying(
            DocumentAlreadyImportedException.class,
            exception -> assertThat(exception.status()).isEqualTo(DocumentImportStatus.PROCESSING));
  }

  @Test
  void failedDocumentDoesNotBlockRetry(@TempDir Path directory) throws Exception {
    var source = Files.writeString(directory.resolve("book.epub"), "same source");
    when(documents.findByOwnerAndSourceSha256AndStatusIn(any(), any(), any()))
        .thenReturn(Optional.empty());

    var accepted = useCase(submitted::set).accept(source, "book.epub", null);

    assertThat(accepted.status()).isEqualTo(DocumentImportStatus.PROCESSING);
    verify(storage).storeSource(userId, accepted.documentId(), source, DocumentFormat.EPUB);
  }

  @Test
  void concurrentInsertConflictIsMappedAndNeverStoresSecondSource(@TempDir Path directory)
      throws Exception {
    var source = Files.writeString(directory.resolve("book.epub"), "same source");
    var winner = existing(DocumentImportStatus.PROCESSING);
    when(documents.findByOwnerAndSourceSha256AndStatusIn(any(), any(), any()))
        .thenReturn(Optional.empty(), Optional.of(winner));
    org.mockito.Mockito.doThrow(new DuplicateActiveDocumentSourceException("unique active source"))
        .when(documents)
        .saveDocument(any());

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> useCase(submitted::set).accept(source, "book.epub", null))
        .isInstanceOfSatisfying(
            DocumentAlreadyImportedException.class,
            exception -> assertThat(exception.existingDocumentId()).isEqualTo(winner.id()));
    verify(storage, never()).storeSource(any(), any(), any());
  }

  @Test
  void nonDuplicatePersistenceExceptionPropagatesAndNeverStoresSource(@TempDir Path directory)
      throws Exception {
    var source = Files.writeString(directory.resolve("book.epub"), "same source");
    org.mockito.Mockito.doThrow(new IllegalStateException("foreign key or db error"))
        .when(documents)
        .saveDocument(any());

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> useCase(submitted::set).accept(source, "book.epub", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("foreign key or db error");
    verify(storage, never()).storeSource(any(), any(), any());
  }

  private ImportedDocument existing(DocumentImportStatus status) {
    var now = java.time.LocalDateTime.now();
    return new ImportedDocument(
        UUID.randomUUID(),
        userId,
        "Book",
        null,
        "en",
        com.soap.soap.domain.model.DocumentFormat.EPUB,
        null,
        "source",
        "book.epub",
        "a".repeat(64),
        status,
        DocumentChunker.VERSION,
        now,
        now);
  }

  private AcceptDocumentImportUseCase useCase(Executor executor) {
    return new AcceptDocumentImportUseCase(
        currentUser,
        users,
        documents,
        storage,
        processor,
        new DocumentLanguagePolicy(Set.of("en")),
        executor,
        meters,
        Clock.systemUTC());
  }

  private ImportedDocument withId(ImportedDocument value, UUID id) {
    return new ImportedDocument(
        id,
        value.ownerId(),
        value.title(),
        value.author(),
        value.language(),
        value.format(),
        value.coverAssetKey(),
        value.sourceAssetKey(),
        value.originalFilename(),
        value.sourceSha256(),
        value.importStatus(),
        value.failureReason(),
        value.chunkingVersion(),
        value.createdAt(),
        value.updatedAt());
  }
}
