package com.soap.soap.application.usecase;

import com.soap.soap.application.command.ImportEpubCommand;
import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.exception.DocumentImportException.Reason;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.ParsedDocument;
import com.soap.soap.application.port.in.ImportEpubPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.DocumentAssetStoragePort;
import com.soap.soap.application.port.out.DocumentParserPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.service.DocumentChunker;
import com.soap.soap.application.service.DocumentLanguagePolicy;
import com.soap.soap.application.service.DocumentLanguageResolver;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.DocumentSection;
import com.soap.soap.domain.model.DocumentUnit;
import com.soap.soap.domain.model.DocumentUnitKind;
import com.soap.soap.domain.model.ImportedDocument;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImportEpubUseCase implements ImportEpubPort {
  private final CurrentUserPort currentUser;
  private final UserRepositoryPort users;
  private final DocumentParserPort parser;
  private final DocumentAssetStoragePort storage;
  private final ImportedDocumentRepositoryPort documents;
  private final DocumentLanguageResolver languages;
  private final DocumentChunker chunker;
  private final Clock clock;

  @Override
  public ImportedDocument importEpub(ImportEpubCommand command) {
    validate(command);
    var userId = currentUser.requireUserId();
    users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    var parsed = parser.parse(command.source(), DocumentFormat.EPUB);
    var language = languages.resolve(command.languageOverride(), parsed.declaredLanguage());
    var now = LocalDateTime.now(clock);
    var processing =
        documents.saveDocument(
            new ImportedDocument(
                null,
                userId,
                parsed.title(),
                parsed.author(),
                language,
                DocumentFormat.EPUB,
                null,
                null,
                command.originalFilename(),
                sha256(command.source()),
                DocumentImportStatus.PROCESSING,
                DocumentChunker.VERSION,
                now,
                now));

    String sourceKey = null;
    String coverKey = null;
    try {
      sourceKey = storage.storeSource(userId, processing.id(), command.source());
      if (parsed.cover() != null) {
        coverKey =
            storage.storeCover(
                userId, processing.id(), parsed.cover().mediaType(), parsed.cover().bytes());
      }
      persistStructure(processing, parsed, language);
      var readyAt = LocalDateTime.now(clock);
      return documents.saveDocument(
          copy(processing, sourceKey, coverKey, DocumentImportStatus.READY, readyAt));
    } catch (RuntimeException exception) {
      deleteQuietly(coverKey);
      deleteQuietly(sourceKey);
      markFailed(processing);
      if (exception instanceof DocumentImportException) throw exception;
      throw new DocumentImportException(Reason.IMPORT_FAILURE, "EPUB import failed", exception);
    }
  }

  public ImportedDocument processAccepted(
      ImportedDocument processing, String languageOverride, DocumentLanguagePolicy languagePolicy) {
    if (processing.importStatus() != DocumentImportStatus.PROCESSING
        || processing.sourceAssetKey() == null) {
      throw new IllegalArgumentException("Accepted document must be PROCESSING with a source");
    }
    String coverKey = null;
    try {
      var parsed = parser.parse(storage.locate(processing.sourceAssetKey()), processing.format());
      var language = languages.resolve(languageOverride, parsed.declaredLanguage());
      languagePolicy.requireSupported(language);
      if (parsed.cover() != null) {
        coverKey =
            storage.storeCover(
                processing.ownerId(),
                processing.id(),
                parsed.cover().mediaType(),
                parsed.cover().bytes());
      }
      var enriched =
          copyMetadata(
              processing, parsed, language, coverKey, DocumentImportStatus.PROCESSING, null);
      persistStructure(enriched, parsed, language);
      return documents.saveDocument(
          copyMetadata(enriched, parsed, language, coverKey, DocumentImportStatus.READY, null));
    } catch (RuntimeException exception) {
      deleteQuietly(coverKey);
      deleteQuietly(processing.sourceAssetKey());
      markFailed(processing, reason(exception));
      throw exception;
    }
  }

  private void persistStructure(
      ImportedDocument document, ParsedDocument parsed, String effectiveLanguage) {
    var sections = new ArrayList<DocumentSection>();
    for (int index = 0; index < parsed.sections().size(); index++) {
      var section = parsed.sections().get(index);
      sections.add(
          new DocumentSection(
              null, document.id(), index + 1, section.title(), section.sourceLocator()));
    }
    var savedSections = documents.saveSections(sections);
    var units = new ArrayList<DocumentUnit>();
    var globalOrdinal = 1;
    for (int index = 0; index < savedSections.size(); index++) {
      var parsedSection = parsed.sections().get(index);
      var chunks = chunker.chunk(parsedSection.blocks(), effectiveLanguage);
      for (int sectionOrdinal = 0; sectionOrdinal < chunks.size(); sectionOrdinal++) {
        var chunk = chunks.get(sectionOrdinal);
        units.add(
            new DocumentUnit(
                null,
                document.id(),
                savedSections.get(index).id(),
                globalOrdinal++,
                sectionOrdinal + 1,
                DocumentUnitKind.LOGICAL_CHUNK,
                chunk.content(),
                chunk.wordCount(),
                parsedSection.sourceLocator(),
                sha256(chunk.content().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
      }
    }
    if (units.isEmpty()) {
      var reason =
          document.format() == DocumentFormat.PDF
              ? Reason.PDF_SCANNED_NOT_SUPPORTED
              : Reason.INVALID_EPUB;
      throw new DocumentImportException(reason, "Document contains no readable content");
    }
    documents.saveUnits(units);
  }

  private void validate(ImportEpubCommand command) {
    if (command == null || command.source() == null) {
      throw new DocumentImportException(Reason.INVALID_EPUB, "EPUB source is required");
    }
    if (command.originalFilename() != null && command.originalFilename().length() > 500) {
      throw new DocumentImportException(Reason.IMPORT_FAILURE, "Original filename is too long");
    }
  }

  private String sha256(java.nio.file.Path source) {
    try (InputStream input = Files.newInputStream(source)) {
      var digest = digest();
      var buffer = new byte[16 * 1024];
      for (int read; (read = input.read(buffer)) >= 0; ) {
        if (read > 0) digest.update(buffer, 0, read);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException exception) {
      throw new DocumentImportException(
          Reason.IMPORT_FAILURE, "Unable to hash document", exception);
    }
  }

  private String sha256(byte[] bytes) {
    return HexFormat.of().formatHex(digest().digest(bytes));
  }

  private MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private ImportedDocument copy(
      ImportedDocument document,
      String sourceKey,
      String coverKey,
      DocumentImportStatus status,
      LocalDateTime updatedAt) {
    return new ImportedDocument(
        document.id(),
        document.ownerId(),
        document.title(),
        document.author(),
        document.language(),
        document.format(),
        coverKey,
        sourceKey,
        document.originalFilename(),
        document.sourceSha256(),
        status,
        status == DocumentImportStatus.FAILED ? Reason.IMPORT_FAILURE.name() : null,
        document.chunkingVersion(),
        document.createdAt(),
        updatedAt);
  }

  private void markFailed(ImportedDocument document) {
    markFailed(document, Reason.IMPORT_FAILURE);
  }

  private void markFailed(ImportedDocument document, Reason reason) {
    try {
      documents.saveDocument(
          new ImportedDocument(
              document.id(),
              document.ownerId(),
              document.title(),
              document.author(),
              document.language(),
              document.format(),
              null,
              null,
              document.originalFilename(),
              document.sourceSha256(),
              DocumentImportStatus.FAILED,
              reason.name(),
              document.chunkingVersion(),
              document.createdAt(),
              LocalDateTime.now(clock)));
    } catch (RuntimeException ignored) {
      // Original failure remains authoritative when persistence itself is unavailable.
    }
  }

  private Reason reason(RuntimeException exception) {
    return exception instanceof DocumentImportException importException
        ? importException.reason()
        : Reason.IMPORT_FAILURE;
  }

  private ImportedDocument copyMetadata(
      ImportedDocument document,
      ParsedDocument parsed,
      String language,
      String coverKey,
      DocumentImportStatus status,
      String failureReason) {
    return new ImportedDocument(
        document.id(),
        document.ownerId(),
        parsed.title() == null ? document.title() : parsed.title(),
        parsed.author(),
        language,
        document.format(),
        coverKey,
        document.sourceAssetKey(),
        document.originalFilename(),
        document.sourceSha256(),
        status,
        failureReason,
        document.chunkingVersion(),
        document.createdAt(),
        LocalDateTime.now(clock));
  }

  private void deleteQuietly(String key) {
    try {
      storage.delete(key);
    } catch (RuntimeException ignored) {
      // Best-effort compensation; operational cleanup can retry orphan keys.
    }
  }
}
