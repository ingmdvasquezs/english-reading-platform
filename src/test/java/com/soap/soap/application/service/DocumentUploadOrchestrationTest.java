package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.StorageChecksumUnavailableException;
import com.soap.soap.application.exception.StorageObjectNotFoundException;
import com.soap.soap.application.exception.TransientStorageException;
import com.soap.soap.application.exception.UploadExpiredException;
import com.soap.soap.application.exception.UploadIntegrityMismatchException;
import com.soap.soap.application.exception.UploadNotCompletedException;
import com.soap.soap.application.exception.UploadNotFoundException;
import com.soap.soap.application.model.ConfirmVerifiedDocumentUploadResult;
import com.soap.soap.application.model.DocumentImportLimits;
import com.soap.soap.application.port.out.DocumentObjectStoragePort;
import com.soap.soap.application.port.out.DocumentUploadRepositoryPort;
import com.soap.soap.application.usecase.ConfirmVerifiedDocumentUploadUseCase;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.DocumentUpload;
import com.soap.soap.domain.model.DocumentUploadStatus;
import com.soap.soap.domain.model.StorageProvider;
import com.soap.soap.domain.model.StoredObjectAttributes;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentUploadOrchestrationTest {

  private static final UUID USER_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID UPLOAD_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID DOC_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final String STORAGE_KEY = "documents/" + USER_A + "/" + DOC_ID + "/source.epub";
  private static final String SHA256_A = "a".repeat(64);
  private static final String SHA256_B = "b".repeat(64);

  private DocumentUploadRepositoryPort documentUploads;
  private DocumentObjectStoragePort storagePort;
  private ConfirmVerifiedDocumentUploadUseCase confirmVerifiedUseCase;
  private DocumentImportLimits limits;
  private Clock clock;
  private ConfirmDocumentUploadOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    documentUploads = mock(DocumentUploadRepositoryPort.class);
    storagePort = mock(DocumentObjectStoragePort.class);
    confirmVerifiedUseCase = mock(ConfirmVerifiedDocumentUploadUseCase.class);
    limits =
        new DocumentImportLimits(
            52428800L, 2000, 10485760L, 157286400L, 100, 10485760L, 25000000, 1000, 5000000);
    clock = Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"), ZoneOffset.UTC);
    orchestrator =
        new ConfirmDocumentUploadOrchestrator(
            documentUploads, storagePort, confirmVerifiedUseCase, limits, clock);
  }

  private DocumentUpload createUpload(DocumentUploadStatus status, LocalDateTime expiresAt) {
    LocalDateTime now = LocalDateTime.now(clock);
    return new DocumentUpload(
        UPLOAD_ID,
        DOC_ID,
        USER_A,
        "book.epub",
        DocumentFormat.EPUB,
        "application/epub+zip",
        1000L,
        SHA256_A,
        STORAGE_KEY,
        StorageProvider.S3,
        status,
        expiresAt != null ? expiresAt : now.plusHours(24),
        null,
        null,
        now,
        now,
        0);
  }

  @Test
  @DisplayName(
      "S12 & S33: Confirm without object in S3 throws UploadNotCompletedException and keeps upload PENDING")
  void s12_s33_confirmWithoutObjectKeepsPending() {
    DocumentUpload upload = createUpload(DocumentUploadStatus.PENDING, null);
    when(documentUploads.findById(UPLOAD_ID)).thenReturn(Optional.of(upload));
    when(storagePort.inspectObject(STORAGE_KEY))
        .thenThrow(new StorageObjectNotFoundException(STORAGE_KEY));

    assertThatThrownBy(() -> orchestrator.confirmUpload(UPLOAD_ID, USER_A, "en"))
        .isInstanceOf(UploadNotCompletedException.class);

    verify(confirmVerifiedUseCase, never()).confirm(any());
    verify(documentUploads, never()).abort(any(), any());
    verify(storagePort, never()).deleteObject(any());
  }

  @Test
  @DisplayName(
      "S13: Confirm size mismatch marks upload ABORTED, deletes S3 object, throws UploadIntegrityMismatchException")
  void s13_confirmSizeMismatchAbortsAndDeletes() {
    DocumentUpload upload = createUpload(DocumentUploadStatus.PENDING, null);
    when(documentUploads.findById(UPLOAD_ID)).thenReturn(Optional.of(upload));
    when(storagePort.inspectObject(STORAGE_KEY))
        .thenReturn(new StoredObjectAttributes(STORAGE_KEY, 2000L, SHA256_A, "etag"));

    assertThatThrownBy(() -> orchestrator.confirmUpload(UPLOAD_ID, USER_A, "en"))
        .isInstanceOf(UploadIntegrityMismatchException.class)
        .hasMessageContaining("Actual size (2000) does not match expected size (1000)");

    verify(documentUploads).abort(UPLOAD_ID, USER_A);
    verify(storagePort).deleteObject(STORAGE_KEY);
    verify(confirmVerifiedUseCase, never()).confirm(any());
  }

  @Test
  @DisplayName(
      "S14: Confirm checksum mismatch marks upload ABORTED, deletes S3 object, throws UploadIntegrityMismatchException")
  void s14_confirmChecksumMismatchAbortsAndDeletes() {
    DocumentUpload upload = createUpload(DocumentUploadStatus.PENDING, null);
    when(documentUploads.findById(UPLOAD_ID)).thenReturn(Optional.of(upload));
    when(storagePort.inspectObject(STORAGE_KEY))
        .thenReturn(new StoredObjectAttributes(STORAGE_KEY, 1000L, SHA256_B, "etag"));

    assertThatThrownBy(() -> orchestrator.confirmUpload(UPLOAD_ID, USER_A, "en"))
        .isInstanceOf(UploadIntegrityMismatchException.class)
        .hasMessageContaining(
            "Actual checksum ("
                + SHA256_B
                + ") does not match expected checksum ("
                + SHA256_A
                + ")");

    verify(documentUploads).abort(UPLOAD_ID, USER_A);
    verify(storagePort).deleteObject(STORAGE_KEY);
    verify(confirmVerifiedUseCase, never()).confirm(any());
  }

  @Test
  @DisplayName(
      "S14b: Confirm with null checksum from storage throws StorageChecksumUnavailableException without aborting upload or deleting object")
  void s14b_nullChecksumThrowsStorageChecksumUnavailableException() {
    DocumentUpload upload = createUpload(DocumentUploadStatus.PENDING, null);
    when(documentUploads.findById(UPLOAD_ID)).thenReturn(Optional.of(upload));
    when(storagePort.inspectObject(STORAGE_KEY))
        .thenReturn(new StoredObjectAttributes(STORAGE_KEY, 1000L, null, "etag"));

    assertThatThrownBy(() -> orchestrator.confirmUpload(UPLOAD_ID, USER_A, "en"))
        .isInstanceOf(StorageChecksumUnavailableException.class);

    verify(documentUploads, never()).abort(any(), any());
    verify(storagePort, never()).deleteObject(any());
    verify(confirmVerifiedUseCase, never()).confirm(any());
  }

  @Test
  @DisplayName(
      "S16: Repeated confirm on already CONFIRMED upload is idempotent and returns same IDs")
  void s16_repeatedConfirmIsIdempotent() {
    UUID jobId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.now(clock);
    DocumentUpload confirmed =
        new DocumentUpload(
            UPLOAD_ID,
            DOC_ID,
            USER_A,
            "book.epub",
            DocumentFormat.EPUB,
            "application/epub+zip",
            1000L,
            SHA256_A,
            STORAGE_KEY,
            StorageProvider.S3,
            DocumentUploadStatus.CONFIRMED,
            now.plusHours(24),
            now,
            jobId,
            now,
            now,
            1);

    when(documentUploads.findById(UPLOAD_ID)).thenReturn(Optional.of(confirmed));

    ConfirmVerifiedDocumentUploadResult result =
        orchestrator.confirmUpload(UPLOAD_ID, USER_A, "en");

    assertThat(result.documentId()).isEqualTo(DOC_ID);
    assertThat(result.jobId()).isEqualTo(jobId);
    assertThat(result.status()).isEqualTo(DocumentImportStatus.PROCESSING);

    // No S3 inspect or abort called for already confirmed upload
    verify(storagePort, never()).inspectObject(any());
    verify(confirmVerifiedUseCase, never()).confirm(any());
  }

  @Test
  @DisplayName("S19: User B cannot access or confirm User A's upload")
  void s19_userBCannotAccessUserAUpload() {
    DocumentUpload upload = createUpload(DocumentUploadStatus.PENDING, null);
    when(documentUploads.findById(UPLOAD_ID)).thenReturn(Optional.of(upload));

    assertThatThrownBy(() -> orchestrator.confirmUpload(UPLOAD_ID, USER_B, "en"))
        .isInstanceOf(UploadNotFoundException.class);

    verify(storagePort, never()).inspectObject(any());
    verify(confirmVerifiedUseCase, never()).confirm(any());
  }

  @Test
  @DisplayName("S20: Expired upload intent cannot be confirmed")
  void s20_expiredIntentCannotBeConfirmed() {
    LocalDateTime expiredAt = LocalDateTime.now(clock).minusMinutes(5);
    DocumentUpload upload = createUpload(DocumentUploadStatus.PENDING, expiredAt);
    when(documentUploads.findById(UPLOAD_ID)).thenReturn(Optional.of(upload));

    assertThatThrownBy(() -> orchestrator.confirmUpload(UPLOAD_ID, USER_A, "en"))
        .isInstanceOf(UploadExpiredException.class);

    verify(storagePort, never()).inspectObject(any());
    verify(confirmVerifiedUseCase, never()).confirm(any());
  }

  @Test
  @DisplayName("S21: Transient storage error does NOT abort or delete, keeps upload PENDING")
  void s21_transientStorageErrorKeepsPending() {
    DocumentUpload upload = createUpload(DocumentUploadStatus.PENDING, null);
    when(documentUploads.findById(UPLOAD_ID)).thenReturn(Optional.of(upload));
    when(storagePort.inspectObject(STORAGE_KEY))
        .thenThrow(new TransientStorageException("S3 503 Slow Down", null));

    assertThatThrownBy(() -> orchestrator.confirmUpload(UPLOAD_ID, USER_A, "en"))
        .isInstanceOf(TransientStorageException.class);

    verify(documentUploads, never()).abort(any(), any());
    verify(storagePort, never()).deleteObject(any());
    verify(confirmVerifiedUseCase, never()).confirm(any());
  }
}
