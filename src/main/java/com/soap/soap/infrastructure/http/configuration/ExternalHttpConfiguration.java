package com.soap.soap.infrastructure.http.configuration;

import com.soap.soap.infrastructure.observability.ExternalProviderObservation;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ExternalHttpConfiguration {
  @Bean
  ExternalProviderObservation externalProviderObservation(MeterRegistry meters) {
    return new ExternalProviderObservation(meters);
  }

  @Bean
  ExternalProviderLimits externalProviderLimits(
      @Value("${external-limits.dictionary-body-bytes:1048576}") int dictionaryBodyBytes,
      @Value("${external-limits.translation-body-bytes:262144}") int translationBodyBytes,
      @Value("${external-limits.maximum-entries:5}") int maximumEntries,
      @Value("${external-limits.maximum-meanings:20}") int maximumMeanings,
      @Value("${external-limits.maximum-definition-characters:2000}") int definitionCharacters,
      @Value("${external-limits.maximum-example-characters:2000}") int exampleCharacters,
      @Value("${external-limits.maximum-translation-characters:10000}") int translationCharacters,
      @Value("${external-limits.maximum-phonetic-characters:200}") int phoneticCharacters,
      @Value("${external-limits.maximum-audio-url-characters:2048}") int audioUrlCharacters) {
    return new ExternalProviderLimits(
        dictionaryBodyBytes,
        translationBodyBytes,
        maximumEntries,
        maximumMeanings,
        definitionCharacters,
        exampleCharacters,
        translationCharacters,
        phoneticCharacters,
        audioUrlCharacters);
  }

  @Bean("freeDictionaryRestClient")
  RestClient freeDictionaryRestClient(
      @Value("${dictionary.free.base-url}") String baseUrl,
      @Value("${dictionary.free.connect-timeout}") Duration connectTimeout,
      @Value("${dictionary.free.read-timeout}") Duration readTimeout,
      ExternalProviderLimits limits) {
    return client(baseUrl, connectTimeout, readTimeout, limits.dictionaryBodyBytes());
  }

  @Bean("libreTranslateRestClient")
  RestClient libreTranslateRestClient(
      @Value("${translation.libre.base-url}") String baseUrl,
      @Value("${translation.libre.connect-timeout}") Duration connectTimeout,
      @Value("${translation.libre.read-timeout}") Duration readTimeout,
      ExternalProviderLimits limits) {
    return client(baseUrl, connectTimeout, readTimeout, limits.translationBodyBytes());
  }

  private RestClient client(
      String baseUrl, Duration connectTimeout, Duration readTimeout, int maximumBodyBytes) {
    var httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(readTimeout);
    return RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(requestFactory)
        .requestInterceptor(new LimitedResponseBodyInterceptor(maximumBodyBytes))
        .build();
  }
}
