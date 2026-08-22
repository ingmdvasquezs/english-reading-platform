package com.soap.soap.infrastructure.http.adapter;

import com.soap.soap.application.exception.ExternalProviderException;
import com.soap.soap.application.port.out.TranslationPort;
import com.soap.soap.infrastructure.http.configuration.ExternalProviderLimits;
import com.soap.soap.infrastructure.http.dto.LibreTranslateResponse;
import com.soap.soap.infrastructure.observability.ExternalProviderObservation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class LibreTranslateAdapter implements TranslationPort {
  private final RestClient client;
  private final String apiKey;
  private final ExternalProviderLimits limits;
  private final ExternalProviderObservation observation;

  public LibreTranslateAdapter(
      @Qualifier("libreTranslateRestClient") RestClient client,
      @Value("${translation.libre.api-key:}") String apiKey) {
    this(
        client,
        apiKey,
        ExternalProviderLimits.defaults(),
        new ExternalProviderObservation(new SimpleMeterRegistry()));
  }

  public LibreTranslateAdapter(
      @Qualifier("libreTranslateRestClient") RestClient client,
      @Value("${translation.libre.api-key:}") String apiKey,
      ExternalProviderLimits limits) {
    this(client, apiKey, limits, new ExternalProviderObservation(new SimpleMeterRegistry()));
  }

  @Autowired
  public LibreTranslateAdapter(
      @Qualifier("libreTranslateRestClient") RestClient client,
      @Value("${translation.libre.api-key:}") String apiKey,
      ExternalProviderLimits limits,
      ExternalProviderObservation observation) {
    this.client = client;
    this.apiKey = apiKey;
    this.limits = limits;
    this.observation = observation;
  }

  @Override
  public String translate(String text, String sourceLanguage, String targetLanguage) {
    return observation.observe(
        "libre_translate", () -> doTranslate(text, sourceLanguage, targetLanguage));
  }

  private String doTranslate(String text, String sourceLanguage, String targetLanguage) {
    var body = new LinkedHashMap<String, Object>();
    body.put("q", text);
    body.put("source", sourceLanguage);
    body.put("target", targetLanguage);
    body.put("format", "text");
    if (apiKey != null && !apiKey.isBlank()) {
      body.put("api_key", apiKey);
    }
    try {
      var response =
          client.post().uri("/translate").body(body).retrieve().body(LibreTranslateResponse.class);
      if (response == null
          || response.translatedText() == null
          || response.translatedText().isBlank()
          || response.translatedText().length() > limits.maximumTranslationCharacters()) {
        throw providerFailure(null);
      }
      return response.translatedText();
    } catch (RestClientException exception) {
      throw providerFailure(exception);
    }
  }

  private ExternalProviderException providerFailure(Throwable cause) {
    return new ExternalProviderException("Translation provider is unavailable", cause);
  }
}
