package com.soap.soap.infrastructure.configuration;

import com.soap.soap.infrastructure.queue.DocumentImportOutboxProperties;
import com.soap.soap.infrastructure.queue.DocumentImportQueueProperties;
import com.soap.soap.infrastructure.queue.DocumentImportWorkerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

@Configuration
@EnableConfigurationProperties({
  DocumentImportQueueProperties.class,
  DocumentImportOutboxProperties.class,
  DocumentImportWorkerProperties.class
})
public class SqsConfiguration {

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnExpression(
      "('${app.document-import.outbox.enabled:false}' == 'true' and ('${APP_ROLE:${app.role:api}}'.equalsIgnoreCase('api') or '${APP_ROLE:${app.role:api}}'.equalsIgnoreCase('all')))"
          + " or ('${app.document-import.worker.enabled:true}' == 'true' and ('${APP_ROLE:${app.role:api}}'.equalsIgnoreCase('worker') or '${APP_ROLE:${app.role:api}}'.equalsIgnoreCase('all')) and !'${app.document-import.queue.url:}'.trim().isEmpty())")
  public SqsClient sqsClient(
      DocumentImportQueueProperties properties,
      @Autowired(required = false) AwsCredentialsProvider credentialsProvider) {
    String regionName =
        properties.region() != null && !properties.region().isBlank()
            ? properties.region()
            : "us-east-1";
    Region region = Region.of(regionName);
    SqsClientBuilder builder = SqsClient.builder().region(region);

    if (properties.endpointOverride() != null) {
      builder.endpointOverride(properties.endpointOverride());
      builder.credentialsProvider(
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create("test-access-key", "test-secret-key")));
    } else {
      builder.credentialsProvider(credentialsProvider);
    }

    return builder.build();
  }
}
