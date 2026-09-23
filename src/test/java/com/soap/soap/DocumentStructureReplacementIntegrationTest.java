package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.command.CompleteDocumentImportCommand;
import com.soap.soap.application.port.out.DocumentProgressRepositoryPort;
import com.soap.soap.application.port.out.ImportJobRepositoryPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.usecase.CompleteDocumentImportUseCase;
import com.soap.soap.application.usecase.FailDocumentImportUseCase;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.DocumentProgress;
import com.soap.soap.domain.model.DocumentSection;
import com.soap.soap.domain.model.DocumentUnit;
import com.soap.soap.domain.model.DocumentUnitKind;
import com.soap.soap.domain.model.ImportJob;
import com.soap.soap.domain.model.ImportJobStatus;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.WorkerClaim;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
class DocumentStructureReplacementIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private CompleteDocumentImportUseCase completeUseCase;
  @Autowired private FailDocumentImportUseCase failUseCase;
  @Autowired private ImportedDocumentRepositoryPort documents;
  @Autowired private ImportJobRepositoryPort importJobs;
  @Autowired private DocumentProgressRepositoryPort progressRepository;
  @Autowired private UserRepositoryPort users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private jakarta.persistence.EntityManager entityManager;

  private User testUser;
  private ImportedDocument document;

  @BeforeEach
  void setUp() {
    testUser =
        users.save(
            new User(
                null,
                "Structure User",
                "structure-" + UUID.randomUUID() + "@example.com",
                "hash",
                null));

    LocalDateTime now = LocalDateTime.now();
    document =
        documents.saveDocument(
            new ImportedDocument(
                UUID.randomUUID(),
                testUser.id(),
                "Initial Title",
                "Initial Author",
                "en",
                DocumentFormat.EPUB,
                null,
                "storage/key.epub",
                "initial.epub",
                "f".repeat(64),
                DocumentImportStatus.PROCESSING,
                null,
                5,
                now,
                now));
  }

  private ImportJob createAndClaimJob(String workerId) {
    LocalDateTime now = LocalDateTime.now();
    ImportJob job =
        importJobs.save(
            new ImportJob(
                UUID.randomUUID(),
                document.id(),
                testUser.id(),
                ImportJobStatus.PENDING,
                0,
                3,
                "storage/key.epub",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                now,
                now,
                0));

    WorkerClaim claim = importJobs.claim(job.id(), workerId, Duration.ofMinutes(10));
    assertThat(claim.isAcquired()).isTrue();
    return importJobs.findById(job.id()).orElseThrow();
  }

  @Test
  @DisplayName(
      "T25: replaceDocumentStructure replaces sections and units cleanly without constraint errors")
  void replaceDocumentStructureReplacesCleanly() {
    // 1. Initial structure
    UUID sec1 = UUID.randomUUID();
    DocumentSection initialSection =
        new DocumentSection(sec1, document.id(), 1, "Old Act I", "act1.xhtml");
    DocumentUnit initialUnit =
        new DocumentUnit(
            UUID.randomUUID(),
            document.id(),
            sec1,
            1,
            1,
            DocumentUnitKind.LOGICAL_CHUNK,
            "Old opening line.",
            3,
            "act1.xhtml#p1",
            null);
    documents.saveSections(List.of(initialSection));
    documents.saveUnits(List.of(initialUnit));
    assertThat(documents.countSections(document.id())).isEqualTo(1);
    assertThat(documents.countUnits(document.id())).isEqualTo(1);

    // 2. Replace with new structure (2 sections, 3 units)
    UUID newSec1 = UUID.randomUUID();
    UUID newSec2 = UUID.randomUUID();
    DocumentSection s1 =
        new DocumentSection(newSec1, document.id(), 1, "New Act I", "act1_new.xhtml");
    DocumentSection s2 =
        new DocumentSection(newSec2, document.id(), 2, "New Act II", "act2_new.xhtml");

    DocumentUnit u1 =
        new DocumentUnit(
            UUID.randomUUID(),
            document.id(),
            newSec1,
            1,
            1,
            DocumentUnitKind.LOGICAL_CHUNK,
            "First unit text.",
            3,
            null,
            null);
    DocumentUnit u2 =
        new DocumentUnit(
            UUID.randomUUID(),
            document.id(),
            newSec1,
            2,
            2,
            DocumentUnitKind.LOGICAL_CHUNK,
            "Second unit text.",
            3,
            null,
            null);
    DocumentUnit u3 =
        new DocumentUnit(
            UUID.randomUUID(),
            document.id(),
            newSec2,
            3,
            1,
            DocumentUnitKind.LOGICAL_CHUNK,
            "Third unit text in section 2.",
            6,
            null,
            null);

    documents.replaceDocumentStructure(document.id(), List.of(s1, s2), List.of(u1, u2, u3));

    assertThat(documents.countSections(document.id())).isEqualTo(2);
    assertThat(documents.countUnits(document.id())).isEqualTo(3);

    List<DocumentUnit> currentUnits = documents.findUnits(document.id());
    assertThat(currentUnits).hasSize(3);
    assertThat(currentUnits.get(0).content()).isEqualTo("First unit text.");
    assertThat(currentUnits.get(2).content()).isEqualTo("Third unit text in section 2.");
  }

  @Test
  @DisplayName(
      "T26: CompleteDocumentImportUseCase transitions document to READY and job to COMPLETED")
  void completeImportSuccessfullyTransitionsState() {
    ImportJob job = createAndClaimJob("worker-1");

    UUID secId = UUID.randomUUID();
    DocumentSection sec = new DocumentSection(secId, document.id(), 1, "Act I", "act1.xhtml");
    DocumentUnit unit =
        new DocumentUnit(
            UUID.randomUUID(),
            document.id(),
            secId,
            1,
            1,
            DocumentUnitKind.LOGICAL_CHUNK,
            "To be, or not to be.",
            6,
            null,
            null);

    CompleteDocumentImportCommand command =
        new CompleteDocumentImportCommand(
            job.id(),
            job.leaseToken(),
            "worker-1",
            List.of(sec),
            List.of(unit),
            "Hamlet: Prince of Denmark",
            "William Shakespeare",
            "en",
            "covers/hamlet.jpg");

    boolean completed = completeUseCase.completeImport(command);
    assertThat(completed).isTrue();

    // Verify document is READY
    ImportedDocument readyDoc = documents.findDocumentById(document.id()).orElseThrow();
    assertThat(readyDoc.importStatus()).isEqualTo(DocumentImportStatus.READY);
    assertThat(readyDoc.title()).isEqualTo("Hamlet: Prince of Denmark");
    assertThat(readyDoc.author()).isEqualTo("William Shakespeare");
    assertThat(readyDoc.coverAssetKey()).isEqualTo("covers/hamlet.jpg");

    // Verify job is COMPLETED
    ImportJob completedJob = importJobs.findById(job.id()).orElseThrow();
    assertThat(completedJob.status()).isEqualTo(ImportJobStatus.COMPLETED);
    assertThat(completedJob.finishedAt()).isNotNull();

    // Verify structure
    assertThat(documents.countUnits(document.id())).isEqualTo(1);
    assertThat(documents.countSections(document.id())).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "T27: Fenced completion fails and rolls back if leaseToken does not match or lease has expired")
  void completeImportFailsWithInvalidFencingToken() {
    ImportJob job = createAndClaimJob("worker-1");

    CompleteDocumentImportCommand commandWithBadToken =
        new CompleteDocumentImportCommand(
            job.id(),
            UUID.randomUUID(), // invalid token
            "worker-1",
            List.of(),
            List.of(),
            "Title",
            null,
            "en",
            null);

    assertThatThrownBy(() -> completeUseCase.completeImport(commandWithBadToken))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fencing lease token does not match");

    // Expired lease
    jdbc.update(
        "UPDATE import_jobs SET lease_until = ? WHERE id = ?",
        LocalDateTime.now().minusSeconds(5),
        job.id());
    entityManager.clear();

    CompleteDocumentImportCommand commandWithExpiredLease =
        new CompleteDocumentImportCommand(
            job.id(),
            job.leaseToken(),
            "worker-1",
            List.of(),
            List.of(),
            "Title",
            null,
            "en",
            null);

    assertThatThrownBy(() -> completeUseCase.completeImport(commandWithExpiredLease))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("worker lease has expired");
  }

  @Test
  @DisplayName("T28: Fenced completion fails if document is not in PROCESSING status")
  void completeImportFailsIfDocumentNotProcessing() {
    ImportJob job = createAndClaimJob("worker-1");

    // Set document status to FAILED
    LocalDateTime now = LocalDateTime.now();
    documents.saveDocument(
        new ImportedDocument(
            document.id(),
            document.ownerId(),
            document.title(),
            document.author(),
            document.language(),
            document.format(),
            document.coverAssetKey(),
            document.sourceAssetKey(),
            document.originalFilename(),
            document.sourceSha256(),
            DocumentImportStatus.FAILED,
            "IMPORT_FAILURE",
            document.chunkingVersion(),
            document.createdAt(),
            now));

    CompleteDocumentImportCommand command =
        new CompleteDocumentImportCommand(
            job.id(),
            job.leaseToken(),
            "worker-1",
            List.of(),
            List.of(),
            "Title",
            null,
            "en",
            null);

    assertThatThrownBy(() -> completeUseCase.completeImport(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("document is not in PROCESSING state");
  }

  @Test
  @DisplayName(
      "T29: replaceDocumentStructure fails with IllegalStateException if document_progress already exists")
  void replaceDocumentStructureFailsIfProgressExists() {
    UUID secId = UUID.randomUUID();
    DocumentSection sec = new DocumentSection(secId, document.id(), 1, "Section 1", null);
    UUID unitId = UUID.randomUUID();
    DocumentUnit unit =
        new DocumentUnit(
            unitId,
            document.id(),
            secId,
            1,
            1,
            DocumentUnitKind.LOGICAL_CHUNK,
            "Initial unit.",
            2,
            null,
            null);
    documents.saveSections(List.of(sec));
    documents.saveUnits(List.of(unit));

    // Save reading progress on this document
    LocalDateTime now = LocalDateTime.now();
    progressRepository.save(DocumentProgress.start(testUser.id(), document.id(), unitId, now));

    // Attempting replaceDocumentStructure must fail as invariant violation
    assertThatThrownBy(
            () ->
                documents.replaceDocumentStructure(
                    document.id(),
                    List.of(new DocumentSection(UUID.randomUUID(), document.id(), 1, "Sec", null)),
                    List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unexpected reading progress exists");
  }

  @Test
  @DisplayName(
      "T30: FailDocumentImportUseCase marks both job and document as FAILED with failure reason")
  void failFinalUseCaseMarksBothEntitiesFailed() {
    ImportJob job = createAndClaimJob("worker-1");

    boolean failed =
        failUseCase.failFinal(
            job.id(),
            job.leaseToken(),
            "worker-1",
            "INVALID_EPUB",
            "EPUB package document contains invalid XML");
    assertThat(failed).isTrue();

    // Verify job is FAILED
    ImportJob failedJob = importJobs.findById(job.id()).orElseThrow();
    assertThat(failedJob.status()).isEqualTo(ImportJobStatus.FAILED);
    assertThat(failedJob.lastErrorCode()).isEqualTo("INVALID_EPUB");
    assertThat(failedJob.lastErrorMessage()).contains("invalid XML");

    // Verify document is FAILED
    ImportedDocument failedDoc = documents.findDocumentById(document.id()).orElseThrow();
    assertThat(failedDoc.importStatus()).isEqualTo(DocumentImportStatus.FAILED);
    assertThat(failedDoc.failureReason()).isEqualTo("INVALID_EPUB");
  }

  @Test
  @DisplayName(
      "T27_A: Reclaimed job rejects stale worker completion, preserving original document and job state")
  void staleWorkerCannotCompleteReclaimedJob() {
    // Initial structure
    UUID sec1 = UUID.randomUUID();
    DocumentSection initialSec =
        new DocumentSection(sec1, document.id(), 1, "Initial Section", null);
    DocumentUnit initialUnit =
        new DocumentUnit(
            UUID.randomUUID(),
            document.id(),
            sec1,
            1,
            1,
            DocumentUnitKind.LOGICAL_CHUNK,
            "Initial content",
            2,
            null,
            null);
    documents.saveSections(List.of(initialSec));
    documents.saveUnits(List.of(initialUnit));

    // Worker 1 claims job
    ImportJob job = createAndClaimJob("worker-1");
    UUID worker1Token = job.leaseToken();

    // Expire lease in database
    jdbc.update(
        "UPDATE import_jobs SET lease_until = ? WHERE id = ?",
        LocalDateTime.now().minusSeconds(10),
        job.id());
    entityManager.clear();

    // Worker 2 reclaims job with new lease
    WorkerClaim worker2Claim = importJobs.claim(job.id(), "worker-2", Duration.ofMinutes(5));
    assertThat(worker2Claim.isAcquired()).isTrue();
    UUID worker2Token = worker2Claim.leaseToken();
    assertThat(worker2Token).isNotEqualTo(worker1Token);

    // Stale Worker 1 attempts completeImport with worker1Token and new structure
    UUID zombieSecId = UUID.randomUUID();
    DocumentSection zombieSec =
        new DocumentSection(zombieSecId, document.id(), 1, "Zombie Section", null);
    DocumentUnit zombieUnit =
        new DocumentUnit(
            UUID.randomUUID(),
            document.id(),
            zombieSecId,
            1,
            1,
            DocumentUnitKind.LOGICAL_CHUNK,
            "Zombie content",
            2,
            null,
            null);

    CompleteDocumentImportCommand staleCommand =
        new CompleteDocumentImportCommand(
            job.id(),
            worker1Token,
            "worker-1",
            List.of(zombieSec),
            List.of(zombieUnit),
            "Zombie Title",
            "Zombie Author",
            "en",
            null);

    assertThatThrownBy(() -> completeUseCase.completeImport(staleCommand))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fencing lease token does not match");

    // VERIFY POST-FAILURE INVARIANTS:
    // 1. Structure was NOT replaced
    List<DocumentUnit> currentUnits = documents.findUnits(document.id());
    assertThat(currentUnits).hasSize(1);
    assertThat(currentUnits.getFirst().content()).isEqualTo("Initial content");

    List<DocumentSection> currentSections = documents.findSections(document.id());
    assertThat(currentSections).hasSize(1);
    assertThat(currentSections.getFirst().title()).isEqualTo("Initial Section");

    // 2. Document is NOT READY
    ImportedDocument currentDoc = documents.findDocumentById(document.id()).orElseThrow();
    assertThat(currentDoc.importStatus()).isEqualTo(DocumentImportStatus.PROCESSING);
    assertThat(currentDoc.title()).isEqualTo("Initial Title");

    // 3. Job remains PROCESSING under Worker 2 with token 2
    ImportJob currentJob = importJobs.findById(job.id()).orElseThrow();
    assertThat(currentJob.status()).isEqualTo(ImportJobStatus.PROCESSING);
    assertThat(currentJob.workerId()).isEqualTo("worker-2");
    assertThat(currentJob.leaseToken()).isEqualTo(worker2Token);
  }

  @Test
  @DisplayName(
      "T30_B: Reclaimed job rejects stale worker failFinal, without altering document or Worker 2 job")
  void staleWorkerCannotFailReclaimedJob() {
    // Worker 1 claims job
    ImportJob job = createAndClaimJob("worker-1");
    UUID worker1Token = job.leaseToken();

    // Expire lease in database
    jdbc.update(
        "UPDATE import_jobs SET lease_until = ? WHERE id = ?",
        LocalDateTime.now().minusSeconds(10),
        job.id());
    entityManager.clear();

    // Worker 2 reclaims job with new lease
    WorkerClaim worker2Claim = importJobs.claim(job.id(), "worker-2", Duration.ofMinutes(5));
    assertThat(worker2Claim.isAcquired()).isTrue();
    UUID worker2Token = worker2Claim.leaseToken();

    // Stale Worker 1 attempts failFinal with worker1Token
    boolean failed =
        failUseCase.failFinal(
            job.id(), worker1Token, "worker-1", "CORRUPT_DOCUMENT", "Corrupted packaging");
    assertThat(failed).isFalse();

    // VERIFY POST-FAILURE INVARIANTS:
    // 1. Document is NOT FAILED
    ImportedDocument currentDoc = documents.findDocumentById(document.id()).orElseThrow();
    assertThat(currentDoc.importStatus()).isEqualTo(DocumentImportStatus.PROCESSING);
    assertThat(currentDoc.failureReason()).isNull();

    // 2. Job remains PROCESSING under Worker 2 with token 2
    ImportJob currentJob = importJobs.findById(job.id()).orElseThrow();
    assertThat(currentJob.status()).isEqualTo(ImportJobStatus.PROCESSING);
    assertThat(currentJob.workerId()).isEqualTo("worker-2");
    assertThat(currentJob.leaseToken()).isEqualTo(worker2Token);
  }

  @Test
  @DisplayName("T41: Architecture is pure local without AWS SDK classes on classpath")
  void verifyZeroAwsDependencies() {
    assertThatThrownBy(() -> Class.forName("software.amazon.awssdk.services.s3.S3Client"))
        .isInstanceOf(ClassNotFoundException.class);
    assertThatThrownBy(() -> Class.forName("software.amazon.awssdk.services.sqs.SqsClient"))
        .isInstanceOf(ClassNotFoundException.class);
  }
}
