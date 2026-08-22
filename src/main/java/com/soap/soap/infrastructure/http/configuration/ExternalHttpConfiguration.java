package com.soap.soap.infrastructure.http.configuration;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ExternalHttpConfiguration {
  @Bean("freeDictionaryRestClient")
  RestClient freeDictionaryRestClient(
      @Value("${dictionary.free.base-url}") String baseUrl,
      @Value("${dictionary.free.connect-timeout}") Duration connectTimeout,
      @Value("${dictionary.free.read-timeout}") Duration readTimeout) {
    return client(baseUrl, connectTimeout, readTimeout);
  }

  @Bean("libreTranslateRestClient")
  RestClient libreTranslateRestClient(
      @Value("${translation.libre.base-url}") String baseUrl,
      @Value("${translation.libre.connect-timeout}") Duration connectTimeout,
      @Value("${translation.libre.read-timeout}") Duration readTimeout) {
    return client(baseUrl, connectTimeout, readTimeout);
  }

  private RestClient client(String baseUrl, Duration connectTimeout, Duration readTimeout) {
    var requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);
    return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }
}
