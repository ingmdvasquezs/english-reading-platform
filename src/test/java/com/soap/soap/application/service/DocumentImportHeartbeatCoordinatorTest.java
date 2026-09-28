package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.JobLeaseLostException;
import com.soap.soap.application.port.out.ImportJobRepositoryPort;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;

class DocumentImportHeartbeatCoordinatorTest {

  private ImportJobRepositoryPort importJobs;
  private SqsClient sqsClient;
  private ScheduledExecutorService scheduler;
  private UUID jobId;
  private UUID leaseToken;
  private static final String WORKER_ID = "test-worker-1";
  private static final String QUEUE_URL =
      "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";
  private static final String RECEIPT_HANDLE = "receipt-handle-test-123";

  @BeforeEach
  void setUp() {
    importJobs = mock(ImportJobRepositoryPort.class);
    sqsClient = mock(SqsClient.class);
    scheduler = Executors.newSingleThreadScheduledExecutor();
    jobId = UUID.randomUUID();
    leaseToken = UUID.randomUUID();
  }

  @AfterEach
  void tearDown() {
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdownNow();
    }
  }

  @Test
  @DisplayName("ChangeMessageVisibility sends exact queueUrl, receiptHandle, and visibilityTimeout")
  void tick_extendsSqsVisibilityWithCorrectParameters() {
    Duration leaseDuration = Duration.ofSeconds(90);
    Duration heartbeatInterval = Duration.ofSeconds(30);

    when(importJobs.renewLease(eq(jobId), eq(leaseToken), eq(WORKER_ID), eq(leaseDuration)))
        .thenReturn(true);

    try (DocumentImportHeartbeatCoordinator coordinator =
        new DocumentImportHeartbeatCoordinator(
            importJobs,
            sqsClient,
            QUEUE_URL,
            jobId,
            leaseToken,
            WORKER_ID,
            RECEIPT_HANDLE,
            leaseDuration,
            heartbeatInterval,
            scheduler)) {

      coordinator.tick();

      ArgumentCaptor<ChangeMessageVisibilityRequest> captor =
          ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
      verify(sqsClient).changeMessageVisibility(captor.capture());
      ChangeMessageVisibilityRequest request = captor.getValue();

      assertThat(request.queueUrl()).isEqualTo(QUEUE_URL);
      assertThat(request.receiptHandle()).isEqualTo(RECEIPT_HANDLE);
      assertThat(request.visibilityTimeout()).isEqualTo(90);
      assertThat(coordinator.isOwnershipLost()).isFalse();
    }
  }

  @Test
  @DisplayName("Lost lease marks ownership lost and does NOT extend SQS visibility")
  void tick_leaseLost_marksOwnershipLost_skipsSqsVisibility() {
    Duration leaseDuration = Duration.ofSeconds(60);
    Duration heartbeatInterval = Duration.ofSeconds(20);

    when(importJobs.renewLease(eq(jobId), eq(leaseToken), eq(WORKER_ID), eq(leaseDuration)))
        .thenReturn(false);

    try (DocumentImportHeartbeatCoordinator coordinator =
        new DocumentImportHeartbeatCoordinator(
            importJobs,
            sqsClient,
            QUEUE_URL,
            jobId,
            leaseToken,
            WORKER_ID,
            RECEIPT_HANDLE,
            leaseDuration,
            heartbeatInterval,
            scheduler)) {

      coordinator.tick();

      assertThat(coordinator.isOwnershipLost()).isTrue();
      assertThatThrownBy(coordinator::checkOwnership)
          .isInstanceOf(JobLeaseLostException.class)
          .hasMessageContaining("Ownership lease lost");
      verify(sqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
    }
  }

  @Test
  @DisplayName("Coordinator close cleanly shuts down scheduler executor")
  void close_shutsDownSchedulerExecutor() {
    Duration leaseDuration = Duration.ofSeconds(60);
    Duration heartbeatInterval = Duration.ofSeconds(20);

    DocumentImportHeartbeatCoordinator coordinator =
        new DocumentImportHeartbeatCoordinator(
            importJobs,
            sqsClient,
            QUEUE_URL,
            jobId,
            leaseToken,
            WORKER_ID,
            RECEIPT_HANDLE,
            leaseDuration,
            heartbeatInterval,
            scheduler);

    coordinator.start();
    assertThat(scheduler.isShutdown()).isFalse();

    coordinator.close();
    assertThat(scheduler.isShutdown()).isTrue();
  }
}
