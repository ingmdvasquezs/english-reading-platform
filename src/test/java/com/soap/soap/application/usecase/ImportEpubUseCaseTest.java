package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.ImportEpubCommand;
import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.exception.DocumentImportException.Reason;
import com.soap.soap.application.model.ParsedDocument;
import com.soap.soap.application.model.ParsedDocumentCover;
import com.soap.soap.application.model.ParsedDocumentSection;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.DocumentAssetStoragePort;
import com.soap.soap.application.port.out.DocumentParserPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.service.DocumentChunker;
import com.soap.soap.application.service.DocumentLanguageResolver;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.DocumentSection;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.domain.model.User;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportEpubUseCaseTest {
  @TempDir Path directory;
  @Mock CurrentUserPort currentUser;
  @Mock UserRepositoryPort users;
  @Mock DocumentParserPort parser;
  @Mock DocumentAssetStoragePort storage;
  @Mock ImportedDocumentRepositoryPort documents;

  private UUID userId;
  private ImportEpubUseCase useCase;
  private Path source;

  @BeforeEach
  void setUp() throws Exception {
    userId = UUID.randomUUID();
    source = directory.resolve("input.epub");
    Files.writeString(source, "streamed source");
    useCase =
        new ImportEpubUseCase(
            currentUser,
            users,
            parser,
            storage,
            documents,
            new DocumentLanguageResolver(),
            new DocumentChunker(),
            Clock.fixed(Instant.parse("2026-09-07T15:00:00Z"), ZoneOffset.UTC));
    when(currentUser.requireUserId()).thenReturn(userId);
    when(users.findById(userId))
        .thenReturn(Optional.of(new User(userId, "Owner", "owner@example.com", "hash", null)));
  }

  @Test
  void importsSourceCoverSectionsAndChunksThenMarksReady() {
    when(parser.parse(any(), any())).thenReturn(parsed("en-US", true));
    var saves = new AtomicInteger();
    when(documents.saveDocument(any()))
        .thenAnswer(
            invocation -> {
              var value = invocation.<ImportedDocument>getArgument(0);
              if (saves.getAndIncrement() == 0) return withId(value, UUID.randomUUID());
              return value;
            });
    when(storage.storeSource(any(), any(), any())).thenReturn("documents/u/d/source.epub");
    when(storage.storeCover(any(), any(), any(), any())).thenReturn("documents/u/d/cover.png");
    org.mockito.Mockito.lenient()
        .when(documents.saveSections(any()))
        .thenAnswer(
            invocation ->
                invocation.<List<DocumentSection>>getArgument(0).stream()
                    .map(
                        section ->
                            new DocumentSection(
                                UUID.randomUUID(),
                                section.documentId(),
                                section.ordinal(),
                                section.title(),
                                section.sourceLocator()))
                    .toList());

    var imported = useCase.importEpub(new ImportEpubCommand(source, "../client-name.epub", null));

    assertThat(imported.importStatus()).isEqualTo(DocumentImportStatus.READY);
    assertThat(imported.language()).isEqualTo("en-US");
    assertThat(imported.sourceSha256()).hasSize(64);
    assertThat(imported.sourceAssetKey()).endsWith("source.epub");
    assertThat(imported.coverAssetKey()).endsWith("cover.png");
    verify(documents).saveUnits(any());
    var order = inOrder(documents, storage);
    order.verify(documents).saveDocument(any());
    order.verify(storage).storeSource(any(), any(), any());
    order.verify(storage).storeCover(any(), any(), any(), any());
    order.verify(documents).saveSections(any());
    order.verify(documents).saveUnits(any());
    order.verify(documents).saveDocument(any());
  }

  @Test
  void languageOverrideAllowsImportWhenMetadataHasNoLanguageAndCoverIsOptional() {
    when(parser.parse(any(), any())).thenReturn(parsed(null, false));
    stubPersistence();
    when(storage.storeSource(any(), any(), any())).thenReturn("source");

    var imported = useCase.importEpub(new ImportEpubCommand(source, "book.epub", "fr_CA"));

    assertThat(imported.language()).isEqualTo("fr-CA");
    assertThat(imported.coverAssetKey()).isNull();
    verify(storage, never()).storeCover(any(), any(), any(), any());
  }

  @Test
  void missingLanguageFailsBeforeCreatingProcessingRecord() {
    when(parser.parse(any(), any())).thenReturn(parsed(null, false));

    assertThatThrownBy(() -> useCase.importEpub(new ImportEpubCommand(source, "book.epub", null)))
        .isInstanceOfSatisfying(
            DocumentImportException.class,
            exception -> assertThat(exception.reason()).isEqualTo(Reason.LANGUAGE_REQUIRED));
    verify(documents, never()).saveDocument(any());
    verify(storage, never()).storeSource(any(), any(), any());
  }

  @Test
  void storageFailureCompensatesAssetsAndMarksDocumentFailed() {
    when(parser.parse(any(), any())).thenReturn(parsed("en", true));
    stubPersistence();
    when(storage.storeSource(any(), any(), any())).thenReturn("source-key");
    when(storage.storeCover(any(), any(), any(), any()))
        .thenThrow(new DocumentImportException(Reason.STORAGE_FAILURE, "failed"));

    assertThatThrownBy(() -> useCase.importEpub(new ImportEpubCommand(source, "book.epub", null)))
        .isInstanceOfSatisfying(
            DocumentImportException.class,
            exception -> assertThat(exception.reason()).isEqualTo(Reason.STORAGE_FAILURE));
    verify(storage).delete("source-key");
    verify(documents)
        .saveDocument(
            org.mockito.ArgumentMatchers.argThat(
                document -> document.importStatus() == DocumentImportStatus.FAILED));
  }

  @Test
  void parserFailureCreatesNeitherDatabaseStateNorAssets() {
    when(parser.parse(any(), any()))
        .thenThrow(new DocumentImportException(Reason.INVALID_EPUB, "invalid"));

    assertThatThrownBy(() -> useCase.importEpub(new ImportEpubCommand(source, "book.epub", null)))
        .isInstanceOf(DocumentImportException.class);
    verify(documents, never()).saveDocument(any());
    verify(storage, never()).storeSource(any(), any(), any());
  }

  @Test
  void persistenceFailureAfterStorageDeletesTheAssetAndAttemptsFailedStatus() {
    when(parser.parse(any(), any())).thenReturn(parsed("en", false));
    var documentId = UUID.randomUUID();
    var calls = new AtomicInteger();
    when(documents.saveDocument(any()))
        .thenAnswer(
            invocation -> {
              var value = invocation.<ImportedDocument>getArgument(0);
              if (calls.getAndIncrement() == 0) return withId(value, documentId);
              return value;
            });
    when(storage.storeSource(any(), any(), any())).thenReturn("source-key");
    when(documents.saveSections(any()))
        .thenThrow(new IllegalStateException("database unavailable"));

    assertThatThrownBy(() -> useCase.importEpub(new ImportEpubCommand(source, "book.epub", null)))
        .isInstanceOfSatisfying(
            DocumentImportException.class,
            exception -> assertThat(exception.reason()).isEqualTo(Reason.IMPORT_FAILURE));
    verify(storage).delete("source-key");
    verify(documents)
        .saveDocument(
            org.mockito.ArgumentMatchers.argThat(
                document -> document.importStatus() == DocumentImportStatus.FAILED));
  }

  private ParsedDocument parsed(String language, boolean cover) {
    return new ParsedDocument(
        "Book",
        "Author",
        language,
        List.of(
            new ParsedDocumentSection("One", "OPS/one.xhtml", List.of("First paragraph.")),
            new ParsedDocumentSection("Two", "OPS/two.xhtml", List.of("Second paragraph."))),
        cover ? new ParsedDocumentCover("image/png", new byte[] {1, 2}) : null);
  }

  private void stubPersistence() {
    when(documents.saveDocument(any()))
        .thenAnswer(
            invocation -> {
              var value = invocation.<ImportedDocument>getArgument(0);
              return value.id() == null ? withId(value, UUID.randomUUID()) : value;
            });
    org.mockito.Mockito.lenient()
        .when(documents.saveSections(any()))
        .thenAnswer(
            invocation ->
                invocation.<List<DocumentSection>>getArgument(0).stream()
                    .map(
                        section ->
                            new DocumentSection(
                                UUID.randomUUID(),
                                section.documentId(),
                                section.ordinal(),
                                section.title(),
                                section.sourceLocator()))
                    .toList());
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
        value.chunkingVersion(),
        value.createdAt(),
        value.updatedAt());
  }
}
