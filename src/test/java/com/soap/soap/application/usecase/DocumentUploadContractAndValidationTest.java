package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.CreateDocumentUploadIntentCommand;
import com.soap.soap.application.model.DocumentImportLimits;
import com.soap.soap.application.model.DocumentUploadIntentResult;
import com.soap.soap.application.port.out.DocumentObjectStoragePort;
import com.soap.soap.application.port.out.DocumentUploadRepositoryPort;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentUpload;
import com.soap.soap.domain.model.DocumentUploadStatus;
import com.soap.soap.domain.model.StorageProvider;
import com.soap.soap.domain.model.UploadAuthorization;
import com.soap.soap.infrastructure.storage.S3DocumentObjectStorageAdapter;
import com.soap.soap.infrastructure.storage.S3DocumentStorageProperties;
import java.net.URI;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class DocumentUploadContractAndValidationTest {

  private static final String VALID_SHA256 =
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private DocumentUploadRepositoryPort documentUploads;
  private DocumentObjectStoragePort storagePort;
  private DocumentImportLimits limits;
  private S3DocumentStorageProperties properties;
  private Clock clock;
  private CreateDocumentUploadIntentUseCase useCase;

  @BeforeEach
  void setUp() {
    documentUploads = mock(DocumentUploadRepositoryPort.class);
    storagePort = mock(DocumentObjectStoragePort.class);
    limits =
        new DocumentImportLimits(
            52428800L, 2000, 10485760L, 157286400L, 100, 10485760L, 25000000, 1000, 5000000);
    properties =
        new S3DocumentStorageProperties(
            StorageProvider.S3,
            Duration.ofHours(24),
            Duration.ofMinutes(10),
            new S3DocumentStorageProperties.S3Properties(
                "english-reading-test-bucket", "us-east-1", null, false));
    clock = Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"), ZoneOffset.UTC);
    useCase =
        new CreateDocumentUploadIntentUseCase(
            documentUploads, storagePort, limits, properties, clock);
  }

  @Test
  @DisplayName("S01: Valid intent returns expected fields and 201 contract")
  void s01_createIntentValid() {
    var auth =
        new UploadAuthorization(
            URI.create("https://s3.us-east-1.amazonaws.com/test-bucket/test-key?signature=xyz"),
            "PUT",
            Map.of("Content-Type", "application/epub+zip", "If-None-Match", "*"),
            Instant.parse("2026-09-24T12:10:00Z"));

    when(storagePort.createUploadAuthorization(any(), any(), any(), any())).thenReturn(auth);

    var command =
        new CreateDocumentUploadIntentCommand(
            USER_ID, "hamlet.epub", "application/epub+zip", 123456L, VALID_SHA256);

    DocumentUploadIntentResult result = useCase.createIntent(command);

    assertThat(result.uploadId()).isNotNull();
    assertThat(result.documentId()).isNotNull();
    assertThat(result.uploadUrl()).isEqualTo(auth.uploadUrl());
    assertThat(result.method()).isEqualTo("PUT");
    assertThat(result.expiresAt()).isEqualTo(auth.expiresAt());
    assertThat(result.requiredHeaders()).containsEntry("Content-Type", "application/epub+zip");
    assertThat(result.requiredHeaders()).containsEntry("If-None-Match", "*");
  }

  @Test
  @DisplayName("S02: Generated storage key is strictly server-owned")
  void s02_generatedKeyServerOwned() {
    var auth =
        new UploadAuthorization(
            URI.create("https://example.invalid"), "PUT", Collections.emptyMap(), Instant.now());
    when(storagePort.createUploadAuthorization(any(), any(), any(), any())).thenReturn(auth);

    var command =
        new CreateDocumentUploadIntentCommand(
            USER_ID, "../../../etc/passwd/malicious.pdf", null, 1000L, VALID_SHA256);

    DocumentUploadIntentResult result = useCase.createIntent(command);

    var captor = ArgumentCaptor.forClass(DocumentUpload.class);
    verify(documentUploads).save(captor.capture());
    DocumentUpload saved = captor.getValue();

    assertThat(saved.originalFilename()).isEqualTo("malicious.pdf");
    assertThat(saved.storageKey())
        .isEqualTo("documents/" + USER_ID + "/" + result.documentId() + "/source.pdf");
    assertThat(saved.storageKey()).doesNotContain("..");
    assertThat(saved.format()).isEqualTo(DocumentFormat.PDF);
    assertThat(saved.storageProvider()).isEqualTo(StorageProvider.S3);
    assertThat(saved.status()).isEqualTo(DocumentUploadStatus.PENDING);
  }

  @Test
  @DisplayName("S03: Size > maxSourceBytes rejected with IllegalArgumentException")
  void s03_sizeTooLargeRejected() {
    var command =
        new CreateDocumentUploadIntentCommand(
            USER_ID, "huge.epub", "application/epub+zip", 52428801L, VALID_SHA256);

    assertThatThrownBy(() -> useCase.createIntent(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeds maximum limit");
  }

  @Test
  @DisplayName(
      "S03b: Boundary size test - exactly maxSourceBytes (52428800) accepted, max+1 (52428801) rejected")
  void s03_boundarySizeCheck() {
    var auth =
        new UploadAuthorization(
            URI.create("https://example.invalid"), "PUT", Collections.emptyMap(), Instant.now());
    when(storagePort.createUploadAuthorization(any(), any(), any(), any())).thenReturn(auth);

    var maxExactCmd =
        new CreateDocumentUploadIntentCommand(
            USER_ID, "max.epub", "application/epub+zip", 52428800L, VALID_SHA256);
    assertThat(useCase.createIntent(maxExactCmd)).isNotNull();

    var maxPlusOneCmd =
        new CreateDocumentUploadIntentCommand(
            USER_ID, "maxplus.epub", "application/epub+zip", 52428801L, VALID_SHA256);
    assertThatThrownBy(() -> useCase.createIntent(maxPlusOneCmd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeds maximum limit");
  }

  @Test
  @DisplayName("S04: Size <= 0 rejected with IllegalArgumentException")
  void s04_sizeZeroOrNegativeRejected() {
    var zeroCmd =
        new CreateDocumentUploadIntentCommand(
            USER_ID, "zero.epub", "application/epub+zip", 0L, VALID_SHA256);
    assertThatThrownBy(() -> useCase.createIntent(zeroCmd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sizeBytes must be strictly positive");

    var negCmd =
        new CreateDocumentUploadIntentCommand(
            USER_ID, "neg.epub", "application/epub+zip", -5L, VALID_SHA256);
    assertThatThrownBy(() -> useCase.createIntent(negCmd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sizeBytes must be strictly positive");
  }

  @Test
  @DisplayName("S05: Invalid SHA-256 syntax rejected with IllegalArgumentException")
  void s05_invalidSha256Rejected() {
    var shortSha =
        new CreateDocumentUploadIntentCommand(
            USER_ID, "book.epub", "application/epub+zip", 100L, "abc123");
    assertThatThrownBy(() -> useCase.createIntent(shortSha))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("checksumSha256 must contain exactly 64 lowercase hexadecimal");

    var nonHex =
        new CreateDocumentUploadIntentCommand(
            USER_ID, "book.epub", "application/epub+zip", 100L, "g".repeat(64));
    assertThatThrownBy(() -> useCase.createIntent(nonHex))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("checksumSha256 must contain exactly 64 lowercase hexadecimal");
  }

  @Test
  @DisplayName("S06: Unsupported file extension rejected with IllegalArgumentException")
  void s06_unsupportedExtensionRejected() {
    var txtCmd =
        new CreateDocumentUploadIntentCommand(
            USER_ID, "notes.txt", "text/plain", 100L, VALID_SHA256);
    assertThatThrownBy(() -> useCase.createIntent(txtCmd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Only .epub and .pdf files are supported");

    var exeCmd =
        new CreateDocumentUploadIntentCommand(
            USER_ID, "payload.exe", "application/octet-stream", 100L, VALID_SHA256);
    assertThatThrownBy(() -> useCase.createIntent(exeCmd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Only .epub and .pdf files are supported");
  }

  @Test
  @DisplayName("S28: requiredHeaders strictly excludes host, authorization, content-length")
  void s28_requiredHeadersExcludesForbiddenBrowserHeaders() {
    S3Client mockS3 = mock(S3Client.class);
    S3Presigner mockPresigner = mock(S3Presigner.class);
    var adapter = new S3DocumentObjectStorageAdapter(mockS3, mockPresigner, properties);

    PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
    try {
      when(presigned.url()).thenReturn(new URL("https://s3.amazonaws.com/test-bucket/test-key"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    when(presigned.expiration()).thenReturn(Instant.now().plusSeconds(600));
    ArgumentCaptor<PutObjectPresignRequest> presignCaptor =
        ArgumentCaptor.forClass(PutObjectPresignRequest.class);
    when(mockPresigner.presignPutObject(presignCaptor.capture())).thenReturn(presigned);

    UploadAuthorization auth =
        adapter.createUploadAuthorization(
            "documents/123/456/source.epub",
            "application/epub+zip",
            VALID_SHA256,
            Duration.ofMinutes(10));

    PutObjectPresignRequest captured = presignCaptor.getValue();
    assertThat(captured.putObjectRequest().contentLength()).isNull();
    assertThat(captured.putObjectRequest().ifNoneMatch()).isEqualTo("*");

    Map<String, String> headers = auth.requiredHeaders();
    assertThat(headers).containsKey("Content-Type");
    assertThat(headers).containsKey("x-amz-checksum-sha256");
    assertThat(headers).containsKey("If-None-Match");

    // Must NOT contain browser-controlled headers
    assertThat(headers.keySet().stream().map(String::toLowerCase))
        .doesNotContain("host", "authorization", "content-length");
  }

  @Test
  @DisplayName("S29: Canonical Content-Type derived server-side from extension")
  void s29_canonicalContentTypeDerived() {
    var auth =
        new UploadAuthorization(
            URI.create("https://example.invalid"), "PUT", Collections.emptyMap(), Instant.now());
    when(storagePort.createUploadAuthorization(any(), any(), any(), any())).thenReturn(auth);

    // .epub maps to application/epub+zip
    useCase.createIntent(
        new CreateDocumentUploadIntentCommand(
            USER_ID, "story.epub", "application/octet-stream", 1000L, VALID_SHA256));
    verify(storagePort)
        .createUploadAuthorization(any(), eq("application/epub+zip"), eq(VALID_SHA256), any());

    // .pdf maps to application/pdf
    useCase.createIntent(
        new CreateDocumentUploadIntentCommand(
            USER_ID, "paper.pdf", "application/octet-stream", 1000L, VALID_SHA256));
    verify(storagePort)
        .createUploadAuthorization(any(), eq("application/pdf"), eq(VALID_SHA256), any());
  }

  @Test
  @DisplayName("S23: Client request cannot inject external bucket or storage key")
  void s23_clientCannotInjectKeyOrBucket() {
    var auth =
        new UploadAuthorization(
            URI.create("https://example.invalid"), "PUT", Collections.emptyMap(), Instant.now());
    when(storagePort.createUploadAuthorization(any(), any(), any(), any())).thenReturn(auth);

    var command =
        new CreateDocumentUploadIntentCommand(
            USER_ID, "C:\\Windows\\System32\\calc.pdf", null, 1000L, VALID_SHA256);

    DocumentUploadIntentResult result = useCase.createIntent(command);
    var captor = ArgumentCaptor.forClass(DocumentUpload.class);
    verify(documentUploads).save(captor.capture());

    assertThat(captor.getValue().storageKey())
        .startsWith("documents/" + USER_ID + "/" + result.documentId() + "/source.pdf");
    assertThat(captor.getValue().storageKey()).doesNotContain("Windows");
  }
}
