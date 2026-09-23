package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.exception.DocumentImportException.Reason;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.domain.model.User;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
@Transactional
class DocumentImportFailureReasonPostgresIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private ImportedDocumentRepositoryPort documents;
  @Autowired private UserRepositoryPort users;
  @Autowired private JdbcTemplate jdbc;

  private User owner;

  @BeforeEach
  void setUp() {
    owner =
        users.save(
            new User(
                null,
                "Test Owner",
                "failure-reason-" + UUID.randomUUID() + "@example.com",
                "hash",
                null));
  }

  @ParameterizedTest
  @EnumSource(Reason.class)
  @DisplayName("T01: PostgreSQL allows each of the 11 DocumentImportException failure reasons")
  void allowsAllFailureReasonsInPostgreSQL(Reason reason) {
    LocalDateTime now = LocalDateTime.now();
    ImportedDocument doc =
        new ImportedDocument(
            null,
            owner.id(),
            "Title for " + reason.name(),
            "Author",
            "en",
            DocumentFormat.EPUB,
            null,
            "assets/test.epub",
            "test.epub",
            "a".repeat(64),
            DocumentImportStatus.FAILED,
            reason.name(),
            5,
            now,
            now);

    ImportedDocument saved = documents.saveDocument(doc);
    assertThat(saved.id()).isNotNull();
    assertThat(saved.failureReason()).isEqualTo(reason.name());
    assertThat(saved.importStatus()).isEqualTo(DocumentImportStatus.FAILED);
  }

  @Test
  @DisplayName("T01b: PostgreSQL rejects invalid failure reasons not in the check constraint")
  void rejectsInvalidFailureReason() {
    LocalDateTime now = LocalDateTime.now();
    UUID docId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO imported_documents (
                      id, user_id, title, language, format, import_status, failure_reason,
                      source_sha256, chunking_version, created_at, updated_at
                    ) VALUES (
                      ?, ?, 'Invalid Reason Doc', 'en', 'EPUB', 'FAILED', 'UNKNOWN_REASON',
                      ?, 5, ?, ?
                    )
                    """,
                    docId,
                    owner.id(),
                    "b".repeat(64),
                    now,
                    now))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
