package com.soap.soap.infrastructure.queue;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.document-import.queue")
public record DocumentImportQueueProperties(String url, String region, URI endpointOverride) {

  public DocumentImportQueueProperties {
    if (region == null || region.isBlank()) {
      region = "us-east-1";
    }
  }
}
