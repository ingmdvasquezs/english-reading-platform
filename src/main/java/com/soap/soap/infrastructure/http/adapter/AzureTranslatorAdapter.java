package com.soap.soap.infrastructure.http.adapter;

import com.soap.soap.application.exception.ExternalProviderException;
import com.soap.soap.application.model.LexicalTranslationCandidate;
import com.soap.soap.application.port.out.TranslationPort;
import com.soap.soap.infrastructure.http.configuration.ExternalProviderLimits;
import com.soap.soap.infrastructure.http.configuration.TranslationClientPolicy;
import com.soap.soap.infrastructure.http.dto.AzureDictionaryLookupResponse;
import com.soap.soap.infrastructure.http.dto.AzureTranslationResponse;
import com.soap.soap.infrastructure.observability.ExternalProviderObservation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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
  private final Map<String, List<LexicalTranslationCandidate>> lexicalCache =
      new ConcurrentHashMap<>();

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
    var list = translateBatch(List.of(text), sourceLanguage, targetLanguage);
    return list.isEmpty() ? "" : list.getFirst();
  }

  @Override
  public List<LexicalTranslationCandidate> lookupLexical(
      String word, String sourceLanguage, String targetLanguage) {
    if (word == null || word.isBlank()) {
      return List.of();
    }
    var cacheKey = sourceLanguage + ":" + targetLanguage + ":" + word.trim().toLowerCase();
    var cached = lexicalCache.get(cacheKey);
    if (cached != null) {
      return cached;
    }
    var candidates =
        observation.observe(
            "azure_translator_dictionary",
            () -> lookupLexicalWithRetry(word.trim(), sourceLanguage, targetLanguage));
    if (candidates != null && !candidates.isEmpty()) {
      lexicalCache.put(cacheKey, candidates);
    }
    return candidates != null ? candidates : List.of();
  }

  private List<LexicalTranslationCandidate> lookupLexicalWithRetry(
      String word, String sourceLanguage, String targetLanguage) {
    requireConfiguration();
    var deadline = System.nanoTime() + policy.timeout().toNanos();
    RuntimeException last = null;
    for (var attempt = 0; attempt <= policy.maxRetries(); attempt++) {
      try {
        return requestDictionaryLookup(word, sourceLanguage, targetLanguage);
      } catch (RetryableTranslationException exception) {
        last = exception;
        if (attempt == policy.maxRetries()
            || System.nanoTime() + policy.retryDelay().toNanos() >= deadline) break;
        LOGGER.warn(
            "translation.dictionary.retry provider=azure_translator attempt={}", attempt + 1);
        pause(deadline);
      }
    }
    throw failure(last == null ? null : last.getCause());
  }

  private List<LexicalTranslationCandidate> requestDictionaryLookup(
      String word, String sourceLanguage, String targetLanguage) {
    try {
      var requestBody = List.of(Map.of("Text", word));
      var response =
          client
              .post()
              .uri(
                  builder ->
                      builder
                          .path("/dictionary/lookup")
                          .queryParam("api-version", "3.0")
                          .queryParam("from", sourceLanguage)
                          .queryParam("to", targetLanguage)
                          .build())
              .contentType(MediaType.APPLICATION_JSON)
              .header("Ocp-Apim-Subscription-Key", apiKey)
              .header("Ocp-Apim-Subscription-Region", region)
              .body(requestBody)
              .retrieve()
              .body(AzureDictionaryLookupResponse[].class);

      if (response == null || response.length == 0 || response[0].translations() == null) {
        return List.of();
      }

      var candidates = new java.util.ArrayList<LexicalTranslationCandidate>();
      for (var item : response[0].translations()) {
        if (item == null) {
          continue;
        }
        var target =
            item.normalizedTarget() != null && !item.normalizedTarget().isBlank()
                ? item.normalizedTarget()
                : item.displayTarget();
        if (target == null
            || target.isBlank()
            || target.length() > limits.maximumTranslationCharacters()) {
          continue;
        }
        var pos = item.posTag() != null ? item.posTag() : "";
        var confidence = item.confidence();
        var backTranslations =
            item.backTranslations() != null
                ? item.backTranslations().stream()
                    .map(b -> b.normalizedText() != null ? b.normalizedText() : b.displayText())
                    .filter(Objects::nonNull)
                    .toList()
                : List.<String>of();
        candidates.add(new LexicalTranslationCandidate(target, pos, confidence, backTranslations));
      }
      return candidates;
    } catch (RestClientResponseException exception) {
      if (retryable(exception.getStatusCode().value())) {
        throw new RetryableTranslationException(exception);
      }
      throw failure(exception);
    } catch (RestClientException exception) {
      if (isTimeout(exception)) {
        throw new RetryableTranslationException(exception);
      }
      throw failure(exception);
    }
  }

  @Override
  public List<String> translateBatch(
      List<String> texts, String sourceLanguage, String targetLanguage) {
    if (texts == null || texts.isEmpty()) {
      return List.of();
    }
    return observation.observe(
        "azure_translator", () -> translateBatchWithRetry(texts, sourceLanguage, targetLanguage));
  }

  private List<String> translateBatchWithRetry(
      List<String> texts, String sourceLanguage, String targetLanguage) {
    requireConfiguration();
    var deadline = System.nanoTime() + policy.timeout().toNanos();
    RuntimeException last = null;
    for (var attempt = 0; attempt <= policy.maxRetries(); attempt++) {
      try {
        return requestBatch(texts, sourceLanguage, targetLanguage);
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

  private List<String> requestBatch(
      List<String> texts, String sourceLanguage, String targetLanguage) {
    try {
      var requestBody =
          texts.stream()
              .map(text -> (Map<String, String>) Map.of("Text", text != null ? text : ""))
              .toList();

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
              .body(requestBody)
              .retrieve()
              .body(AzureTranslationResponse[].class);
      if (response == null || response.length < texts.size()) {
        throw failure(null);
      }
      var results = new java.util.ArrayList<String>(texts.size());
      for (int i = 0; i < texts.size(); i++) {
        var item = response[i];
        if (item == null || item.translations() == null || item.translations().isEmpty()) {
          throw failure(null);
        }
        var translated = item.translations().getFirst().text();
        if (translated == null) {
          results.add("");
        } else if (translated.length() > limits.maximumTranslationCharacters()) {
          throw failure(null);
        } else {
          results.add(translated);
        }
      }
      return results;
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
