package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.port.out.ImportJobRepositoryPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.ImportJob;
import com.soap.soap.domain.model.ImportJobStatus;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.WorkerClaim;
import com.soap.soap.domain.model.WorkerClaimResult;
import java.time.Duration;
import java.time.LocalDateTime;
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
class WorkerLeaseClaimIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private ImportJobRepositoryPort importJobs;
  @Autowired private ImportedDocumentRepositoryPort importedDocuments;
  @Autowired private UserRepositoryPort users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private jakarta.persistence.EntityManager entityManager;

  private User testUser;
  private ImportedDocument testDocument;

  @BeforeEach
  void setUp() {
    testUser =
        users.save(
            new User(
                null,
                "Worker User",
                "worker-user-" + UUID.randomUUID() + "@example.com",
                "hash",
                null));

    LocalDateTime now = LocalDateTime.now();
    testDocument =
        importedDocuments.saveDocument(
            new ImportedDocument(
                UUID.randomUUID(),
                testUser.id(),
                "Lease Test Doc",
                null,
                "en",
                DocumentFormat.EPUB,
                null,
                "source/key.epub",
                "key.epub",
                "e".repeat(64),
                DocumentImportStatus.PROCESSING,
                null,
                5,
                now,
                now));
  }

  private ImportJob createJob(ImportJobStatus status, int attemptCount, int maxAttempts) {
    LocalDateTime now = LocalDateTime.now();
    return importJobs.save(
        new ImportJob(
            UUID.randomUUID(),
            testDocument.id(),
            testUser.id(),
            status,
            attemptCount,
            maxAttempts,
            "source/key.epub",
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
  }

  @Test
  @DisplayName(
      "T10: Claim on PENDING job acquires lease with new UUID leaseToken and PROCESSING status")
  void claimPendingJobAcquiresLease() {
    ImportJob job = createJob(ImportJobStatus.PENDING, 0, 3);

    WorkerClaim claim = importJobs.claim(job.id(), "worker-1", Duration.ofMinutes(5));

    assertThat(claim.result()).isEqualTo(WorkerClaimResult.ACQUIRED);
    assertThat(claim.leaseToken()).isNotNull();
    assertThat(claim.job()).isPresent();

    ImportJob claimedJob = claim.job().get();
    assertThat(claimedJob.status()).isEqualTo(ImportJobStatus.PROCESSING);
    assertThat(claimedJob.workerId()).isEqualTo("worker-1");
    assertThat(claimedJob.leaseToken()).isEqualTo(claim.leaseToken());
    assertThat(claimedJob.leaseUntil()).isNotNull();
    assertThat(claimedJob.leaseUntil()).isAfter(LocalDateTime.now());
    assertThat(claimedJob.attemptCount()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "T11: Second worker attempting to claim active lease is rejected with ACTIVE_BY_OTHER_WORKER")
  void secondWorkerCannotClaimActiveJob() {
    ImportJob job = createJob(ImportJobStatus.PENDING, 0, 3);
    WorkerClaim firstClaim = importJobs.claim(job.id(), "worker-1", Duration.ofMinutes(5));
    assertThat(firstClaim.isAcquired()).isTrue();

    WorkerClaim secondClaim = importJobs.claim(job.id(), "worker-2", Duration.ofMinutes(5));
    assertThat(secondClaim.result()).isEqualTo(WorkerClaimResult.ACTIVE_BY_OTHER_WORKER);
    assertThat(secondClaim.job()).isEmpty();
  }

  @Test
  @DisplayName(
      "T12: Worker crash (lease expired) allows second worker to claim with NEW leaseToken")
  void expiredLeaseCanBeReclaimedBySecondWorker() {
    ImportJob job = createJob(ImportJobStatus.PENDING, 0, 3);
    WorkerClaim firstClaim = importJobs.claim(job.id(), "worker-1", Duration.ofSeconds(1));
    assertThat(firstClaim.isAcquired()).isTrue();
    UUID firstToken = firstClaim.leaseToken();

    // Expire lease in database
    jdbc.update(
        "UPDATE import_jobs SET lease_until = ? WHERE id = ?",
        LocalDateTime.now().minusSeconds(10),
        job.id());
    entityManager.clear();

    WorkerClaim secondClaim = importJobs.claim(job.id(), "worker-2", Duration.ofMinutes(5));
    assertThat(secondClaim.result()).isEqualTo(WorkerClaimResult.ACQUIRED);
    assertThat(secondClaim.leaseToken()).isNotEqualTo(firstToken);
    assertThat(secondClaim.job().orElseThrow().attemptCount()).isEqualTo(2);
    assertThat(secondClaim.job().orElseThrow().workerId()).isEqualTo("worker-2");
  }

  @Test
  @DisplayName("T13: renewLease by owning worker with valid leaseToken extends lease_until")
  void renewLeaseSucceedsForOwner() {
    ImportJob job = createJob(ImportJobStatus.PENDING, 0, 3);
    WorkerClaim claim = importJobs.claim(job.id(), "worker-1", Duration.ofMinutes(2));

    boolean renewed =
        importJobs.renewLease(job.id(), claim.leaseToken(), "worker-1", Duration.ofMinutes(10));
    assertThat(renewed).isTrue();

    ImportJob updated = importJobs.findById(job.id()).orElseThrow();
    assertThat(updated.leaseUntil()).isAfter(LocalDateTime.now().plusMinutes(9));
  }

  @Test
  @DisplayName("T14: renewLease with wrong leaseToken or wrong workerId is rejected")
  void renewLeaseFailsWithMismatchedCredentials() {
    ImportJob job = createJob(ImportJobStatus.PENDING, 0, 3);
    WorkerClaim claim = importJobs.claim(job.id(), "worker-1", Duration.ofMinutes(5));

    // Wrong lease token
    boolean wrongToken =
        importJobs.renewLease(job.id(), UUID.randomUUID(), "worker-1", Duration.ofMinutes(5));
    assertThat(wrongToken).isFalse();

    // Wrong worker ID
    boolean wrongWorker =
        importJobs.renewLease(job.id(), claim.leaseToken(), "worker-2", Duration.ofMinutes(5));
    assertThat(wrongWorker).isFalse();
  }

  @Test
  @DisplayName("T15: Job reaching max_attempts is rejected with RETRY_LIMIT_EXCEEDED")
  void rejectsClaimWhenAttemptsExceedMax() {
    ImportJob job = createJob(ImportJobStatus.PENDING, 3, 3);

    WorkerClaim claim = importJobs.claim(job.id(), "worker-1", Duration.ofMinutes(5));
    assertThat(claim.result()).isEqualTo(WorkerClaimResult.RETRY_LIMIT_EXCEEDED);
  }

  @Test
  @DisplayName("T16: Job in COMPLETED status cannot be claimed")
  void completedJobCannotBeClaimed() {
    ImportJob job = createJob(ImportJobStatus.COMPLETED, 1, 3);
    WorkerClaim claim = importJobs.claim(job.id(), "worker-1", Duration.ofMinutes(5));
    assertThat(claim.result()).isEqualTo(WorkerClaimResult.ALREADY_COMPLETED);
  }

  @Test
  @DisplayName("T17: Job in FAILED status cannot be claimed")
  void failedJobCannotBeClaimed() {
    ImportJob job = createJob(ImportJobStatus.FAILED, 3, 3);
    WorkerClaim claim = importJobs.claim(job.id(), "worker-1", Duration.ofMinutes(5));
    assertThat(claim.result()).isEqualTo(WorkerClaimResult.FINAL_FAILED);
  }

  @Test
  @DisplayName("T18: Job in ABORTED status cannot be claimed")
  void abortedJobCannotBeClaimed() {
    ImportJob job = createJob(ImportJobStatus.ABORTED, 0, 3);
    WorkerClaim claim = importJobs.claim(job.id(), "worker-1", Duration.ofMinutes(5));
    assertThat(claim.result()).isEqualTo(WorkerClaimResult.ABORTED);
  }

  @Test
  @DisplayName("T32: releaseForRetry resets job to PENDING and sets next_attempt_at with backoff")
  void releaseForRetrySchedulesNextAttempt() {
    ImportJob job = createJob(ImportJobStatus.PENDING, 0, 3);
    WorkerClaim claim = importJobs.claim(job.id(), "worker-1", Duration.ofMinutes(5));

    boolean retried =
        importJobs.releaseForRetry(
            job.id(),
            claim.leaseToken(),
            "worker-1",
            Duration.ofMinutes(15),
            "TRANSIENT_NET_ERR",
            "Timeout connecting to upstream");
    assertThat(retried).isTrue();

    ImportJob updated = importJobs.findById(job.id()).orElseThrow();
    assertThat(updated.status()).isEqualTo(ImportJobStatus.PENDING);
    assertThat(updated.workerId()).isNull();
    assertThat(updated.leaseToken()).isNull();
    assertThat(updated.leaseUntil()).isNull();
    assertThat(updated.nextAttemptAt()).isNotNull();
    assertThat(updated.nextAttemptAt()).isAfter(LocalDateTime.now().plusMinutes(14));
    assertThat(updated.lastErrorCode()).isEqualTo("TRANSIENT_NET_ERR");
  }

  @Test
  @DisplayName(
      "T33 & T34: Job with future next_attempt_at is skipped, but claimable after backoff passes")
  void respectsRetryBackoff() {
    ImportJob job = createJob(ImportJobStatus.PENDING, 0, 3);
    WorkerClaim claim = importJobs.claim(job.id(), "worker-1", Duration.ofMinutes(5));
    importJobs.releaseForRetry(
        job.id(),
        claim.leaseToken(),
        "worker-1",
        Duration.ofMinutes(10),
        "TEMPORARY_ERROR",
        "Retrying later");

    // T33: cannot claim while next_attempt_at is in future
    WorkerClaim immediateClaim = importJobs.claim(job.id(), "worker-2", Duration.ofMinutes(5));
    assertThat(immediateClaim.result()).isEqualTo(WorkerClaimResult.ACTIVE_BY_OTHER_WORKER);

    // Fast-forward next_attempt_at into the past
    jdbc.update(
        "UPDATE import_jobs SET next_attempt_at = ? WHERE id = ?",
        LocalDateTime.now().minusSeconds(1),
        job.id());
    entityManager.clear();

    // T34: can claim after backoff expires
    WorkerClaim afterBackoffClaim = importJobs.claim(job.id(), "worker-2", Duration.ofMinutes(5));
    assertThat(afterBackoffClaim.result()).isEqualTo(WorkerClaimResult.ACQUIRED);
  }

  @Test
  @DisplayName("T35: failFinal transitions job to FAILED with error codes and finishedAt")
  void failFinalMarksJobFailedPermanently() {
    ImportJob job = createJob(ImportJobStatus.PENDING, 0, 3);
    WorkerClaim claim = importJobs.claim(job.id(), "worker-1", Duration.ofMinutes(5));

    boolean failed =
        importJobs.failFinal(
            job.id(),
            claim.leaseToken(),
            "worker-1",
            "CORRUPTED_ZIP",
            "EPUB rootfile container not found");
    assertThat(failed).isTrue();

    ImportJob updated = importJobs.findById(job.id()).orElseThrow();
    assertThat(updated.status()).isEqualTo(ImportJobStatus.FAILED);
    assertThat(updated.finishedAt()).isNotNull();
    assertThat(updated.lastErrorCode()).isEqualTo("CORRUPTED_ZIP");
  }

  @Test
  @DisplayName(
      "T36: Fencing token protection prevents stale worker A from completing reclaimed job")
  void fencingTokenRejectsZombieWorkerAction() {
    ImportJob job = createJob(ImportJobStatus.PENDING, 0, 3);
    WorkerClaim worker1Claim = importJobs.claim(job.id(), "worker-1", Duration.ofSeconds(1));
    UUID worker1Token = worker1Claim.leaseToken();

    // Lease expires and worker 2 reclaims the job
    jdbc.update(
        "UPDATE import_jobs SET lease_until = ? WHERE id = ?",
        LocalDateTime.now().minusSeconds(10),
        job.id());
    entityManager.clear();

    WorkerClaim worker2Claim = importJobs.claim(job.id(), "worker-2", Duration.ofMinutes(5));
    assertThat(worker2Claim.isAcquired()).isTrue();

    // Stale worker-1 wakes up and tries to complete the job with old token
    boolean completedByStaleWorker = importJobs.complete(job.id(), worker1Token, "worker-1");
    assertThat(completedByStaleWorker).isFalse();

    // Verify job is still PROCESSING under worker 2
    ImportJob current = importJobs.findById(job.id()).orElseThrow();
    assertThat(current.status()).isEqualTo(ImportJobStatus.PROCESSING);
    assertThat(current.workerId()).isEqualTo("worker-2");
    assertThat(current.leaseToken()).isEqualTo(worker2Claim.leaseToken());
  }
}
