package com.soap.soap.infrastructure.rest;

import com.soap.soap.application.command.UpdateDocumentProgressCommand;
import com.soap.soap.application.model.DocumentProgressView;
import com.soap.soap.application.model.DocumentStructureView;
import com.soap.soap.application.model.DocumentUnitReaderData;
import com.soap.soap.application.model.DocumentView;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.DocumentAssetStoragePort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.usecase.AcceptDocumentImportUseCase;
import com.soap.soap.application.usecase.DeleteDocumentUseCase;
import com.soap.soap.application.usecase.DocumentProgressUseCase;
import com.soap.soap.application.usecase.DocumentQueryUseCase;
import com.soap.soap.domain.model.DocumentFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentRestController {
  private final AcceptDocumentImportUseCase imports;
  private final DocumentQueryUseCase queries;
  private final DocumentProgressUseCase progress;
  private final DeleteDocumentUseCase deletions;
  private final CurrentUserPort currentUser;
  private final ImportedDocumentRepositoryPort documents;
  private final DocumentAssetStoragePort storage;

  public DocumentRestController(
      AcceptDocumentImportUseCase imports,
      DocumentQueryUseCase queries,
      DocumentProgressUseCase progress,
      DeleteDocumentUseCase deletions,
      CurrentUserPort currentUser,
      ImportedDocumentRepositoryPort documents,
      DocumentAssetStoragePort storage) {
    this.imports = imports;
    this.queries = queries;
    this.progress = progress;
    this.deletions = deletions;
    this.currentUser = currentUser;
    this.documents = documents;
    this.storage = storage;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ImportResponse> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false) String languageOverride)
      throws IOException {
    var format = validateUpload(file);
    var staged =
        Files.createTempFile("document-upload-", format == DocumentFormat.PDF ? ".pdf" : ".epub");
    try (var input = file.getInputStream();
        var output = Files.newOutputStream(staged)) {
      input.transferTo(output);
    }
    try {
      var accepted = imports.accept(staged, file.getOriginalFilename(), languageOverride, format);
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(new ImportResponse(accepted.documentId(), accepted.status().name()));
    } finally {
      Files.deleteIfExists(staged);
    }
  }

  @GetMapping
  public PageResponse<DocumentResponse> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    var result = queries.list(page, size);
    return PageResponse.from(
        result, result.content().stream().map(DocumentResponse::from).toList());
  }

  @GetMapping("/{documentId}")
  public DocumentResponse get(@PathVariable UUID documentId) {
    return DocumentResponse.from(queries.get(documentId));
  }

  @DeleteMapping("/{documentId}")
  public ResponseEntity<Void> delete(@PathVariable UUID documentId) {
    deletions.delete(documentId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{documentId}/structure")
  public StructureResponse structure(@PathVariable UUID documentId) {
    return StructureResponse.from(queries.structure(documentId));
  }

  @GetMapping("/{documentId}/units/{unitId}")
  public UnitResponse unit(@PathVariable UUID documentId, @PathVariable UUID unitId) {
    return UnitResponse.from(queries.unit(documentId, unitId));
  }

  @GetMapping("/{documentId}/progress")
  public ProgressResponse getProgress(@PathVariable UUID documentId) {
    return ProgressResponse.from(progress.get(documentId));
  }

  @PutMapping("/{documentId}/progress")
  public ProgressResponse updateProgress(
      @PathVariable UUID documentId, @RequestBody ProgressRequest request) {
    return ProgressResponse.from(
        progress.update(
            new UpdateDocumentProgressCommand(
                documentId,
                request.currentUnitId(),
                request.completed(),
                request.expectedVersion())));
  }

  @GetMapping("/{documentId}/cover")
  public ResponseEntity<FileSystemResource> cover(@PathVariable UUID documentId)
      throws IOException {
    var userId = currentUser.requireUserId();
    var document =
        documents
            .findDocumentById(documentId)
            .filter(value -> value.ownerId().equals(userId))
            .orElseThrow(
                () ->
                    new com.soap.soap.application.exception.DocumentNotFoundException(documentId));
    if (document.coverAssetKey() == null) return ResponseEntity.notFound().build();
    var path = storage.locate(document.coverAssetKey());
    var mediaType =
        MediaType.parseMediaType(
            java.util.Objects.requireNonNullElse(
                Files.probeContentType(path), "application/octet-stream"));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(mediaType)
        .body(new FileSystemResource(path));
  }

  private DocumentFormat validateUpload(MultipartFile file) throws IOException {
    if (file.isEmpty()) throw new IllegalArgumentException("Document file is required");
    var filename = java.util.Objects.requireNonNullElse(file.getOriginalFilename(), "");
    var lowerName = filename.toLowerCase(java.util.Locale.ROOT);
    var format =
        lowerName.endsWith(".epub")
            ? DocumentFormat.EPUB
            : lowerName.endsWith(".pdf") ? DocumentFormat.PDF : null;
    if (format == null) throw new IllegalArgumentException("File must use .epub or .pdf");
    var type = file.getContentType();
    if (type != null && !type.equals("application/octet-stream")) {
      var expected = format == DocumentFormat.PDF ? "application/pdf" : "application/epub+zip";
      if (!type.equals(expected)) {
        throw new IllegalArgumentException("Unsupported document content type");
      }
    }
    try (var input = file.getInputStream()) {
      var magic = input.readNBytes(5);
      var pdf =
          magic.length == 5
              && magic[0] == '%'
              && magic[1] == 'P'
              && magic[2] == 'D'
              && magic[3] == 'F'
              && magic[4] == '-';
      var zip = magic.length >= 2 && magic[0] == 'P' && magic[1] == 'K';
      if ((format == DocumentFormat.PDF && !pdf) || (format == DocumentFormat.EPUB && !zip)) {
        throw new IllegalArgumentException("Document signature does not match its extension");
      }
    }
    return format;
  }

  public record ImportResponse(UUID documentId, String status) {}

  public record ProgressRequest(UUID currentUnitId, boolean completed, Long expectedVersion) {}

  public record ProgressResponse(
      UUID documentId,
      UUID currentUnitId,
      String status,
      java.time.LocalDateTime startedAt,
      java.time.LocalDateTime lastReadAt,
      java.time.LocalDateTime completedAt,
      Long version) {
    static ProgressResponse from(DocumentProgressView value) {
      return new ProgressResponse(
          value.documentId(),
          value.currentUnitId(),
          value.status(),
          value.startedAt(),
          value.lastReadAt(),
          value.completedAt(),
          value.version());
    }
  }

  public record StructureResponse(
      UUID documentId,
      UUID firstUnitId,
      java.util.List<SectionResponse> sections,
      long totalUnits) {
    static StructureResponse from(DocumentStructureView value) {
      return new StructureResponse(
          value.documentId(),
          value.firstUnitId(),
          value.sections().stream()
              .map(
                  section ->
                      new SectionResponse(
                          section.id(),
                          section.ordinal(),
                          section.title(),
                          section.firstUnitId(),
                          section.unitCount()))
              .toList(),
          value.totalUnits());
    }
  }

  public record SectionResponse(
      UUID id, int ordinal, String title, UUID firstUnitId, long unitCount) {}

  public record UnitResponse(
      UUID documentId,
      UUID sectionId,
      String sectionTitle,
      int sectionOrdinal,
      int totalSections,
      UUID unitId,
      int sectionUnitOrdinal,
      int sectionUnitCount,
      int globalOrdinal,
      int totalUnits,
      UUID previousUnitId,
      UUID nextUnitId,
      String content,
      java.util.List<TokenResponse> tokens,
      String progressStatus) {
    static UnitResponse from(DocumentUnitReaderData value) {
      return new UnitResponse(
          value.documentId(),
          value.sectionId(),
          value.sectionTitle(),
          value.sectionOrdinal(),
          value.totalSections(),
          value.unitId(),
          value.sectionUnitOrdinal(),
          value.sectionUnitCount(),
          value.globalOrdinal(),
          value.totalUnits(),
          value.previousUnitId(),
          value.nextUnitId(),
          value.content(),
          value.tokens().stream().map(TokenResponse::from).toList(),
          value.progressStatus() == null ? "NOT_STARTED" : value.progressStatus().name());
    }
  }

  public record TokenResponse(
      String value, String normalizedValue, String type, String vocabularyStatus) {
    static TokenResponse from(com.soap.soap.application.model.ReaderToken value) {
      return new TokenResponse(
          value.value(),
          value.normalizedValue(),
          value.type().name(),
          value.status() == null ? "NEW" : value.status().name());
    }
  }

  public record DocumentResponse(
      UUID documentId,
      String title,
      String author,
      String language,
      String format,
      String status,
      String failureReason,
      boolean coverAvailable,
      String coverUrl,
      long totalSections,
      long totalUnits,
      String progressStatus,
      java.time.LocalDateTime lastReadAt,
      java.time.LocalDateTime createdAt,
      java.time.LocalDateTime updatedAt) {
    static DocumentResponse from(DocumentView value) {
      return new DocumentResponse(
          value.id(),
          value.title(),
          value.author(),
          value.language(),
          value.format().name(),
          value.status().name(),
          value.failureReason(),
          value.coverAvailable(),
          value.coverAvailable() ? "/api/v1/documents/" + value.id() + "/cover" : null,
          value.totalSections(),
          value.totalUnits(),
          value.progressStatus() == null ? "NOT_STARTED" : value.progressStatus().name(),
          value.lastReadAt(),
          value.createdAt(),
          value.updatedAt());
    }
  }

  public record PageResponse<T>(
      java.util.List<T> content, int page, int size, long totalElements, int totalPages) {
    static <T> PageResponse<T> from(PageResult<?> page, java.util.List<T> content) {
      return new PageResponse<>(
          content, page.page(), page.size(), page.totalElements(), page.totalPages());
    }
  }
}
