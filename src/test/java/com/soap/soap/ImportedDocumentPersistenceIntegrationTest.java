package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.exception.DuplicateActiveDocumentSourceException;
import com.soap.soap.application.port.out.DocumentProgressRepositoryPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.DocumentProgress;
import com.soap.soap.domain.model.DocumentSection;
import com.soap.soap.domain.model.DocumentUnit;
import com.soap.soap.domain.model.DocumentUnitKind;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.domain.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
@Transactional
class ImportedDocumentPersistenceIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private ImportedDocumentRepositoryPort documents;
  @Autowired private DocumentProgressRepositoryPort progress;
  @Autowired private UserRepositoryPort users;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;

  private User owner;
  private ImportedDocument document;
  private DocumentSection firstSection;
  private DocumentSection secondSection;
  private DocumentUnit firstUnit;
  private DocumentUnit secondUnit;

  @BeforeEach
  void setUp() {
    owner =
        users.save(
            new User(
                null,
                "Document Owner",
                "document-" + UUID.randomUUID() + "@example.com",
                "hash",
                null));
    document = documents.saveDocument(document(owner.id(), "fr"));
    var savedSections =
        documents.saveSections(
            List.of(
                new DocumentSection(null, document.id(), 2, "Second", "chapter-2.xhtml"),
                new DocumentSection(null, document.id(), 1, "First", "chapter-1.xhtml")));
    firstSection =
        savedSections.stream().filter(section -> section.ordinal() == 1).findFirst().orElseThrow();
    secondSection =
        savedSections.stream().filter(section -> section.ordinal() == 2).findFirst().orElseThrow();
    var savedUnits =
        documents.saveUnits(
            List.of(
                unit(document.id(), secondSection.id(), 3, 1, "Third"),
                unit(document.id(), firstSection.id(), 2, 2, "Second"),
                unit(document.id(), firstSection.id(), 1, 1, "First")));
    firstUnit =
        savedUnits.stream().filter(unit -> unit.globalOrdinal() == 1).findFirst().orElseThrow();
    secondUnit =
        savedUnits.stream().filter(unit -> unit.globalOrdinal() == 2).findFirst().orElseThrow();
  }

  @Test
  void savesLoadsAndNavigatesDocumentStructureInStableOrder() {
    assertThat(documents.findDocumentById(document.id())).contains(document);
    assertThat(documents.findSections(document.id()))
        .extracting(DocumentSection::ordinal)
        .containsExactly(1, 2);
    assertThat(documents.findUnits(document.id()))
        .extracting(DocumentUnit::globalOrdinal)
        .containsExactly(1, 2, 3);
    assertThat(documents.findUnitsBySection(document.id(), firstSection.id()))
        .extracting(DocumentUnit::sectionOrdinal)
        .containsExactly(1, 2);
    assertThat(documents.findUnitByGlobalOrdinal(document.id(), 2)).contains(secondUnit);
    assertThat(documents.findFirstUnit(document.id())).contains(firstUnit);
  }

  @Test
  void derivesSectionNavigationInOneOrderedProjectionWithoutUsingTechnicalTitles() {
    var emptyTechnicalSection =
        documents
            .saveSections(
                List.of(new DocumentSection(null, document.id(), 3, null, "OPS/id-idp123.xhtml")))
            .getFirst();

    var navigation = documents.findSectionNavigation(document.id());

    assertThat(navigation).extracting(value -> value.ordinal()).containsExactly(1, 2, 3);
    assertThat(navigation.get(0).title()).isEqualTo("First");
    assertThat(navigation.get(0).firstUnitId()).isEqualTo(firstUnit.id());
    assertThat(navigation.get(0).unitCount()).isEqualTo(2);
    assertThat(navigation.get(1).firstUnitId())
        .isEqualTo(documents.findUnitByGlobalOrdinal(document.id(), 3).orElseThrow().id());
    assertThat(navigation.get(1).unitCount()).isEqualTo(1);
    assertThat(navigation.get(2).id()).isEqualTo(emptyTechnicalSection.id());
    assertThat(navigation.get(2).title()).isNull();
    assertThat(navigation.get(2).firstUnitId()).isNull();
    assertThat(navigation.get(2).unitCount()).isZero();
  }

  @Test
  void databaseRejectsSameActiveSourceForSameUser() {
    assertThatThrownBy(() -> documents.saveDocument(document(owner.id(), "fr")))
        .isInstanceOf(DuplicateActiveDocumentSourceException.class);
  }

  @Test
  void sameSourceHashIsAllowedForAnotherUser() {
    var other = users.save(new User(null, "Other", "other-duplicate@example.com", "hash", null));
    var allowed = documents.saveDocument(document(other.id(), "fr"));
    assertThat(allowed.sourceSha256()).isEqualTo(document.sourceSha256());
  }

  @Test
  void failedDocumentReleasesItsSourceHashForRetry() {
    var failed =
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
            "INVALID_EPUB",
            document.chunkingVersion(),
            document.createdAt(),
            document.updatedAt().plusMinutes(1));
    documents.saveDocument(failed);

    var retry = documents.saveDocument(document(owner.id(), "fr"));

    assertThat(retry.id()).isNotEqualTo(document.id());
    assertThat(retry.sourceSha256()).isEqualTo(document.sourceSha256());
  }

  @Test
  void databaseRejectsASectionFromAnotherDocumentOnAUnit() {
    var other = documents.saveDocument(document(owner.id(), "en"));

    assertThatThrownBy(
            () -> {
              documents.saveUnits(List.of(unit(other.id(), firstSection.id(), 99, 99, "Invalid")));
              entityManager.flush();
            })
        .isInstanceOf(PersistenceException.class);
  }

  @Test
  void progressPersistsAdvancesCompletesAndHasAUniqueUserDocumentIdentity() {
    var openedAt = LocalDateTime.parse("2026-09-07T12:00:00");
    var started =
        progress.save(DocumentProgress.start(owner.id(), document.id(), firstUnit.id(), openedAt));
    var advanced = progress.save(started.moveTo(secondUnit.id(), openedAt.plusMinutes(2)));
    var completed = progress.save(advanced.complete(secondUnit.id(), openedAt.plusMinutes(4)));

    assertThat(advanced.startedAt()).isEqualTo(openedAt);
    assertThat(advanced.lastReadAt()).isEqualTo(openedAt.plusMinutes(2));
    assertThat(completed.completedAt()).isEqualTo(openedAt.plusMinutes(4));
    assertThat(completed.version()).isGreaterThan(started.version());
    assertThat(progress.findByUserIdAndDocumentId(owner.id(), document.id())).contains(completed);

    assertThatThrownBy(
            () ->
                progress.save(
                    DocumentProgress.start(
                        owner.id(), document.id(), firstUnit.id(), openedAt.plusHours(1))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void databaseEnforcesDocumentOwnershipAndCurrentUnitMembershipForProgress() {
    var otherUser =
        users.save(
            new User(null, "Other", "other-" + UUID.randomUUID() + "@example.com", "hash", null));
    var otherDocument = documents.saveDocument(document(otherUser.id(), "es"));
    var otherSection =
        documents
            .saveSections(List.of(new DocumentSection(null, otherDocument.id(), 1, null, null)))
            .getFirst();
    var otherUnit =
        documents
            .saveUnits(List.of(unit(otherDocument.id(), otherSection.id(), 1, 1, "Other")))
            .getFirst();
    entityManager.flush();

    assertThatThrownBy(
            () ->
                progress.save(
                    DocumentProgress.start(
                        owner.id(), otherDocument.id(), otherUnit.id(), LocalDateTime.now())))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deletingDocumentCascadesSectionsUnitsAndProgress() {
    progress.save(
        DocumentProgress.start(owner.id(), document.id(), firstUnit.id(), LocalDateTime.now()));
    documents.deleteDocument(document.id());
    entityManager.flush();

    assertThat(count("imported_documents", "id", document.id())).isZero();
    assertThat(count("document_sections", "document_id", document.id())).isZero();
    assertThat(count("document_units", "document_id", document.id())).isZero();
    assertThat(count("document_progress", "document_id", document.id())).isZero();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void optimisticLockingRejectsAStaleProgressUpdate() {
    var openedAt = LocalDateTime.parse("2026-09-07T12:00:00");
    var saved =
        progress.save(DocumentProgress.start(owner.id(), document.id(), firstUnit.id(), openedAt));
    jdbc.update("UPDATE document_progress SET version = version + 1 WHERE id = ?", saved.id());

    assertThatThrownBy(() -> progress.save(saved.moveTo(secondUnit.id(), openedAt.plusMinutes(1))))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentDuplicateActiveSourceInsertsProduceExactlyOneWinnerAndOneDuplicateException()
      throws Exception {
    var raceUser =
        users.save(
            new User(
                null, "Race Owner", "race-" + UUID.randomUUID() + "@example.com", "hash", null));
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    var ready = new java.util.concurrent.CountDownLatch(2);
    var start = new java.util.concurrent.CountDownLatch(1);

    var docToInsert = document(raceUser.id(), "de");

    java.util.concurrent.Callable<Object> task =
        () -> {
          ready.countDown();
          if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timeout waiting for start");
          }
          return documents.saveDocument(docToInsert);
        };

    var future1 = executor.submit(task);
    var future2 = executor.submit(task);

    assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    start.countDown();

    var results = new java.util.ArrayList<Object>();
    for (var future : List.of(future1, future2)) {
      try {
        results.add(future.get(10, java.util.concurrent.TimeUnit.SECONDS));
      } catch (java.util.concurrent.ExecutionException e) {
        results.add(e.getCause());
      }
    }
    executor.shutdown();

    var successCount = results.stream().filter(ImportedDocument.class::isInstance).count();
    var duplicateCount =
        results.stream().filter(DuplicateActiveDocumentSourceException.class::isInstance).count();

    assertThat(successCount).isEqualTo(1);
    assertThat(duplicateCount).isEqualTo(1);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM imported_documents WHERE user_id = ? AND deduplication_sha256 = ?",
                Integer.class,
                raceUser.id(),
                docToInsert.sourceSha256()))
        .isEqualTo(1);
  }

  private ImportedDocument document(UUID ownerId, String language) {
    var now = LocalDateTime.parse("2026-09-07T10:00:00");
    return new ImportedDocument(
        null,
        ownerId,
        "Imported book",
        "Author",
        language,
        DocumentFormat.EPUB,
        "cover/key",
        "source/key",
        "book.epub",
        Integer.toHexString(Math.floorMod(language.hashCode(), 16)).repeat(64),
        DocumentImportStatus.READY,
        1,
        now,
        now);
  }

  private DocumentUnit unit(
      UUID documentId, UUID sectionId, int globalOrdinal, int sectionOrdinal, String content) {
    return new DocumentUnit(
        null,
        documentId,
        sectionId,
        globalOrdinal,
        sectionOrdinal,
        DocumentUnitKind.LOGICAL_CHUNK,
        content,
        1,
        "chapter.xhtml#" + globalOrdinal,
        "b".repeat(64));
  }

  private int count(String table, String column, UUID id) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id);
  }
}
