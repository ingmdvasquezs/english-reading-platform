package com.soap.soap.infrastructure.http.adapter;

import com.soap.soap.application.exception.ExternalProviderException;
import com.soap.soap.application.port.out.TranslationPort;
import com.soap.soap.infrastructure.http.configuration.ExternalProviderLimits;
import com.soap.soap.infrastructure.http.configuration.TranslationClientPolicy;
import com.soap.soap.infrastructure.http.dto.AzureTranslationResponse;
import com.soap.soap.infrastructure.observability.ExternalProviderObservation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AzureTranslatorAdapter implements TranslationPort {
  private static final Logger LOGGER = LoggerFactory.getLogger(AzureTranslatorAdapter.class);
  private final RestClient client;
  private final String apiKey;
  private final String region;
  private final ExternalProviderLimits limits;
  private final TranslationClientPolicy policy;
  private final ExternalProviderObservation observation;

  public AzureTranslatorAdapter(RestClient client, String apiKey, String region) {
    this(
        client,
        apiKey,
        region,
        ExternalProviderLimits.defaults(),
        TranslationClientPolicy.defaults(),
        new ExternalProviderObservation(new SimpleMeterRegistry()));
  }

  @Autowired
  public AzureTranslatorAdapter(
      @Qualifier("azureTranslatorRestClient") RestClient client,
      @Value("${translation.azure.api-key:}") String apiKey,
      @Value("${translation.azure.region:}") String region,
      ExternalProviderLimits limits,
      TranslationClientPolicy policy,
      ExternalProviderObservation observation) {
    this.client = client;
    this.apiKey = apiKey;
    this.region = region;
    this.limits = limits;
    this.policy = policy;
    this.observation = observation;
  }

  @Override
  public String translate(String text, String sourceLanguage, String targetLanguage) {
    return observation.observe(
        "azure_translator", () -> translateWithRetry(text, sourceLanguage, targetLanguage));
  }

  private String translateWithRetry(String text, String sourceLanguage, String targetLanguage) {
    requireConfiguration();
    var deadline = System.nanoTime() + policy.timeout().toNanos();
    RuntimeException last = null;
    for (var attempt = 0; attempt <= policy.maxRetries(); attempt++) {
      try {
        return request(text, sourceLanguage, targetLanguage);
      } catch (RetryableTranslationException exception) {
        last = exception;
        if (attempt == policy.maxRetries()
            || System.nanoTime() + policy.retryDelay().toNanos() >= deadline) break;
        LOGGER.warn("translation.retry provider=azure_translator attempt={}", attempt + 1);
        pause(deadline);
      }
    }
    throw failure(last == null ? null : last.getCause());
  }

  private String request(String text, String sourceLanguage, String targetLanguage) {
    try {
      var response =
          client
              .post()
              .uri(
                  builder ->
                      builder
                          .path("/translate")
                          .queryParam("api-version", "3.0")
                          .queryParam("from", sourceLanguage)
                          .queryParam("to", targetLanguage)
                          .build())
              .contentType(MediaType.APPLICATION_JSON)
              .header("Ocp-Apim-Subscription-Key", apiKey)
              .header("Ocp-Apim-Subscription-Region", region)
              .body(List.of(Map.of("Text", text)))
              .retrieve()
              .body(AzureTranslationResponse[].class);
      if (response == null
          || response.length == 0
          || response[0] == null
          || response[0].translations() == null
          || response[0].translations().isEmpty()) throw failure(null);
      var translated = response[0].translations().getFirst().text();
      if (translated == null
          || translated.isBlank()
          || translated.length() > limits.maximumTranslationCharacters()) throw failure(null);
      return translated;
    } catch (RestClientResponseException exception) {
      if (retryable(exception.getStatusCode().value()))
        throw new RetryableTranslationException(exception);
      throw failure(exception);
    } catch (RestClientException exception) {
      if (isTimeout(exception)) throw new RetryableTranslationException(exception);
      throw failure(exception);
    }
  }

  private void requireConfiguration() {
    if (apiKey == null || apiKey.isBlank() || region == null || region.isBlank())
      throw failure(new IllegalStateException("Azure Translator provider is not configured"));
  }

  private boolean retryable(int status) {
    return status == 500 || status == 502 || status == 503 || status == 504;
  }

  private boolean isTimeout(Throwable throwable) {
    for (var current = throwable; current != null; current = current.getCause())
      if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException)
        return true;
    return false;
  }

  private void pause(long deadline) {
    var remaining = Duration.ofNanos(Math.max(0, deadline - System.nanoTime()));
    var delay = policy.retryDelay().compareTo(remaining) < 0 ? policy.retryDelay() : remaining;
    try {
      Thread.sleep(delay);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw failure(exception);
    }
  }

  private ExternalProviderException failure(Throwable cause) {
    return new ExternalProviderException("Translation provider is unavailable", cause);
  }

  private static final class RetryableTranslationException extends RuntimeException {
    RetryableTranslationException(Throwable cause) {
      super(cause);
    }
  }
}
