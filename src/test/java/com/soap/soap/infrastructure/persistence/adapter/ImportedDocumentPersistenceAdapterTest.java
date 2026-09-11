package com.soap.soap.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.DuplicateActiveDocumentSourceException;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.infrastructure.persistence.entity.ImportedDocumentEntity;
import com.soap.soap.infrastructure.persistence.mapper.DocumentSectionEntityMapper;
import com.soap.soap.infrastructure.persistence.mapper.DocumentUnitEntityMapper;
import com.soap.soap.infrastructure.persistence.mapper.ImportedDocumentEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaDocumentSectionRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaDocumentUnitRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaImportedDocumentRepository;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ImportedDocumentPersistenceAdapterTest {

  @Mock private JpaImportedDocumentRepository documents;
  @Mock private JpaDocumentSectionRepository sections;
  @Mock private JpaDocumentUnitRepository units;

  private ImportedDocumentEntityMapper documentMapper;
  private DocumentSectionEntityMapper sectionMapper;
  private DocumentUnitEntityMapper unitMapper;
  private ImportedDocumentPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    documentMapper = Mappers.getMapper(ImportedDocumentEntityMapper.class);
    sectionMapper = Mappers.getMapper(DocumentSectionEntityMapper.class);
    unitMapper = Mappers.getMapper(DocumentUnitEntityMapper.class);
    adapter =
        new ImportedDocumentPersistenceAdapter(
            documents, sections, units, documentMapper, sectionMapper, unitMapper);
  }

  @Test
  void saveDocumentSucceedsWhenNoConstraintViolation() {
    var domain = sampleDocument();
    var entity = documentMapper.toEntity(domain);
    when(documents.saveAndFlush(any(ImportedDocumentEntity.class))).thenReturn(entity);

    var saved = adapter.saveDocument(domain);

    assertThat(saved).isNotNull();
    assertThat(saved.title()).isEqualTo(domain.title());
  }

  @Test
  void
      saveDocumentTranslatesActiveSourceUniqueConstraintViolationToDuplicateActiveDocumentSourceExceptionByConstraintName() {
    var domain = sampleDocument();
    var constraintException =
        new ConstraintViolationException(
            "duplicate active source",
            new SQLException("unique constraint"),
            "uk_imported_documents_user_active_source");
    var springException =
        new DataIntegrityViolationException(
            "duplicate key value violates unique constraint", constraintException);

    when(documents.saveAndFlush(any(ImportedDocumentEntity.class))).thenThrow(springException);

    assertThatThrownBy(() -> adapter.saveDocument(domain))
        .isInstanceOf(DuplicateActiveDocumentSourceException.class)
        .hasMessage("Active document source already exists for user")
        .hasCause(springException);
  }

  @Test
  void
      saveDocumentTranslatesActiveSourceUniqueConstraintViolationToDuplicateActiveDocumentSourceExceptionByMessage() {
    var domain = sampleDocument();
    var springException =
        new DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"uk_imported_documents_user_active_source\"");

    when(documents.saveAndFlush(any(ImportedDocumentEntity.class))).thenThrow(springException);

    assertThatThrownBy(() -> adapter.saveDocument(domain))
        .isInstanceOf(DuplicateActiveDocumentSourceException.class)
        .hasMessage("Active document source already exists for user")
        .hasCause(springException);
  }

  @Test
  void saveDocumentRethrowsDataIntegrityViolationExceptionWhenConstraintIsDifferent() {
    var domain = sampleDocument();
    var foreignKeyConstraintException =
        new ConstraintViolationException(
            "foreign key violation",
            new SQLException("violates foreign key"),
            "fk_imported_documents_user");
    var springException =
        new DataIntegrityViolationException("foreign key violation", foreignKeyConstraintException);

    when(documents.saveAndFlush(any(ImportedDocumentEntity.class))).thenThrow(springException);

    assertThatThrownBy(() -> adapter.saveDocument(domain))
        .isInstanceOf(DataIntegrityViolationException.class)
        .isNotInstanceOf(DuplicateActiveDocumentSourceException.class);
  }

  @Test
  void saveDocumentRethrowsDataIntegrityViolationExceptionWhenNotNullConstraintFails() {
    var domain = sampleDocument();
    var springException =
        new DataIntegrityViolationException(
            "null value in column \"title\" violates not-null constraint");

    when(documents.saveAndFlush(any(ImportedDocumentEntity.class))).thenThrow(springException);

    assertThatThrownBy(() -> adapter.saveDocument(domain))
        .isInstanceOf(DataIntegrityViolationException.class)
        .isNotInstanceOf(DuplicateActiveDocumentSourceException.class);
  }

  @Test
  void saveDocumentRethrowsDataIntegrityViolationExceptionWhenCheckConstraintFails() {
    var domain = sampleDocument();
    var checkException =
        new ConstraintViolationException(
            "check constraint violation",
            new SQLException("check violation"),
            "ck_readings_origin");
    var springException =
        new DataIntegrityViolationException("check constraint failed", checkException);

    when(documents.saveAndFlush(any(ImportedDocumentEntity.class))).thenThrow(springException);

    assertThatThrownBy(() -> adapter.saveDocument(domain))
        .isInstanceOf(DataIntegrityViolationException.class)
        .isNotInstanceOf(DuplicateActiveDocumentSourceException.class);
  }

  @Test
  void saveDocumentTranslatesSchemaQualifiedActiveSourceUniqueConstraintViolation() {
    var domain = sampleDocument();
    var constraintException =
        new ConstraintViolationException(
            "duplicate active source",
            new SQLException("unique constraint"),
            "public.uk_imported_documents_user_active_source");
    var springException =
        new DataIntegrityViolationException("duplicate key value", constraintException);

    when(documents.saveAndFlush(any(ImportedDocumentEntity.class))).thenThrow(springException);

    assertThatThrownBy(() -> adapter.saveDocument(domain))
        .isInstanceOf(DuplicateActiveDocumentSourceException.class)
        .hasMessage("Active document source already exists for user");
  }

  @Test
  void structuredConstraintTakesPrecedenceOverIncidentalMessageContent() {
    var domain = sampleDocument();
    var otherConstraintException =
        new ConstraintViolationException(
            "conflict",
            new SQLException(
                "fk failed uk_imported_documents_user_active_source mentioned in text"),
            "fk_another_constraint");
    var springException =
        new DataIntegrityViolationException("something failed", otherConstraintException);

    when(documents.saveAndFlush(any(ImportedDocumentEntity.class))).thenThrow(springException);

    assertThatThrownBy(() -> adapter.saveDocument(domain))
        .isInstanceOf(DataIntegrityViolationException.class)
        .isNotInstanceOf(DuplicateActiveDocumentSourceException.class);
  }

  private ImportedDocument sampleDocument() {
    var now = LocalDateTime.now();
    return new ImportedDocument(
        null,
        UUID.randomUUID(),
        "Test Document",
        "Author",
        "en",
        DocumentFormat.EPUB,
        null,
        null,
        "test.epub",
        "a".repeat(64),
        DocumentImportStatus.PROCESSING,
        1,
        now,
        now);
  }
}
