package com.soap.soap.infrastructure.http.adapter;

import com.soap.soap.application.exception.ExternalProviderException;
import com.soap.soap.application.port.out.TranslationPort;
import com.soap.soap.infrastructure.http.dto.LibreTranslateResponse;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class LibreTranslateAdapter implements TranslationPort {
  private final RestClient client;
  private final String apiKey;

  public LibreTranslateAdapter(
      @Qualifier("libreTranslateRestClient") RestClient client,
      @Value("${translation.libre.api-key:}") String apiKey) {
    this.client = client;
    this.apiKey = apiKey;
  }

  @Override
  public String translate(String text, String sourceLanguage, String targetLanguage) {
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
          || response.translatedText().isBlank()) {
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
