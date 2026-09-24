package com.soap.soap.infrastructure.configuration;

import com.soap.soap.infrastructure.storage.S3DocumentStorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3DocumentStorageProperties.class)
public class S3Configuration {

  @Bean
  public AwsCredentialsProvider awsCredentialsProvider(S3DocumentStorageProperties properties) {
    if (properties.s3().endpointOverride() != null) {
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create("test-access-key", "test-secret-key"));
    }
    return DefaultCredentialsProvider.create();
  }

  @Bean
  public S3Client s3Client(
      S3DocumentStorageProperties properties, AwsCredentialsProvider credentialsProvider) {
    Region region = Region.of(properties.s3().region());
    S3ClientBuilder builder =
        S3Client.builder().region(region).credentialsProvider(credentialsProvider);

    if (properties.s3().endpointOverride() != null) {
      builder.endpointOverride(properties.s3().endpointOverride());
    }

    if (properties.s3().pathStyleAccess()) {
      builder.serviceConfiguration(
          software.amazon.awssdk.services.s3.S3Configuration.builder()
              .pathStyleAccessEnabled(true)
              .build());
    }

    return builder.build();
  }

  @Bean
  public S3Presigner s3Presigner(
      S3DocumentStorageProperties properties, AwsCredentialsProvider credentialsProvider) {
    Region region = Region.of(properties.s3().region());
    S3Presigner.Builder builder =
        S3Presigner.builder().region(region).credentialsProvider(credentialsProvider);

    if (properties.s3().endpointOverride() != null) {
      builder.endpointOverride(properties.s3().endpointOverride());
    }

    if (properties.s3().pathStyleAccess()) {
      builder.serviceConfiguration(
          software.amazon.awssdk.services.s3.S3Configuration.builder()
              .pathStyleAccessEnabled(true)
              .build());
    }

    return builder.build();
  }
}
