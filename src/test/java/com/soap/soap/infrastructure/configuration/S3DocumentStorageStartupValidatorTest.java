package com.soap.soap.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.soap.soap.domain.model.StorageProvider;
import com.soap.soap.infrastructure.storage.S3DocumentStorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;

/**
 * BACK-022: startup fails fast when S3 document storage is active without a valid bucket. Uses the
 * real application.yaml / application-{profile}.yaml files.
 */
class S3DocumentStorageStartupValidatorTest {
  private static final String VALID_BUCKET = "example-documents-bucket";

  private static ApplicationContextRunner runner(String profile) {
    return new ApplicationContextRunner()
        .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(S3Configuration.class, S3DocumentStorageStartupValidator.class);
  }

  @Test
  void prodWithoutBucketVariableFailsStartupInsteadOfSigningAgainstThePlaceholder() {
    assumeThat(System.getenv("DOCUMENT_STORAGE_S3_BUCKET")).isNull();
    runner("prod").run(failsOnInvalidBucket());
  }

  @Test
  void prodWithEmptyOrBlankBucketFailsStartupInsteadOfUsingAFallbackBucket() {
    runner("prod").withPropertyValues("DOCUMENT_STORAGE_S3_BUCKET=").run(failsOnInvalidBucket());
    runner("prod").withPropertyValues("DOCUMENT_STORAGE_S3_BUCKET=   ").run(failsOnInvalidBucket());
  }

  @Test
  void prodWithAnUnresolvedPlaceholderFailsStartup() {
    runner("prod")
        .withPropertyValues("app.document-storage.s3.bucket=${SOME_UNDEFINED_BUCKET_VARIABLE}")
        .run(failsOnInvalidBucket());
  }

  @Test
  void prodWithAValidBucketStartsWithTheS3Provider() {
    runner("prod")
        .withPropertyValues("DOCUMENT_STORAGE_S3_BUCKET=" + VALID_BUCKET)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var properties = context.getBean(S3DocumentStorageProperties.class);
              assertThat(properties.provider()).isEqualTo(StorageProvider.S3);
              assertThat(properties.s3().bucket()).isEqualTo(VALID_BUCKET);
            });
  }

  @Test
  void prodCannotOptOutOfS3ThroughTheProviderVariable() {
    runner("prod")
        .withPropertyValues("DOCUMENT_STORAGE_PROVIDER=FILESYSTEM", "DOCUMENT_STORAGE_S3_BUCKET=")
        .run(failsOnInvalidBucket());
  }

  @Test
  void localProfileWithoutAwsConfigurationStillStarts() {
    runner("local")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(S3DocumentStorageProperties.class).provider())
                  .isEqualTo(StorageProvider.FILESYSTEM);
            });
  }

  @Test
  void localProfileThatOptsIntoS3IsValidatedLikeProduction() {
    runner("local").withPropertyValues("DOCUMENT_STORAGE_PROVIDER=S3").run(failsOnInvalidBucket());
    runner("local")
        .withPropertyValues("DOCUMENT_STORAGE_PROVIDER=S3", "DOCUMENT_STORAGE_S3_BUCKET=local-dev")
        .run(context -> assertThat(context).hasNotFailed());
  }

  private static ContextConsumer<AssertableApplicationContext> failsOnInvalidBucket() {
    return context -> {
      assertThat(context).hasFailed();
      assertThat(context.getStartupFailure())
          .rootCause()
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("DOCUMENT_STORAGE_S3_BUCKET")
          .hasMessageContaining("valid S3 bucket name");
    };
  }
}
