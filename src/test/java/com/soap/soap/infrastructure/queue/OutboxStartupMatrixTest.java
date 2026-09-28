package com.soap.soap.infrastructure.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.soap.soap.application.port.out.OutboxEventRepositoryPort;
import com.soap.soap.application.service.OutboxDeliveryFailureService;
import com.soap.soap.application.service.OutboxDispatcher;
import com.soap.soap.infrastructure.configuration.SqsConfiguration;
import com.soap.soap.infrastructure.persistence.mapper.OutboxEventSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.sqs.SqsClient;

class OutboxStartupMatrixTest {

  @Configuration
  static class TestDependenciesConfiguration {
    @Bean
    AwsCredentialsProvider awsCredentialsProvider() {
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create("test-access-key", "test-secret-key"));
    }

    @Bean
    OutboxEventRepositoryPort outboxEventRepositoryPort() {
      return mock(OutboxEventRepositoryPort.class);
    }

    @Bean
    OutboxEventSerializer outboxEventSerializer() {
      return mock(OutboxEventSerializer.class);
    }

    @Bean
    OutboxDeliveryFailureService outboxDeliveryFailureService() {
      return mock(OutboxDeliveryFailureService.class);
    }
  }

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              TestDependenciesConfiguration.class,
              SqsConfiguration.class,
              SqsImportQueuePublisher.class,
              OutboxDispatcher.class,
              ScheduledOutboxDispatcher.class);

  @Test
  @DisplayName(
      "Case A: APP_ROLE=api, outbox enabled=true, queue URL configured -> dispatcher/scheduler active")
  void caseA_apiRoleEnabledWithQueueUrl_active() {
    runner
        .withPropertyValues(
            "app.role=api",
            "app.document-import.outbox.enabled=true",
            "app.document-import.queue.url=https://sqs.us-east-1.amazonaws.com/123456789012/test-queue",
            "app.document-import.queue.region=us-east-1")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(SqsClient.class);
              assertThat(context).hasSingleBean(SqsImportQueuePublisher.class);
              assertThat(context).hasSingleBean(OutboxDispatcher.class);
              assertThat(context).hasSingleBean(ScheduledOutboxDispatcher.class);
            });
  }

  @Test
  @DisplayName(
      "Case B: APP_ROLE=worker, queue URL empty -> starts cleanly, publisher/scheduler disabled")
  void caseB_workerRoleEmptyQueueUrl_startsSuccessfullyWithoutScheduler() {
    runner
        .withPropertyValues(
            "app.role=worker",
            "app.document-import.outbox.enabled=true",
            "app.document-import.queue.url=")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(ScheduledOutboxDispatcher.class);
              assertThat(context).doesNotHaveBean(OutboxDispatcher.class);
              assertThat(context).doesNotHaveBean(SqsImportQueuePublisher.class);
              assertThat(context).doesNotHaveBean(SqsClient.class);
            });
  }

  @Test
  @DisplayName("Case B (via APP_ROLE env): APP_ROLE=worker, queue URL empty -> starts cleanly")
  void caseB_workerRoleViaEnv_startsSuccessfullyWithoutScheduler() {
    runner
        .withPropertyValues(
            "APP_ROLE=worker",
            "app.document-import.outbox.enabled=true",
            "app.document-import.queue.url=")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(ScheduledOutboxDispatcher.class);
              assertThat(context).doesNotHaveBean(OutboxDispatcher.class);
              assertThat(context).doesNotHaveBean(SqsImportQueuePublisher.class);
              assertThat(context).doesNotHaveBean(SqsClient.class);
            });
  }

  @Test
  @DisplayName(
      "Case C: APP_ROLE=api, outbox enabled=false, queue URL empty -> starts cleanly, scheduler disabled")
  void caseC_apiRoleDisabledOutboxEmptyQueueUrl_startsSuccessfullyWithoutScheduler() {
    runner
        .withPropertyValues(
            "app.role=api",
            "app.document-import.outbox.enabled=false",
            "app.document-import.queue.url=")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(ScheduledOutboxDispatcher.class);
              assertThat(context).doesNotHaveBean(OutboxDispatcher.class);
              assertThat(context).doesNotHaveBean(SqsImportQueuePublisher.class);
              assertThat(context).doesNotHaveBean(SqsClient.class);
            });
  }

  @Test
  @DisplayName("Case D: APP_ROLE=api, outbox enabled=true, queue URL empty -> FAIL FAST on startup")
  void caseD_apiRoleEnabledEmptyQueueUrl_failsFastOnStartup() {
    runner
        .withPropertyValues(
            "app.role=api",
            "app.document-import.outbox.enabled=true",
            "app.document-import.queue.url=")
        .run(
            context -> {
              assertThat(context).hasFailed();
              Throwable failure = context.getStartupFailure();
              assertThat(failure).isNotNull();
              assertThat(failure)
                  .rootCause()
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessageContaining(
                      "SQS queue URL (app.document-import.queue.url) must be configured");
            });
  }
}
