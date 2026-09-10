package com.soap.soap.spike.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.command.UpdateDocumentProgressCommand;
import com.soap.soap.application.exception.DocumentAlreadyImportedException;
import com.soap.soap.application.exception.DocumentNotReadyException;
import com.soap.soap.application.model.DocumentImportLimits;
import com.soap.soap.application.port.out.DocumentProgressRepositoryPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.WordRepositoryPort;
import com.soap.soap.application.service.DocumentChunker;
import com.soap.soap.application.service.DocumentLanguagePolicy;
import com.soap.soap.application.service.DocumentLanguageResolver;
import com.soap.soap.application.service.ReaderContentPreparer;
import com.soap.soap.application.usecase.AcceptDocumentImportUseCase;
import com.soap.soap.application.usecase.DeleteDocumentUseCase;
import com.soap.soap.application.usecase.DocumentProgressUseCase;
import com.soap.soap.application.usecase.DocumentQueryUseCase;
import com.soap.soap.application.usecase.ImportEpubUseCase;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import com.soap.soap.infrastructure.document.PdfDocumentParserAdapter;
import com.soap.soap.infrastructure.storage.FilesystemDocumentAssetStorageAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
class PdfImportIntegrationTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired UserRepositoryPort users;
  @Autowired ImportedDocumentRepositoryPort documents;
  @Autowired DocumentProgressRepositoryPort progressRepository;
  @Autowired ReaderContentPreparer readerContent;
  @Autowired JdbcTemplate jdbc;
  @Autowired WordRepositoryPort words;
  @Autowired UserVocabularyRepositoryPort vocabulary;

  @Test
  void languageRequiredIsFailedHiddenAndRetryBecomesReadable(@TempDir Path directory)
      throws Exception {
    var user =
        users.save(
            new User(null, "PDF Owner", "pdf-" + UUID.randomUUID() + "@example.com", "hash", null));
    var currentUser = (com.soap.soap.application.port.out.CurrentUserPort) () -> user.id();
    var limits =
        new DocumentImportLimits(
            50L << 20, 2_000, 10L << 20, 150L << 20, 100, 10L << 20, 25_000_000);
    var storage = new FilesystemDocumentAssetStorageAdapter(directory.resolve("assets"), limits);
    var processor =
        new ImportEpubUseCase(
            currentUser,
            users,
            new PdfDocumentParserAdapter(limits),
            storage,
            documents,
            new DocumentLanguageResolver(),
            new DocumentChunker(),
            Clock.systemUTC());
    var accept =
        new AcceptDocumentImportUseCase(
            currentUser,
            users,
            documents,
            storage,
            processor,
            new DocumentLanguagePolicy(Set.of("en")),
            Runnable::run,
            new SimpleMeterRegistry(),
            Clock.systemUTC());
    var queries =
        new DocumentQueryUseCase(currentUser, documents, progressRepository, readerContent);
    var progress =
        new DocumentProgressUseCase(
            currentUser, documents, progressRepository, queries, Clock.systemUTC());
    var source = createPdf(directory.resolve("journey.pdf"));

    var failedAttempt = accept.accept(source, "journey.pdf", null, DocumentFormat.PDF);
    var failed = queries.get(failedAttempt.documentId());

    assertThat(failedAttempt.status()).isEqualTo(DocumentImportStatus.PROCESSING);
    assertThat(failed.status()).isEqualTo(DocumentImportStatus.FAILED);
    assertThat(failed.failureReason()).isEqualTo("LANGUAGE_REQUIRED");
    assertThat(failed.totalSections()).isZero();
    assertThat(failed.totalUnits()).isZero();
    assertThat(documents.findSections(failedAttempt.documentId())).isEmpty();
    assertThat(documents.findUnits(failedAttempt.documentId())).isEmpty();
    assertThat(progress.get(failedAttempt.documentId()).status()).isEqualTo("NOT_STARTED");
    assertThat(queries.list(0, 10).content())
        .noneMatch(view -> view.id().equals(failedAttempt.documentId()));
    assertThatThrownBy(() -> queries.structure(failedAttempt.documentId()))
        .isInstanceOf(DocumentNotReadyException.class);
    var failedRow =
        jdbc.queryForMap(
            "SELECT import_status, failure_reason, source_asset_key, cover_asset_key, "
                + "source_sha256, deduplication_sha256, chunking_version "
                + "FROM imported_documents WHERE id = ?",
            failedAttempt.documentId());
    assertThat(failedRow.get("import_status")).isEqualTo("FAILED");
    assertThat(failedRow.get("failure_reason")).isEqualTo("LANGUAGE_REQUIRED");
    assertThat(failedRow.get("source_asset_key")).isNull();
    assertThat(failedRow.get("cover_asset_key")).isNull();
    assertThat(failedRow.get("source_sha256")).isNotNull();
    assertThat(failedRow.get("deduplication_sha256")).isNull();
    assertThat(failedRow.get("chunking_version")).isEqualTo(4);

    var accepted = accept.accept(source, "journey.pdf", "en", DocumentFormat.PDF);
    var ready = queries.get(accepted.documentId());
    var listed = queries.list(0, 10);
    var structure = queries.structure(accepted.documentId());
    var unit = queries.unit(accepted.documentId(), structure.firstUnitId());
    var savedProgress =
        progress.update(
            new UpdateDocumentProgressCommand(
                accepted.documentId(), structure.firstUnitId(), true, null));

    assertThat(accepted.status()).isEqualTo(DocumentImportStatus.PROCESSING);
    assertThat(ready.status()).isEqualTo(DocumentImportStatus.READY);
    assertThat(ready.format()).isEqualTo(DocumentFormat.PDF);
    assertThat(ready.title()).isEqualTo("The Last Light of Alder Harbor");
    assertThat(ready.author()).isNull();
    assertThat(listed.content()).extracting(view -> view.id()).contains(accepted.documentId());
    assertThat(listed.content()).noneMatch(view -> view.id().equals(failedAttempt.documentId()));
    assertThat(structure.firstUnitId()).isNotNull();
    assertThat(structure.sections())
        .allSatisfy(
            section -> {
              assertThat(section.title()).isNull();
              assertThat(section.firstUnitId()).isNotNull();
              assertThat(section.unitCount()).isPositive();
            });
    assertThat(structure.totalUnits()).isPositive();
    assertThat(unit.content()).contains("international reading journey");
    assertThat(unit.tokens()).isNotEmpty();
    assertThat(unit.previousUnitId()).isNull();
    assertThat(savedProgress.status()).isEqualTo("COMPLETED");
    assertThat(progress.get(accepted.documentId()).currentUnitId())
        .isEqualTo(structure.firstUnitId());
    assertThatThrownBy(() -> accept.accept(source, "journey.pdf", "en", DocumentFormat.PDF))
        .isInstanceOf(DocumentAlreadyImportedException.class);

    var now = java.time.LocalDateTime.now();
    var knownWord = words.save(new Word(null, "remember", "en"));
    vocabulary.save(new UserVocabulary(null, user, knownWord, VocabularyStatus.KNOWN, now, now));

    var sourceAsset =
        storage.locate(
            documents.findDocumentById(accepted.documentId()).orElseThrow().sourceAssetKey());
    var deletions =
        new DeleteDocumentUseCase(currentUser, documents, storage, new SimpleMeterRegistry());
    deletions.delete(accepted.documentId());

    assertThat(documents.findDocumentById(accepted.documentId())).isEmpty();
    assertThat(documents.findSections(accepted.documentId())).isEmpty();
    assertThat(documents.findUnits(accepted.documentId())).isEmpty();
    assertThat(progressRepository.findByUserIdAndDocumentId(user.id(), accepted.documentId()))
        .isEmpty();
    assertThat(sourceAsset).doesNotExist();
    assertThat(vocabulary.findByUserIdAndWordId(user.id(), knownWord.id()).orElseThrow().status())
        .isEqualTo(VocabularyStatus.KNOWN);
    assertThat(queries.list(0, 10).content())
        .noneMatch(view -> view.id().equals(accepted.documentId()));
    assertThatThrownBy(() -> queries.get(accepted.documentId()))
        .isInstanceOf(com.soap.soap.application.exception.DocumentNotFoundException.class);

    var reimported = accept.accept(source, "journey.pdf", "en", DocumentFormat.PDF);
    assertThat(queries.get(reimported.documentId()).status()).isEqualTo(DocumentImportStatus.READY);
  }

  private Path createPdf(Path target) throws Exception {
    try (var document = new PDDocument()) {
      document.getDocumentInformation().setTitle("(anonymous)");
      document.getDocumentInformation().setAuthor("(anonymous)");
      var page = new PDPage();
      document.addPage(page);
      try (var content = new PDPageContentStream(document, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(50, 740);
        content.showText("English Reading Sample - Page 1");
        content.newLineAtOffset(0, -18);
        content.showText("The Last Light of Alder Harbor");
        content.newLineAtOffset(0, -18);
        content.showText("A short English reading sample");
        content.newLineAtOffset(0, -18);
        content.showText("Chapter 1 - The Letter");
        content.newLineAtOffset(0, -18);
        content.showText("An international reading journey provides enough useful English words.");
        content.endText();
      }
      document.save(target.toFile());
    }
    return target;
  }
}
