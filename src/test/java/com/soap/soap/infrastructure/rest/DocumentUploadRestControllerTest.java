package com.soap.soap.infrastructure.rest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.soap.soap.application.command.CreateDocumentUploadIntentCommand;
import com.soap.soap.application.model.ConfirmVerifiedDocumentUploadResult;
import com.soap.soap.application.model.DocumentUploadIntentResult;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.service.ConfirmDocumentUploadOrchestrator;
import com.soap.soap.application.usecase.CreateDocumentUploadIntentUseCase;
import com.soap.soap.application.usecase.RefreshDocumentUploadPresignUseCase;
import com.soap.soap.domain.model.DocumentImportStatus;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DocumentUploadRestControllerTest {

  @Mock private CreateDocumentUploadIntentUseCase createIntentUseCase;
  @Mock private RefreshDocumentUploadPresignUseCase refreshPresignUseCase;
  @Mock private ConfirmDocumentUploadOrchestrator confirmOrchestrator;
  @Mock private CurrentUserPort currentUser;

  private MockMvc mvc;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    when(currentUser.requireUserId()).thenReturn(userId);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new DocumentUploadRestController(
                    createIntentUseCase, refreshPresignUseCase, confirmOrchestrator, currentUser))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
  }

  @Test
  @DisplayName(
      "Contract: POST /api/v1/documents/uploads validates input fields and returns frozen response schema")
  void createIntentContractValidation() throws Exception {
    UUID uploadId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    URI uploadUrl =
        URI.create("https://s3.amazonaws.com/test-bucket/documents/test.epub?signature=xyz");
    Instant expiresAt = Instant.parse("2026-09-24T02:00:00Z");
    Map<String, String> requiredHeaders =
        Map.of(
            "Content-Type", "application/epub+zip",
            "If-None-Match", "*",
            "x-amz-checksum-sha256", "FSrMjewAiPH/Q2ClxCrSaxdy7G6LI4S16EOLxVi2wLY=");

    when(createIntentUseCase.createIntent(any(CreateDocumentUploadIntentCommand.class)))
        .thenReturn(
            new DocumentUploadIntentResult(
                uploadId, documentId, uploadUrl, "PUT", expiresAt, requiredHeaders));

    String requestJson =
        """
        {
          "fileName": "war_and_peace.epub",
          "contentType": "application/epub+zip",
          "sizeBytes": 1048576,
          "checksumSha256": "1522ac8edc8088f1ff4360a5c429326b0772ec6e8b2384b5e8438bc858b6c0b6"
        }
        """;

    mvc.perform(
            post("/api/v1/documents/uploads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isCreated())
        // Positive assertions on frozen contract field names
        .andExpect(jsonPath("$.uploadId").value(uploadId.toString()))
        .andExpect(jsonPath("$.documentId").value(documentId.toString()))
        .andExpect(jsonPath("$.uploadUrl").value(uploadUrl.toString()))
        .andExpect(jsonPath("$.method").value("PUT"))
        .andExpect(jsonPath("$.expiresAt").value("2026-09-24T02:00:00Z"))
        .andExpect(jsonPath("$.requiredHeaders['Content-Type']").value("application/epub+zip"))
        .andExpect(jsonPath("$.requiredHeaders['If-None-Match']").value("*"))
        .andExpect(
            jsonPath("$.requiredHeaders['x-amz-checksum-sha256']")
                .value("FSrMjewAiPH/Q2ClxCrSaxdy7G6LI4S16EOLxVi2wLY="))
        // Negative assertions: forbidden legacy/divergent names must NOT appear in response body
        .andExpect(content().string(not(containsString("\"httpMethod\""))))
        .andExpect(content().string(not(containsString("\"filename\""))))
        .andExpect(content().string(not(containsString("\"sha256\""))));

    ArgumentCaptor<CreateDocumentUploadIntentCommand> cmdCaptor =
        ArgumentCaptor.forClass(CreateDocumentUploadIntentCommand.class);
    verify(createIntentUseCase).createIntent(cmdCaptor.capture());
    var cmd = cmdCaptor.getValue();
    org.assertj.core.api.Assertions.assertThat(cmd.fileName()).isEqualTo("war_and_peace.epub");
    org.assertj.core.api.Assertions.assertThat(cmd.checksumSha256())
        .isEqualTo("1522ac8edc8088f1ff4360a5c429326b0772ec6e8b2384b5e8438bc858b6c0b6");
  }

  @Test
  @DisplayName(
      "Contract: POST /api/v1/documents/uploads/{uploadId}/presign returns frozen refresh schema")
  void refreshPresignContractValidation() throws Exception {
    UUID uploadId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    URI uploadUrl =
        URI.create("https://s3.amazonaws.com/test-bucket/documents/test.epub?signature=new");
    Instant expiresAt = Instant.parse("2026-09-24T03:00:00Z");
    Map<String, String> requiredHeaders =
        Map.of(
            "Content-Type", "application/epub+zip",
            "If-None-Match", "*",
            "x-amz-checksum-sha256", "FSrMjewAiPH/Q2ClxCrSaxdy7G6LI4S16EOLxVi2wLY=");

    when(refreshPresignUseCase.refreshPresign(eq(uploadId), eq(userId)))
        .thenReturn(
            new DocumentUploadIntentResult(
                uploadId, documentId, uploadUrl, "PUT", expiresAt, requiredHeaders));

    mvc.perform(post("/api/v1/documents/uploads/{uploadId}/presign", uploadId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uploadUrl").value(uploadUrl.toString()))
        .andExpect(jsonPath("$.method").value("PUT"))
        .andExpect(jsonPath("$.expiresAt").value("2026-09-24T03:00:00Z"))
        .andExpect(jsonPath("$.requiredHeaders['If-None-Match']").value("*"))
        .andExpect(content().string(not(containsString("\"httpMethod\""))));
  }

  @Test
  @DisplayName(
      "Contract: POST /api/v1/documents/uploads/{uploadId}/confirm accepts languageOverride and returns jobId & uploadStatus")
  void confirmContractValidation() throws Exception {
    UUID uploadId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();

    when(confirmOrchestrator.confirmUpload(eq(uploadId), eq(userId), eq("es")))
        .thenReturn(
            new ConfirmVerifiedDocumentUploadResult(
                uploadId, documentId, jobId, DocumentImportStatus.PROCESSING));

    String requestJson =
        """
        {
          "languageOverride": "es"
        }
        """;

    mvc.perform(
            post("/api/v1/documents/uploads/{uploadId}/confirm", uploadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        // Positive assertions
        .andExpect(jsonPath("$.documentId").value(documentId.toString()))
        .andExpect(jsonPath("$.jobId").value(jobId.toString()))
        .andExpect(jsonPath("$.status").value("PROCESSING"))
        .andExpect(jsonPath("$.uploadStatus").value("CONFIRMED"))
        // Negative assertions: forbidden divergent names must NOT appear
        .andExpect(content().string(not(containsString("\"importJobId\""))))
        .andExpect(content().string(not(containsString("\"language\""))));
  }
}
