package com.soap.soap.application.service;

import com.soap.soap.application.exception.JobLeaseLostException;
import com.soap.soap.application.port.out.ImportJobRepositoryPort;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;

public class DocumentImportHeartbeatCoordinator implements AutoCloseable {

  private static final Logger LOG =
      LoggerFactory.getLogger(DocumentImportHeartbeatCoordinator.class);

  private final ImportJobRepositoryPort importJobs;
  private final SqsClient sqsClient;
  private final String queueUrl;
  private final UUID jobId;
  private final UUID leaseToken;
  private final String workerId;
  private final String receiptHandle;
  private final Duration leaseDuration;
  private final Duration heartbeatInterval;
  private final ScheduledExecutorService scheduler;
  private final AtomicBoolean ownershipLost = new AtomicBoolean(false);
  private ScheduledFuture<?> scheduledFuture;

  public DocumentImportHeartbeatCoordinator(
      ImportJobRepositoryPort importJobs,
      SqsClient sqsClient,
      String queueUrl,
      UUID jobId,
      UUID leaseToken,
      String workerId,
      String receiptHandle,
      Duration leaseDuration,
      Duration heartbeatInterval) {
    this(
        importJobs,
        sqsClient,
        queueUrl,
        jobId,
        leaseToken,
        workerId,
        receiptHandle,
        leaseDuration,
        heartbeatInterval,
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "worker-heartbeat-" + jobId);
              thread.setDaemon(true);
              return thread;
            }));
  }

  public DocumentImportHeartbeatCoordinator(
      ImportJobRepositoryPort importJobs,
      SqsClient sqsClient,
      String queueUrl,
      UUID jobId,
      UUID leaseToken,
      String workerId,
      String receiptHandle,
      Duration leaseDuration,
      Duration heartbeatInterval,
      ScheduledExecutorService scheduler) {
    this.importJobs = Objects.requireNonNull(importJobs, "importJobs must not be null");
    this.sqsClient = sqsClient;
    this.queueUrl = queueUrl;
    this.jobId = Objects.requireNonNull(jobId, "jobId must not be null");
    this.leaseToken = Objects.requireNonNull(leaseToken, "leaseToken must not be null");
    this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
    this.receiptHandle = receiptHandle;
    this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
    this.heartbeatInterval =
        Objects.requireNonNull(heartbeatInterval, "heartbeatInterval must not be null");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
  }

  public synchronized void start() {
    if (scheduledFuture != null) {
      return;
    }
    long intervalMillis = heartbeatInterval.toMillis();
    scheduledFuture =
        scheduler.scheduleAtFixedRate(
            this::tick, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    LOG.debug("Started heartbeat coordinator for job {} interval={}", jobId, heartbeatInterval);
  }

  public void tick() {
    try {
      // 1. Extend DB lease
      boolean renewed = importJobs.renewLease(jobId, leaseToken, workerId, leaseDuration);
      if (!renewed) {
        LOG.warn("Worker {} lost ownership lease for job {}", workerId, jobId);
        ownershipLost.set(true);
        synchronized (this) {
          if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
          }
        }
        return;
      }
      LOG.debug("Worker {} renewed DB lease for job {}", workerId, jobId);

      // 2. Extend SQS visibility
      if (sqsClient != null && queueUrl != null && !queueUrl.isBlank() && receiptHandle != null) {
        try {
          sqsClient.changeMessageVisibility(
              ChangeMessageVisibilityRequest.builder()
                  .queueUrl(queueUrl)
                  .receiptHandle(receiptHandle)
                  .visibilityTimeout((int) leaseDuration.toSeconds())
                  .build());
          LOG.debug("Worker {} extended SQS visibility for job {}", workerId, jobId);
        } catch (Exception e) {
          LOG.warn(
              "Worker {} failed to extend SQS visibility for job {}: {}",
              workerId,
              jobId,
              e.getMessage());
        }
      }
    } catch (Exception e) {
      LOG.warn("Unexpected error in heartbeat tick for job {}: {}", jobId, e.getMessage(), e);
    }
  }

  public boolean isOwnershipLost() {
    return ownershipLost.get();
  }

  public void checkOwnership() {
    if (ownershipLost.get()) {
      throw new JobLeaseLostException("Ownership lease lost for job " + jobId);
    }
  }

  @Override
  public synchronized void close() {
    if (scheduledFuture != null) {
      scheduledFuture.cancel(true);
      scheduledFuture = null;
    }
    scheduler.shutdownNow();
    LOG.debug("Stopped heartbeat coordinator for job {}", jobId);
  }
}
