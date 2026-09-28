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
}
