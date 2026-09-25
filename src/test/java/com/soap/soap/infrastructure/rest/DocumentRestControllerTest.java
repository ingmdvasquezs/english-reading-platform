package com.soap.soap.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.soap.soap.application.exception.DocumentNotFoundException;
import com.soap.soap.application.model.AcceptedDocumentImport;
import com.soap.soap.application.model.DocumentStructureView;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.DocumentAssetStoragePort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.usecase.AcceptDocumentImportUseCase;
import com.soap.soap.application.usecase.DeleteDocumentUseCase;
import com.soap.soap.application.usecase.DocumentProgressUseCase;
import com.soap.soap.application.usecase.DocumentQueryUseCase;
import com.soap.soap.application.usecase.GetDocumentVocabularyCompatibilityUseCase;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.ImportedDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DocumentRestControllerTest {
  @Mock AcceptDocumentImportUseCase imports;
  @Mock DocumentQueryUseCase queries;
  @Mock DocumentProgressUseCase progress;
  @Mock DeleteDocumentUseCase deletions;
  @Mock GetDocumentVocabularyCompatibilityUseCase compatibility;
  @Mock CurrentUserPort currentUser;
  @Mock ImportedDocumentRepositoryPort documents;
  @Mock DocumentAssetStoragePort storage;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new DocumentRestController(
                    imports,
                    queries,
                    progress,
                    deletions,
                    compatibility,
                    currentUser,
                    documents,
                    storage))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
  }

  @Test
  void deleteReturnsNoContent() throws Exception {
    mvc.perform(delete("/api/v1/documents/{documentId}", UUID.randomUUID()))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
  }

  @Test
  void processingDeleteReturnsSemanticConflict() throws Exception {
    var id = UUID.randomUUID();
    org.mockito.Mockito.doThrow(
            new com.soap.soap.application.exception.DocumentStillProcessingException())
        .when(deletions)
        .delete(id);

    mvc.perform(delete("/api/v1/documents/{documentId}", id))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOCUMENT_PROCESSING"));
  }

  @Test
  void validMultipartReturnsAcceptedProcessingContract() throws Exception {
    var id = UUID.randomUUID();
    when(imports.accept(any(), eq("../unsafe.epub"), eq("en-US"), eq(DocumentFormat.EPUB)))
        .thenReturn(new AcceptedDocumentImport(id, DocumentImportStatus.PROCESSING));
    var file =
        new MockMultipartFile(
            "file", "../unsafe.epub", "application/epub+zip", new byte[] {'P', 'K', 3, 4});

    mvc.perform(multipart("/api/v1/documents").file(file).param("languageOverride", "en-US"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.documentId").value(id.toString()))
        .andExpect(jsonPath("$.status").value("PROCESSING"));
  }

  @Test
  void validPdfReturnsAcceptedAndSelectsPdfPipeline() throws Exception {
    var id = UUID.randomUUID();
    when(imports.accept(any(), eq("paper.pdf"), eq(null), eq(DocumentFormat.PDF)))
        .thenReturn(new AcceptedDocumentImport(id, DocumentImportStatus.PROCESSING));
    var file =
        new MockMultipartFile("file", "paper.pdf", "application/pdf", "%PDF-1.7\n".getBytes());

    mvc.perform(multipart("/api/v1/documents").file(file))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.documentId").value(id.toString()))
        .andExpect(jsonPath("$.status").value("PROCESSING"));
  }

  @Test
  void rejectsPdfExtensionWhoseSignatureIsNotPdf() throws Exception {
    var file =
        new MockMultipartFile("file", "paper.pdf", "application/pdf", "not-a-pdf".getBytes());

    mvc.perform(multipart("/api/v1/documents").file(file))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void rejectsUnexpectedContentTypeWithSafeJson() throws Exception {
    var file = new MockMultipartFile("file", "book.epub", "text/html", "bad".getBytes());
    mvc.perform(multipart("/api/v1/documents").file(file))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("The request is invalid."));
  }

  @Test
  void ownershipStyleNotFoundDoesNotLeakInternalDetails() throws Exception {
    var id = UUID.randomUUID();
    when(queries.get(id)).thenThrow(new DocumentNotFoundException(id));
    mvc.perform(get("/api/v1/documents/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(id.toString()))));
  }

  @Test
  void structureExposesOnlyFirstUnitIdSectionsAndTotals() throws Exception {
    var documentId = UUID.randomUUID();
    var firstUnitId = UUID.randomUUID();
    when(queries.structure(documentId))
        .thenReturn(
            new DocumentStructureView(
                documentId,
                firstUnitId,
                List.of(
                    new DocumentStructureView.Section(
                        UUID.randomUUID(), 1, "Chapter 1", firstUnitId, 4)),
                48));

    mvc.perform(get("/api/v1/documents/{id}/structure", documentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstUnitId").value(firstUnitId.toString()))
        .andExpect(jsonPath("$.sections[0].unitCount").value(4))
        .andExpect(jsonPath("$.sections[0].firstUnitId").value(firstUnitId.toString()))
        .andExpect(jsonPath("$.totalUnits").value(48))
        .andExpect(jsonPath("$.content").doesNotExist())
        .andExpect(jsonPath("$.unitIds").doesNotExist());
  }

  @Test
  void coverReturnsOwnedBytesAndDetectedContentType() throws Exception {
    var userId = UUID.randomUUID();
    var documentId = UUID.randomUUID();
    var cover = Files.createTempFile("cover-", ".png");
    Files.write(cover, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47});
    when(currentUser.requireUserId()).thenReturn(userId);
    when(documents.findDocumentById(documentId))
        .thenReturn(java.util.Optional.of(document(documentId, userId, "cover-key")));
    when(storage.locate("cover-key")).thenReturn(cover);

    mvc.perform(get("/api/v1/documents/{id}/cover", documentId))
        .andExpect(status().isOk())
        .andExpect(content().bytes(Files.readAllBytes(cover)));
  }

  @Test
  void coverOfAnotherOwnerIsNotDisclosed() throws Exception {
    var documentId = UUID.randomUUID();
    when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
    when(documents.findDocumentById(documentId))
        .thenReturn(java.util.Optional.of(document(documentId, UUID.randomUUID(), "cover-key")));

    mvc.perform(get("/api/v1/documents/{id}/cover", documentId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
  }

  @Test
  void duplicateUploadUsesConflictContract() throws Exception {
    when(imports.accept(any(), any(), any(), eq(DocumentFormat.EPUB)))
        .thenThrow(
            new com.soap.soap.application.exception.DocumentAlreadyImportedException(
                UUID.randomUUID(), DocumentImportStatus.READY));
    var file =
        new MockMultipartFile(
            "file", "book.epub", "application/epub+zip", new byte[] {'P', 'K', 3, 4});

    mvc.perform(multipart("/api/v1/documents").file(file))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOCUMENT_ALREADY_IMPORTED"));
  }

  @Test
  void tokenResponsePreservesNullVocabularyStatusWhenUnclassified() {
    var token =
        new com.soap.soap.application.model.ReaderToken(
            "harbour", "harbour", com.soap.soap.application.model.ReaderTokenType.WORD, null);
    var response = DocumentRestController.TokenResponse.from(token);
    org.assertj.core.api.Assertions.assertThat(response.vocabularyStatus()).isNull();
    org.assertj.core.api.Assertions.assertThat(response.value()).isEqualTo("harbour");
  }

  @Test
  void tokenResponsePreservesPersistedNewStatus() {
    var token =
        new com.soap.soap.application.model.ReaderToken(
            "harbour",
            "harbour",
            com.soap.soap.application.model.ReaderTokenType.WORD,
            com.soap.soap.domain.model.VocabularyStatus.NEW);
    var response = DocumentRestController.TokenResponse.from(token);
    org.assertj.core.api.Assertions.assertThat(response.vocabularyStatus()).isEqualTo("NEW");
  }

  @Test
  void unitReaderEndpointReturnsNullForUnclassifiedAndNewForPersistedNew() throws Exception {
    var documentId = UUID.randomUUID();
    var sectionId = UUID.randomUUID();
    var unitId = UUID.randomUUID();
    var unclassifiedToken =
        new com.soap.soap.application.model.ReaderToken(
            "harbour", "harbour", com.soap.soap.application.model.ReaderTokenType.WORD, null);
    var persistedNewToken =
        new com.soap.soap.application.model.ReaderToken(
            "market",
            "market",
            com.soap.soap.application.model.ReaderTokenType.WORD,
            com.soap.soap.domain.model.VocabularyStatus.NEW);
    var readerData =
        new com.soap.soap.application.model.DocumentUnitReaderData(
            documentId,
            sectionId,
            "Chapter 1",
            1,
            1,
            unitId,
            1,
            1,
            1,
            1,
            null,
            null,
            "harbour market",
            List.of(unclassifiedToken, persistedNewToken),
            null);

    when(queries.unit(documentId, unitId)).thenReturn(readerData);

    mvc.perform(get("/api/v1/documents/{id}/units/{unitId}", documentId, unitId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokens[0].value").value("harbour"))
        .andExpect(
            jsonPath("$.tokens[0].vocabularyStatus").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.tokens[1].value").value("market"))
        .andExpect(jsonPath("$.tokens[1].vocabularyStatus").value("NEW"));
  }

  @Test
  void compatibilityReturnsOkAndExpectedContract() throws Exception {
    var documentId = UUID.randomUUID();
    var view =
        new com.soap.soap.application.model.DocumentVocabularyCompatibilityView(
            documentId,
            120,
            80,
            15,
            5,
            2,
            20,
            new java.math.BigDecimal("76.50"),
            new java.math.BigDecimal("83.33"));
    when(compatibility.getCompatibility(documentId)).thenReturn(view);

    mvc.perform(get("/api/v1/documents/{id}/compatibility", documentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentId").value(documentId.toString()))
        .andExpect(jsonPath("$.uniqueWords").value(120))
        .andExpect(jsonPath("$.knownWords").value(80))
        .andExpect(jsonPath("$.learningWords").value(15))
        .andExpect(jsonPath("$.explicitNewWords").value(5))
        .andExpect(jsonPath("$.ignoredWords").value(2))
        .andExpect(jsonPath("$.unclassifiedWords").value(20))
        .andExpect(jsonPath("$.vocabularyFitPercentage").value(76.50))
        .andExpect(jsonPath("$.classificationConfidencePercentage").value(83.33));
  }

  @Test
  void compatibilityWhenNotFoundReturns404() throws Exception {
    var documentId = UUID.randomUUID();
    when(compatibility.getCompatibility(documentId))
        .thenThrow(new DocumentNotFoundException(documentId));

    mvc.perform(get("/api/v1/documents/{id}/compatibility", documentId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
  }

  @Test
  void compatibilityWhenNotReadyReturns409() throws Exception {
    var documentId = UUID.randomUUID();
    when(compatibility.getCompatibility(documentId))
        .thenThrow(new com.soap.soap.application.exception.DocumentNotReadyException());

    mvc.perform(get("/api/v1/documents/{id}/compatibility", documentId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_READY"));
  }

  @Test
  void uploadStagesEpubInsidePrivateStagingDirectoryAndCleansUpOnSuccess(@TempDir Path tempDir)
      throws Exception {
    var customMvc =
        MockMvcBuilders.standaloneSetup(
                new DocumentRestController(
                    imports,
                    queries,
                    progress,
                    deletions,
                    compatibility,
                    currentUser,
                    documents,
                    storage,
                    tempDir))
            .setControllerAdvice(new RestExceptionHandler())
            .build();

    var id = UUID.randomUUID();
    var pathCaptor = org.mockito.ArgumentCaptor.forClass(Path.class);
    when(imports.accept(
            pathCaptor.capture(), eq("custom-novel.epub"), eq(null), eq(DocumentFormat.EPUB)))
        .thenReturn(new AcceptedDocumentImport(id, DocumentImportStatus.PROCESSING));

    var file =
        new MockMultipartFile(
            "file", "custom-novel.epub", "application/epub+zip", new byte[] {'P', 'K', 3, 4});

    customMvc
        .perform(multipart("/api/v1/documents").file(file))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.documentId").value(id.toString()))
        .andExpect(jsonPath("$.status").value("PROCESSING"));

    var stagedPath = pathCaptor.getValue();
    assertThat(stagedPath).isNotNull().hasParentRaw(tempDir.resolve("staging"));
    assertThat(stagedPath.getFileName().toString())
        .startsWith("document-upload-")
        .endsWith(".epub")
        .doesNotContain("custom-novel");
    assertThat(Files.exists(stagedPath)).isFalse();
  }

  @Test
  void uploadStagesPdfInsidePrivateStagingDirectoryAndCleansUpOnSuccess(@TempDir Path tempDir)
      throws Exception {
    var customMvc =
        MockMvcBuilders.standaloneSetup(
                new DocumentRestController(
                    imports,
                    queries,
                    progress,
                    deletions,
                    compatibility,
                    currentUser,
                    documents,
                    storage,
                    tempDir))
            .setControllerAdvice(new RestExceptionHandler())
            .build();

    var id = UUID.randomUUID();
    var pathCaptor = org.mockito.ArgumentCaptor.forClass(Path.class);
    when(imports.accept(pathCaptor.capture(), eq("article.pdf"), eq(null), eq(DocumentFormat.PDF)))
        .thenReturn(new AcceptedDocumentImport(id, DocumentImportStatus.PROCESSING));

    var file =
        new MockMultipartFile("file", "article.pdf", "application/pdf", "%PDF-1.7\n".getBytes());

    customMvc
        .perform(multipart("/api/v1/documents").file(file))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.documentId").value(id.toString()))
        .andExpect(jsonPath("$.status").value("PROCESSING"));

    var stagedPath = pathCaptor.getValue();
    assertThat(stagedPath).isNotNull().hasParentRaw(tempDir.resolve("staging"));
    assertThat(stagedPath.getFileName().toString())
        .startsWith("document-upload-")
        .endsWith(".pdf")
        .doesNotContain("article");
    assertThat(Files.exists(stagedPath)).isFalse();
  }

  @Test
  void uploadCleansUpStagedFileWhenImportThrowsException(@TempDir Path tempDir) throws Exception {
    var customMvc =
        MockMvcBuilders.standaloneSetup(
                new DocumentRestController(
                    imports,
                    queries,
                    progress,
                    deletions,
                    compatibility,
                    currentUser,
                    documents,
                    storage,
                    tempDir))
            .setControllerAdvice(new RestExceptionHandler())
            .build();

    var pathCaptor = org.mockito.ArgumentCaptor.forClass(Path.class);
    when(imports.accept(pathCaptor.capture(), any(), any(), any()))
        .thenThrow(new IllegalStateException("Failed during import"));

    var file =
        new MockMultipartFile(
            "file", "failing.epub", "application/epub+zip", new byte[] {'P', 'K', 3, 4});

    try {
      customMvc.perform(multipart("/api/v1/documents").file(file));
    } catch (Exception _) {
      // Ignored: verify cleanup regardless of exception propagation or handler
    }

    var stagedPath = pathCaptor.getValue();
    assertThat(stagedPath).isNotNull();
    assertThat(Files.exists(stagedPath)).isFalse();
  }

  private ImportedDocument document(UUID id, UUID ownerId, String coverKey) {
    var now = LocalDateTime.now();
    return new ImportedDocument(
        id,
        ownerId,
        "Book",
        null,
        "en",
        DocumentFormat.EPUB,
        coverKey,
        "source",
        "book.epub",
        "a".repeat(64),
        DocumentImportStatus.READY,
        3,
        now,
        now);
  }
}
