package com.soap.soap.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentUpload;
import com.soap.soap.domain.model.DocumentUploadStatus;
import com.soap.soap.domain.model.ImportJobStatus;
import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.model.SrsItemParameters;
import com.soap.soap.domain.model.SrsState;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.service.FsrsScheduler;
import com.soap.soap.infrastructure.persistence.adapter.ImportJobPersistenceAdapter;
import com.soap.soap.infrastructure.persistence.adapter.OutboxEventPersistenceAdapter;
import com.soap.soap.infrastructure.persistence.mapper.ImportJobEntityMapper;
import com.soap.soap.infrastructure.persistence.mapper.OutboxEventEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaImportJobRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaOutboxEventRepository;
import com.soap.soap.infrastructure.security.InMemoryRateLimiter;
import com.soap.soap.infrastructure.security.RateLimitPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemporalHandlingPolicyTest {

  @Test
  @DisplayName("B. Upload expiration calculation is consistent regardless of host timezone")
  void uploadExpirationIndependentOfHostTimezone() {
    Instant fixedInstant = Instant.parse("2026-09-25T15:00:00Z");
    Clock utcClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
    LocalDateTime nowUtc = LocalDateTime.now(utcClock);

    LocalDateTime expiresAtFuture = nowUtc.plusMinutes(15);
    DocumentUpload validUpload =
        new DocumentUpload(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "sample.epub",
            DocumentFormat.EPUB,
            "application/epub+zip",
            1024L,
            "sha256",
            "key",
            DocumentUploadStatus.PENDING,
            expiresAtFuture,
            null,
            null,
            nowUtc,
            nowUtc,
            0L);
    assertThat(validUpload.isExpired(nowUtc)).isFalse();

    LocalDateTime expiresAtPast = nowUtc.minusSeconds(1);
    DocumentUpload expiredUpload =
        new DocumentUpload(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "sample.epub",
            DocumentFormat.EPUB,
            "application/epub+zip",
            1024L,
            "sha256",
            "key",
            DocumentUploadStatus.PENDING,
            expiresAtPast,
            null,
            null,
            nowUtc,
            nowUtc,
            0L);
    assertThat(expiredUpload.isExpired(nowUtc)).isTrue();
  }

  @Test
  @DisplayName(
      "C. Import job lease: with Clock.fixed UTC, leaseUntil = now + leaseDuration exactly")
  void importJobLeaseCalculatedExactly() {
    Instant fixedInstant = Instant.parse("2026-09-25T12:00:00Z");
    Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
    LocalDateTime expectedNow = LocalDateTime.ofInstant(fixedInstant, ZoneOffset.UTC);
    Duration leaseDuration = Duration.ofMinutes(5);
    LocalDateTime expectedLeaseUntil = expectedNow.plus(leaseDuration);

    JpaImportJobRepository repository = mock(JpaImportJobRepository.class);
    ImportJobEntityMapper mapper = mock(ImportJobEntityMapper.class);
    ImportJobPersistenceAdapter adapter =
        new ImportJobPersistenceAdapter(repository, mapper, fixedClock);

    UUID jobId = UUID.randomUUID();
    String workerId = "worker-1";
    when(repository.claimJob(
            eq(jobId),
            eq(workerId),
            any(UUID.class),
            eq(ImportJobStatus.PROCESSING),
            eq(expectedLeaseUntil),
            eq(expectedNow)))
        .thenReturn(0);

    adapter.claim(jobId, workerId, leaseDuration);

    verify(repository)
        .claimJob(
            eq(jobId),
            eq(workerId),
            any(UUID.class),
            eq(ImportJobStatus.PROCESSING),
            eq(expectedLeaseUntil),
            eq(expectedNow));
  }

  @Test
  @DisplayName(
      "D. Outbox lock/retry: lockedUntil and nextAttemptAt calculated exactly with Clock.fixed UTC")
  void outboxLockAndRetryCalculatedExactly() {
    Instant fixedInstant = Instant.parse("2026-09-25T10:00:00Z");
    Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
    LocalDateTime expectedNow = LocalDateTime.ofInstant(fixedInstant, ZoneOffset.UTC);
    Duration lockDuration = Duration.ofMinutes(2);
    Duration retryBackoff = Duration.ofSeconds(30);

    JpaOutboxEventRepository repository = mock(JpaOutboxEventRepository.class);
    OutboxEventEntityMapper mapper = mock(OutboxEventEntityMapper.class);
    OutboxEventPersistenceAdapter adapter =
        new OutboxEventPersistenceAdapter(repository, mapper, fixedClock);

    // D1: claimBatch
    UUID eventId = UUID.randomUUID();
    when(repository.findClaimableIds(expectedNow, 10)).thenReturn(List.of(eventId));
    adapter.claimBatch("dispatcher-1", 10, lockDuration);
    verify(repository)
        .lockBatch(List.of(eventId), "dispatcher-1", expectedNow.plus(lockDuration), expectedNow);

    // D2: markFailedAttempt with retryBackoff
    adapter.markFailedAttempt(eventId, "dispatcher-1", "network timeout", retryBackoff);
    verify(repository)
        .markFailedRetry(
            eventId,
            "dispatcher-1",
            expectedNow.plus(retryBackoff),
            "network timeout",
            expectedNow);
  }

  @Test
  @DisplayName("E. FSRS: mathematical calculations remain identical with UTC offset anchoring")
  void fsrsParityBeforeAndAfterUtcAnchoring() {
    FsrsScheduler scheduler = new FsrsScheduler();
    LocalDateTime lastReviewedAt = LocalDateTime.of(2026, 9, 20, 12, 0, 0);
    LocalDateTime nowUtc = LocalDateTime.of(2026, 9, 25, 12, 0, 0);

    long utcAnchoredSeconds =
        Duration.between(lastReviewedAt.atOffset(ZoneOffset.UTC), nowUtc.atOffset(ZoneOffset.UTC))
            .toSeconds();
    assertThat(utcAnchoredSeconds).isEqualTo(5 * 86400L);

    SrsItemParameters item =
        new SrsItemParameters(
            SrsState.REVIEW, VocabularyStatus.KNOWN, 4.0, 5.0, 2, 0, lastReviewedAt, null);
    var result = scheduler.calculateNextState(item, ReviewRating.GOOD, nowUtc);
    assertThat(result).isNotNull();
    assertThat(result.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(result.stability()).isGreaterThan(item.stability());
  }

  @Test
  @DisplayName("F. Rate limiting: UTC Clock does not alter windowing behavior")
  void inMemoryRateLimiterWorksWithUtcClock() {
    Instant start = Instant.parse("2026-09-25T12:00:00Z");
    Clock fixedClock = Clock.fixed(start, ZoneOffset.UTC);
    InMemoryRateLimiter limiter = new InMemoryRateLimiter(fixedClock, 100);

    // 2 requests allowed in 10s window
    assertThat(limiter.tryAcquire(RateLimitPolicy.LOGIN, "test-key", 2, Duration.ofSeconds(10)))
        .isTrue();
    assertThat(limiter.tryAcquire(RateLimitPolicy.LOGIN, "test-key", 2, Duration.ofSeconds(10)))
        .isTrue();
    assertThat(limiter.tryAcquire(RateLimitPolicy.LOGIN, "test-key", 2, Duration.ofSeconds(10)))
        .isFalse();
  }
}
