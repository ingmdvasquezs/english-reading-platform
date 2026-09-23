package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.port.out.DocumentUploadRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentUpload;
import com.soap.soap.domain.model.DocumentUploadStatus;
import com.soap.soap.domain.model.User;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
@Transactional
class DocumentUploadPersistenceIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private DocumentUploadRepositoryPort documentUploads;
  @Autowired private UserRepositoryPort users;

  private User testUser;

  @BeforeEach
  void setUp() {
    testUser =
        users.save(
            new User(
                null, "Upload User", "upload-" + UUID.randomUUID() + "@example.com", "hash", null));
  }

  @Test
  @DisplayName("T02: Persist document_upload in PENDING status")
  void createsDocumentUploadInPendingStatus() {
    LocalDateTime now = LocalDateTime.now();
    UUID uploadId = UUID.randomUUID();
    UUID reservedDocId = UUID.randomUUID();

    DocumentUpload upload =
        new DocumentUpload(
            uploadId,
            reservedDocId,
            testUser.id(),
            "test_book.epub",
            DocumentFormat.EPUB,
            "application/epub+zip",
            102400L,
            "c".repeat(64),
            "uploads/" + uploadId + "/source.epub",
            DocumentUploadStatus.PENDING,
            now.plusMinutes(30),
            null,
            null,
            now,
            now,
            0);

    DocumentUpload saved = documentUploads.save(upload);
    assertThat(saved).isNotNull();
    assertThat(saved.id()).isEqualTo(uploadId);
    assertThat(saved.status()).isEqualTo(DocumentUploadStatus.PENDING);
    assertThat(saved.documentId()).isEqualTo(reservedDocId);
    assertThat(saved.isExpired(now)).isFalse();
    assertThat(saved.isConfirmed()).isFalse();

    var found = documentUploads.findById(uploadId);
    assertThat(found).isPresent();
    assertThat(found.get().originalFilename()).isEqualTo("test_book.epub");
  }

  @Test
  @DisplayName("T03: Expiration detection evaluates correctly for document_upload")
  void detectsExpiredDocumentUpload() {
    LocalDateTime now = LocalDateTime.now();
    UUID uploadId = UUID.randomUUID();

    DocumentUpload expiredUpload =
        new DocumentUpload(
            uploadId,
            UUID.randomUUID(),
            testUser.id(),
            "old.epub",
            DocumentFormat.EPUB,
            "application/epub+zip",
            50000L,
            null,
            "uploads/expired.epub",
            DocumentUploadStatus.PENDING,
            now.minusMinutes(5),
            null,
            null,
            now.minusHours(1),
            now.minusMinutes(5),
            0);

    documentUploads.save(expiredUpload);

    var found = documentUploads.findById(uploadId);
    assertThat(found).isPresent();
    assertThat(found.get().isExpired(now)).isTrue();
  }

  @Test
  @DisplayName("T04: Unique constraint prevents reserving the same document_id twice")
  void preventsDuplicateDocumentIdReservation() {
    LocalDateTime now = LocalDateTime.now();
    UUID sharedDocId = UUID.randomUUID();

    DocumentUpload firstUpload =
        new DocumentUpload(
            UUID.randomUUID(),
            sharedDocId,
            testUser.id(),
            "first.epub",
            DocumentFormat.EPUB,
            "application/epub+zip",
            1000L,
            null,
            "key1",
            DocumentUploadStatus.PENDING,
            now.plusMinutes(15),
            null,
            null,
            now,
            now,
            0);
    documentUploads.save(firstUpload);

    DocumentUpload secondUpload =
        new DocumentUpload(
            UUID.randomUUID(),
            sharedDocId,
            testUser.id(),
            "second.epub",
            DocumentFormat.EPUB,
            "application/epub+zip",
            2000L,
            null,
            "key2",
            DocumentUploadStatus.PENDING,
            now.plusMinutes(15),
            null,
            null,
            now,
            now,
            0);

    assertThatThrownBy(() -> documentUploads.save(secondUpload))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("T04b: Abort upload transitions status to ABORTED unless already CONFIRMED")
  void abortsPendingUploadSuccessfully() {
    LocalDateTime now = LocalDateTime.now();
    UUID uploadId = UUID.randomUUID();

    DocumentUpload upload =
        new DocumentUpload(
            uploadId,
            UUID.randomUUID(),
            testUser.id(),
            "to_abort.epub",
            DocumentFormat.EPUB,
            "application/epub+zip",
            1000L,
            null,
            "key/abort",
            DocumentUploadStatus.PENDING,
            now.plusMinutes(15),
            null,
            null,
            now,
            now,
            0);
    documentUploads.save(upload);

    boolean aborted = documentUploads.abort(uploadId, testUser.id());
    assertThat(aborted).isTrue();

    var updated = documentUploads.findById(uploadId);
    assertThat(updated).isPresent();
    assertThat(updated.get().status()).isEqualTo(DocumentUploadStatus.ABORTED);
    assertThat(updated.get().isAborted()).isTrue();

    // Aborting again returns false or preserves aborted
    boolean wrongUserAbort = documentUploads.abort(uploadId, UUID.randomUUID());
    assertThat(wrongUserAbort).isFalse();
  }
}
