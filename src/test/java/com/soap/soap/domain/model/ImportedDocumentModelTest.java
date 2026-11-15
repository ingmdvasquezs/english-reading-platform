package com.soap.soap.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportedDocumentModelTest {

  private static final String HASH = "a".repeat(64);
  private static final LocalDateTime NOW = LocalDateTime.parse("2026-09-07T10:00:00");

  @Test
  void readyAndFailedTransitionsPreserveTheSourceStorageProvider() {
    for (var provider : StorageProvider.values()) {
      var processing =
          new ImportedDocument(
              UUID.randomUUID(),
              UUID.randomUUID(),
              "Placeholder",
              null,
              "en",
              DocumentFormat.EPUB,
              null,
              "documents/u/d/source.epub",
              provider,
              "book.epub",
              HASH,
              DocumentImportStatus.PROCESSING,
              null,
              5,
              NOW,
              NOW);

      var ready = processing.markReady("Book", "Ada", "en-GB", "covers/c.jpg", NOW.plusMinutes(1));
      assertThat(ready.importStatus()).isEqualTo(DocumentImportStatus.READY);
      assertThat(ready.sourceStorageProvider()).isEqualTo(provider);
      assertThat(ready.failureReason()).isNull();
      assertThat(ready.title()).isEqualTo("Book");
      assertThat(ready.language()).isEqualTo("en-GB");
      assertThat(ready.coverAssetKey()).isEqualTo("covers/c.jpg");
      assertThat(ready.id()).isEqualTo(processing.id());
      assertThat(ready.sourceAssetKey()).isEqualTo(processing.sourceAssetKey());
      assertThat(ready.createdAt()).isEqualTo(NOW);
      assertThat(ready.updatedAt()).isEqualTo(NOW.plusMinutes(1));

      var failed = processing.markFailed("INVALID_EPUB", NOW.plusMinutes(2));
      assertThat(failed.importStatus()).isEqualTo(DocumentImportStatus.FAILED);
      assertThat(failed.sourceStorageProvider()).isEqualTo(provider);
      assertThat(failed.failureReason()).isEqualTo("INVALID_EPUB");
      assertThat(failed.title()).isEqualTo("Placeholder");
    }
  }

  @Test
  void createsValidDocumentsWithoutRestrictingLanguageToEnglish() {
    var document = document("fr-CA", 1);

    assertThat(document.language()).isEqualTo("fr-CA");
    assertThat(document.format()).isEqualTo(DocumentFormat.EPUB);
    assertThat(document.importStatus()).isEqualTo(DocumentImportStatus.READY);
  }

  @Test
  void validatesLanguageHashChunkingVersionAndTimestamps() {
    assertThatThrownBy(() -> document("not_a_language!", 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("BCP 47");
    assertThatThrownBy(() -> document("en", 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("chunking version");
    assertThatThrownBy(
            () ->
                new ImportedDocument(
                    null,
                    UUID.randomUUID(),
                    "Book",
                    null,
                    "en",
                    DocumentFormat.EPUB,
                    null,
                    null,
                    null,
                    "bad",
                    DocumentImportStatus.READY,
                    1,
                    NOW,
                    NOW))
        .hasMessageContaining("SHA-256");
  }

  @Test
  void validatesSectionAndUnitInvariantsAndAllowsASectionlessPhysicalPage() {
    var documentId = UUID.randomUUID();
    assertThatThrownBy(() -> new DocumentSection(null, documentId, 0, null, null))
        .hasMessageContaining("ordinal");
    assertThatThrownBy(
            () ->
                new DocumentUnit(
                    null,
                    documentId,
                    UUID.randomUUID(),
                    1,
                    1,
                    DocumentUnitKind.LOGICAL_CHUNK,
                    "text",
                    -1,
                    null,
                    null))
        .hasMessageContaining("word count");

    var pdfPage =
        new DocumentUnit(
            null,
            documentId,
            null,
            1,
            1,
            DocumentUnitKind.PHYSICAL_PAGE,
            "Extracted page",
            2,
            "page:1",
            HASH);
    assertThat(pdfPage.sectionId()).isNull();
  }

  private ImportedDocument document(String language, int chunkingVersion) {
    return new ImportedDocument(
        null,
        UUID.randomUUID(),
        "A book",
        "An author",
        language,
        DocumentFormat.EPUB,
        "covers/key",
        "sources/key",
        "book.epub",
        HASH,
        DocumentImportStatus.READY,
        chunkingVersion,
        NOW,
        NOW);
  }
}
