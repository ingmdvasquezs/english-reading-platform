package com.soap.soap.infrastructure.rest;

import com.soap.soap.application.command.CreateDocumentUploadIntentCommand;
import com.soap.soap.application.model.ConfirmVerifiedDocumentUploadResult;
import com.soap.soap.application.model.DocumentUploadIntentResult;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.service.ConfirmDocumentUploadOrchestrator;
import com.soap.soap.application.usecase.CreateDocumentUploadIntentUseCase;
import com.soap.soap.application.usecase.RefreshDocumentUploadPresignUseCase;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents/uploads")
public class DocumentUploadRestController {

  private final CreateDocumentUploadIntentUseCase createIntentUseCase;
  private final RefreshDocumentUploadPresignUseCase refreshPresignUseCase;
  private final ConfirmDocumentUploadOrchestrator confirmOrchestrator;
  private final CurrentUserPort currentUser;

  public DocumentUploadRestController(
      CreateDocumentUploadIntentUseCase createIntentUseCase,
      RefreshDocumentUploadPresignUseCase refreshPresignUseCase,
      ConfirmDocumentUploadOrchestrator confirmOrchestrator,
      CurrentUserPort currentUser) {
    this.createIntentUseCase = createIntentUseCase;
    this.refreshPresignUseCase = refreshPresignUseCase;
    this.confirmOrchestrator = confirmOrchestrator;
    this.currentUser = currentUser;
  }

  @PostMapping
  public ResponseEntity<UploadIntentResponse> createIntent(
      @RequestBody CreateUploadIntentRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("Request body must not be null");
    }
    if (request.fileName() == null || request.fileName().isBlank()) {
      throw new IllegalArgumentException("fileName is required");
    }
    if (request.sizeBytes() == null) {
      throw new IllegalArgumentException("sizeBytes is required");
    }
    if (request.checksumSha256() == null || request.checksumSha256().isBlank()) {
      throw new IllegalArgumentException("checksumSha256 is required");
    }

    UUID userId = currentUser.requireUserId();
    CreateDocumentUploadIntentCommand command =
        new CreateDocumentUploadIntentCommand(
            userId,
            request.fileName(),
            request.contentType(),
            request.sizeBytes(),
            request.checksumSha256());

    DocumentUploadIntentResult result = createIntentUseCase.createIntent(command);
    return ResponseEntity.status(HttpStatus.CREATED).body(UploadIntentResponse.from(result));
  }

  @PostMapping("/{uploadId}/presign")
  public ResponseEntity<UploadIntentResponse> refreshPresign(@PathVariable UUID uploadId) {
    UUID userId = currentUser.requireUserId();
    DocumentUploadIntentResult result = refreshPresignUseCase.refreshPresign(uploadId, userId);
    return ResponseEntity.ok(UploadIntentResponse.from(result));
  }

  @PostMapping("/{uploadId}/confirm")
  public ResponseEntity<ConfirmUploadResponse> confirm(
      @PathVariable UUID uploadId, @RequestBody(required = false) ConfirmUploadRequest request) {
    UUID userId = currentUser.requireUserId();
    String languageOverride = request != null ? request.languageOverride() : null;

    ConfirmVerifiedDocumentUploadResult result =
        confirmOrchestrator.confirmUpload(uploadId, userId, languageOverride);

    return ResponseEntity.ok(
        new ConfirmUploadResponse(
            result.documentId(), result.jobId(), result.status().name(), "CONFIRMED"));
  }

  public record CreateUploadIntentRequest(
      String fileName, String contentType, Long sizeBytes, String checksumSha256) {}

  public record ConfirmUploadRequest(String languageOverride) {}

  public record ConfirmUploadResponse(
      UUID documentId, UUID jobId, String status, String uploadStatus) {}

  public record UploadIntentResponse(
      UUID uploadId,
      UUID documentId,
      String uploadUrl,
      String method,
      Instant expiresAt,
      Map<String, String> requiredHeaders) {

    public static UploadIntentResponse from(DocumentUploadIntentResult result) {
      return new UploadIntentResponse(
          result.uploadId(),
          result.documentId(),
          result.uploadUrl().toString(),
          result.method(),
          result.expiresAt(),
          result.requiredHeaders());
    }
  }
}
