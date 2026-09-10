package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.DocumentAlreadyImportedException;
import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.exception.ImportCapacityExceededException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.AcceptedDocumentImport;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.DocumentAssetStoragePort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.service.DocumentChunker;
import com.soap.soap.application.service.DocumentLanguagePolicy;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.ImportedDocument;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class AcceptDocumentImportUseCase {
  private static final Logger LOG = LoggerFactory.getLogger(AcceptDocumentImportUseCase.class);
  private final CurrentUserPort currentUser;
  private final UserRepositoryPort users;
  private final ImportedDocumentRepositoryPort documents;
  private final DocumentAssetStoragePort storage;
  private final ImportEpubUseCase processor;
  private final DocumentLanguagePolicy languagePolicy;
  private final Executor executor;
  private final MeterRegistry meters;
  private final Clock clock;

  public AcceptDocumentImportUseCase(
      CurrentUserPort currentUser,
      UserRepositoryPort users,
      ImportedDocumentRepositoryPort documents,
      DocumentAssetStoragePort storage,
      ImportEpubUseCase processor,
      DocumentLanguagePolicy languagePolicy,
      @Qualifier("documentImportExecutor") Executor executor,
      MeterRegistry meters,
      Clock clock) {
    this.currentUser = currentUser;
    this.users = users;
    this.documents = documents;
    this.storage = storage;
    this.processor = processor;
    this.languagePolicy = languagePolicy;
    this.executor = executor;
    this.meters = meters;
    this.clock = clock;
  }

  public AcceptedDocumentImport accept(
      Path stagedSource, String originalFilename, String languageOverride) {
    return accept(stagedSource, originalFilename, languageOverride, DocumentFormat.EPUB);
  }

  public AcceptedDocumentImport accept(
      Path stagedSource, String originalFilename, String languageOverride, DocumentFormat format) {
    var userId = currentUser.requireUserId();
    users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    var sourceSha256 = sha256(stagedSource);
    rejectExisting(userId, sourceSha256);
    var now = LocalDateTime.now(clock);
    var safeName = safeFilename(originalFilename);
    ImportedDocument processing;
    try {
      processing =
          documents.saveDocument(
              new ImportedDocument(
                  null,
                  userId,
                  title(safeName),
                  null,
                  placeholderLanguage(languageOverride),
                  format,
                  null,
                  null,
                  safeName,
                  sourceSha256,
                  DocumentImportStatus.PROCESSING,
                  DocumentChunker.VERSION,
                  now,
                  now));
    } catch (DataIntegrityViolationException exception) {
      throw duplicateAfterRace(userId, sourceSha256, exception);
    }
    var documentId = processing.id();
    String sourceKey = null;
    try {
      sourceKey = storage.storeSource(userId, documentId, stagedSource, format);
      processing = withSource(processing, sourceKey);
      processing = documents.saveDocument(processing);
      long bytes = Files.size(stagedSource);
      meters.counter("documents.imports", "outcome", "accepted").increment();
      meters.summary("documents.import.source.bytes").record(bytes);
      var submitted = processing;
      executor.execute(() -> process(submitted, languageOverride));
      return new AcceptedDocumentImport(documentId, DocumentImportStatus.PROCESSING);
    } catch (RejectedExecutionException exception) {
      if (sourceKey != null) storage.delete(sourceKey);
      fail(processing, DocumentImportException.Reason.IMPORT_FAILURE);
      throw new ImportCapacityExceededException();
    } catch (IOException | RuntimeException exception) {
      if (sourceKey != null) storage.delete(sourceKey);
      fail(processing, reason(exception));
      if (exception instanceof RuntimeException runtime) throw runtime;
      throw new DocumentImportException(
          DocumentImportException.Reason.IMPORT_FAILURE, "Unable to accept document", exception);
    }
  }

  private void rejectExisting(UUID userId, String sourceSha256) {
    documents
        .findByOwnerAndSourceSha256AndStatusIn(
            userId,
            sourceSha256,
            Set.of(DocumentImportStatus.PROCESSING, DocumentImportStatus.READY))
        .ifPresent(
            existing -> {
              throw new DocumentAlreadyImportedException(existing.id(), existing.importStatus());
            });
  }

  private DocumentAlreadyImportedException duplicateAfterRace(
      UUID userId, String sourceSha256, DataIntegrityViolationException cause) {
    return documents
        .findByOwnerAndSourceSha256AndStatusIn(
            userId,
            sourceSha256,
            Set.of(DocumentImportStatus.PROCESSING, DocumentImportStatus.READY))
        .map(
            existing ->
                new DocumentAlreadyImportedException(existing.id(), existing.importStatus()))
        .orElseGet(
            () -> {
              var exception =
                  new DocumentAlreadyImportedException(null, DocumentImportStatus.PROCESSING);
              exception.addSuppressed(cause);
              return exception;
            });
  }

  private void process(ImportedDocument document, String override) {
    Timer.Sample sample = Timer.start(meters);
    try {
      var ready = processor.processAccepted(document, override, languagePolicy);
      meters.counter("documents.imports", "outcome", "ready").increment();
      meters.summary("documents.import.sections").record(documents.countSections(ready.id()));
      meters.summary("documents.import.units").record(documents.countUnits(ready.id()));
      LOG.info(
          "document_import_ready documentId={} userId={} format={} sections={} units={}",
          ready.id(),
          ready.ownerId(),
          ready.format(),
          documents.countSections(ready.id()),
          documents.countUnits(ready.id()));
    } catch (RuntimeException exception) {
      var reason = reason(exception);
      meters.counter("documents.imports", "outcome", "failed", "reason", reason.name()).increment();
      LOG.warn(
          "document_import_failed documentId={} userId={} reason={}",
          document.id(),
          document.ownerId(),
          reason);
    } finally {
      sample.stop(meters.timer("documents.import.duration"));
    }
  }

  private void fail(ImportedDocument document, DocumentImportException.Reason reason) {
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
  }

  private ImportedDocument withSource(ImportedDocument value, String sourceKey) {
    return new ImportedDocument(
        value.id(),
        value.ownerId(),
        value.title(),
        value.author(),
        value.language(),
        value.format(),
        null,
        sourceKey,
        value.originalFilename(),
        value.sourceSha256(),
        value.importStatus(),
        value.chunkingVersion(),
        value.createdAt(),
        LocalDateTime.now(clock));
  }

  private String safeFilename(String value) {
    if (value == null || value.isBlank()) return "document";
    var safe = Path.of(value.replace('\\', '/')).getFileName().toString().strip();
    return safe.length() > 500 ? safe.substring(0, 500) : safe;
  }

  private String title(String filename) {
    var title =
        filename
            .replaceFirst("(?i)\\.(epub|pdf)$", "")
            .replaceAll("_+", " ")
            .replaceAll("\\s+", " ")
            .strip();
    if (title.isEmpty()) title = "Untitled document";
    if (Character.isLowerCase(title.codePointAt(0))) {
      title = title.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + title.substring(1);
    }
    return title.length() > 300 ? title.substring(0, 300) : title;
  }

  private String placeholderLanguage(String override) {
    return override == null || override.isBlank() ? "und" : override.strip();
  }

  private String sha256(Path source) {
    try (var input = Files.newInputStream(source)) {
      var digest = MessageDigest.getInstance("SHA-256");
      var buffer = new byte[16384];
      for (int read; (read = input.read(buffer)) >= 0; )
        if (read > 0) digest.update(buffer, 0, read);
      return HexFormat.of().formatHex(digest.digest());
    } catch (Exception exception) {
      throw new DocumentImportException(
          DocumentImportException.Reason.IMPORT_FAILURE, "Unable to hash upload", exception);
    }
  }

  private DocumentImportException.Reason reason(Exception exception) {
    return exception instanceof DocumentImportException value
        ? value.reason()
        : DocumentImportException.Reason.IMPORT_FAILURE;
  }
}
