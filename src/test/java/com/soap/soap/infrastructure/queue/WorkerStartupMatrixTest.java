package com.soap.soap.infrastructure.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.soap.soap.application.service.DocumentImportProcessor;
import com.soap.soap.infrastructure.configuration.SqsConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.sqs.SqsClient;

class WorkerStartupMatrixTest {

  @Configuration
  static class TestDependenciesConfiguration {
    @Bean
    AwsCredentialsProvider awsCredentialsProvider() {
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create("test-access-key", "test-secret-key"));
    }

    @Bean
    DocumentImportProcessor documentImportProcessor() {
      return mock(DocumentImportProcessor.class);
    }
  }

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withPropertyValues("app.document-import.worker.auto-startup=false")
          .withUserConfiguration(
              TestDependenciesConfiguration.class,
              SqsConfiguration.class,
              SqsDocumentImportConsumer.class);

  @Test
  @DisplayName("Case S: APP_ROLE=api -> consumer absent")
  void caseS_apiRole_consumerAbsent() {
    runner
        .withPropertyValues(
            "app.role=api",
            "app.document-import.outbox.enabled=true",
            "app.document-import.queue.url=https://sqs.us-east-1.amazonaws.com/123456789012/test-queue")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(SqsDocumentImportConsumer.class);
            });
  }

  @Test
  @DisplayName("Case T: APP_ROLE=worker with queue URL configured -> consumer present")
  void caseT_workerRoleWithQueueUrl_consumerPresent() {
    runner
        .withPropertyValues(
            "app.role=worker",
            "app.document-import.queue.url=https://sqs.us-east-1.amazonaws.com/123456789012/test-queue")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(SqsDocumentImportConsumer.class);
              assertThat(context).hasSingleBean(SqsClient.class);
            });
  }

  @Test
  @DisplayName("Case U: APP_ROLE=worker missing queue URL -> fail-fast on startup")
  void caseU_workerRoleMissingQueueUrl_failsFast() {
    runner
        .withPropertyValues("app.role=worker", "app.document-import.queue.url=")
        .run(
            context -> {
              assertThat(context).hasFailed();
              Throwable failure = context.getStartupFailure();
              assertThat(failure).isNotNull();
              assertThat(failure)
                  .rootCause()
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessageContaining(
                      "SQS queue URL (app.document-import.queue.url) must be configured when document import worker is enabled");
            });
  }

  @Test
  @DisplayName(
      "Case V: APP_ROLE=api without worker queue config still starts if publisher config is valid")
  void caseV_apiRoleWithoutWorkerQueueConfig_startsCleanly() {
    runner
        .withPropertyValues(
            "app.role=api",
            "app.document-import.outbox.enabled=true",
            "app.document-import.queue.url=https://sqs.us-east-1.amazonaws.com/123456789012/test-queue")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(SqsDocumentImportConsumer.class);
              assertThat(context).hasSingleBean(SqsClient.class);
            });
  }

  @Test
  @DisplayName("Case W: APP_ROLE=all with queue URL configured -> consumer present")
  void caseW_allRoleWithQueueUrl_consumerPresent() {
    runner
        .withPropertyValues(
            "app.role=all",
            "app.document-import.queue.url=https://sqs.us-east-1.amazonaws.com/123456789012/test-queue")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(SqsDocumentImportConsumer.class);
              assertThat(context).hasSingleBean(SqsClient.class);
            });
  }

  @Test
  @DisplayName("Case X: maxMessages > 1 -> fails fast on startup")
  void caseX_maxMessagesGreaterThanOne_failsFast() {
    runner
        .withPropertyValues(
            "app.role=worker",
            "app.document-import.queue.url=https://sqs.us-east-1.amazonaws.com/123456789012/test-queue",
            "app.document-import.worker.max-messages=2")
        .run(
            context -> {
              assertThat(context).hasFailed();
              Throwable failure = context.getStartupFailure();
              assertThat(failure).isNotNull();
              assertThat(failure)
                  .rootCause()
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining("maxMessages must be 1 for sequential worker, got: 2");
            });
  }

  @Test
  @DisplayName("Case Y: waitTimeSeconds > 20 -> fails fast on startup")
  void caseY_waitTimeSecondsGreaterThanTwenty_failsFast() {
    runner
        .withPropertyValues(
            "app.role=worker",
            "app.document-import.queue.url=https://sqs.us-east-1.amazonaws.com/123456789012/test-queue",
            "app.document-import.worker.wait-time-seconds=21")
        .run(
            context -> {
              assertThat(context).hasFailed();
              Throwable failure = context.getStartupFailure();
              assertThat(failure).isNotNull();
              assertThat(failure)
                  .rootCause()
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining("waitTimeSeconds must be between 1 and 20, got: 21");
            });
  }

  @Test
  @DisplayName("Case Z: visibilityTimeoutSeconds > 43200 -> fails fast on startup")
  void caseZ_visibilityTimeoutSecondsTooLarge_failsFast() {
    runner
        .withPropertyValues(
            "app.role=worker",
            "app.document-import.queue.url=https://sqs.us-east-1.amazonaws.com/123456789012/test-queue",
            "app.document-import.worker.visibility-timeout-seconds=43201")
        .run(
            context -> {
              assertThat(context).hasFailed();
              Throwable failure = context.getStartupFailure();
              assertThat(failure).isNotNull();
              assertThat(failure)
                  .rootCause()
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining(
                      "visibilityTimeoutSeconds must be between 1 and 43200, got: 43201");
            });
  }

  @Test
  @DisplayName("Case AA: heartbeatInterval * 2 > leaseDuration -> fails fast on startup")
  void caseAA_heartbeatTooCloseToLeaseDuration_failsFast() {
    runner
        .withPropertyValues(
            "app.role=worker",
            "app.document-import.queue.url=https://sqs.us-east-1.amazonaws.com/123456789012/test-queue",
            "app.document-import.worker.heartbeat-interval=35s",
            "app.document-import.worker.lease-duration=60s")
        .run(
            context -> {
              assertThat(context).hasFailed();
              Throwable failure = context.getStartupFailure();
              assertThat(failure).isNotNull();
              assertThat(failure)
                  .rootCause()
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining("must be at most half of leaseDuration");
            });
  }

  @Test
  @DisplayName("Case AB: heartbeatInterval * 2 > visibilityTimeoutSeconds -> fails fast on startup")
  void caseAB_heartbeatTooCloseToVisibilityTimeout_failsFast() {
    runner
        .withPropertyValues(
            "app.role=worker",
            "app.document-import.queue.url=https://sqs.us-east-1.amazonaws.com/123456789012/test-queue",
            "app.document-import.worker.heartbeat-interval=35s",
            "app.document-import.worker.lease-duration=100s",
            "app.document-import.worker.visibility-timeout-seconds=60")
        .run(
            context -> {
              assertThat(context).hasFailed();
              Throwable failure = context.getStartupFailure();
              assertThat(failure).isNotNull();
              assertThat(failure)
                  .rootCause()
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining("must be at most half of visibilityTimeoutSeconds");
            });
  }

  @Test
  @DisplayName("DocumentImportWorkerProperties defaults assign expected production values")
  void properties_defaultsAssignExpectedValues() {
    DocumentImportWorkerProperties props =
        new DocumentImportWorkerProperties(null, null, 0, 0, 0, null, null, null, null, null, null);

    assertThat(props.isEnabled()).isTrue();
    assertThat(props.isAutoStartup()).isTrue();
    assertThat(props.waitTimeSeconds()).isEqualTo(20);
    assertThat(props.visibilityTimeoutSeconds()).isEqualTo(60);
    assertThat(props.maxMessages()).isEqualTo(1);
    assertThat(props.pollDelay()).isEqualTo(java.time.Duration.ofSeconds(1));
    assertThat(props.errorBackoff()).isEqualTo(java.time.Duration.ofSeconds(5));
    assertThat(props.leaseDuration()).isEqualTo(java.time.Duration.ofSeconds(60));
    assertThat(props.heartbeatInterval()).isEqualTo(java.time.Duration.ofSeconds(20));
    assertThat(props.stagingCleanupAge()).isEqualTo(java.time.Duration.ofHours(24));
    assertThat(props.workerId()).startsWith("worker-");
  }

  @Test
  @DisplayName("DocumentImportWorkerProperties rejects invalid or non-positive durations")
  void properties_rejectsInvalidDurations() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            new DocumentImportWorkerProperties(
                true,
                true,
                20,
                60,
                1,
                java.time.Duration.ofSeconds(-1),
                java.time.Duration.ofSeconds(5),
                java.time.Duration.ofSeconds(60),
                java.time.Duration.ofSeconds(20),
                "w1",
                java.time.Duration.ofHours(24)));

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            new DocumentImportWorkerProperties(
                true,
                true,
                20,
                60,
                1,
                java.time.Duration.ofSeconds(1),
                java.time.Duration.ZERO,
                java.time.Duration.ofSeconds(60),
                java.time.Duration.ofSeconds(20),
                "w1",
                java.time.Duration.ofHours(24)));
  }
}
