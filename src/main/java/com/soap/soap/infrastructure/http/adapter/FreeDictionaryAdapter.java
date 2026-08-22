package com.soap.soap.infrastructure.http.adapter;

import com.soap.soap.application.exception.ExternalProviderException;
import com.soap.soap.application.exception.WordNotFoundException;
import com.soap.soap.application.model.DictionaryEntry;
import com.soap.soap.application.model.WordDefinition;
import com.soap.soap.application.model.WordMeaning;
import com.soap.soap.application.port.out.DictionaryPort;
import com.soap.soap.infrastructure.http.configuration.ExternalProviderLimits;
import com.soap.soap.infrastructure.http.dto.FreeDictionaryResponse;
import com.soap.soap.infrastructure.observability.ExternalProviderObservation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class FreeDictionaryAdapter implements DictionaryPort {
  private static final int MAX_DEFINITIONS_PER_MEANING = 3;
  private final RestClient client;
  private final ExternalProviderLimits limits;
  private final ExternalProviderObservation observation;

  public FreeDictionaryAdapter(@Qualifier("freeDictionaryRestClient") RestClient client) {
    this(
        client,
        ExternalProviderLimits.defaults(),
        new ExternalProviderObservation(new SimpleMeterRegistry()));
  }

  public FreeDictionaryAdapter(
      @Qualifier("freeDictionaryRestClient") RestClient client, ExternalProviderLimits limits) {
    this(client, limits, new ExternalProviderObservation(new SimpleMeterRegistry()));
  }

  @Autowired
  public FreeDictionaryAdapter(
      @Qualifier("freeDictionaryRestClient") RestClient client,
      ExternalProviderLimits limits,
      ExternalProviderObservation observation) {
    this.client = client;
    this.limits = limits;
    this.observation = observation;
  }

  @Override
  public DictionaryEntry lookup(String word, String language) {
    return observation.observe("free_dictionary", () -> doLookup(word, language));
  }

  private DictionaryEntry doLookup(String word, String language) {
    try {
      var responses =
          client
              .get()
              .uri("/api/v2/entries/{language}/{word}", language, word)
              .retrieve()
              .body(FreeDictionaryResponse[].class);
      if (responses == null
          || responses.length == 0
          || responses.length > limits.maximumEntries()) {
        throw providerFailure(null);
      }
      return map(responses);
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new WordNotFoundException(word);
      }
      throw providerFailure(exception);
    } catch (RestClientException exception) {
      throw providerFailure(exception);
    } catch (RuntimeException exception) {
      if (exception instanceof WordNotFoundException
          || exception instanceof ExternalProviderException) {
        throw exception;
      }
      throw providerFailure(exception);
    }
  }

  private DictionaryEntry map(FreeDictionaryResponse[] responses) {
    var first = responses[0];
    if (isBlank(first.word())) {
      throw providerFailure(null);
    }
    var allMeanings =
        Arrays.stream(responses)
            .filter(Objects::nonNull)
            .flatMap(response -> safe(response.meanings()).stream())
            .toList();
    if (allMeanings.size() > limits.maximumMeanings()) {
      throw providerFailure(null);
    }
    var meanings =
        allMeanings.stream()
            .filter(meaning -> !isBlank(meaning.partOfSpeech()))
            .map(
                meaning ->
                    new WordMeaning(
                        meaning.partOfSpeech(),
                        safe(meaning.definitions()).stream()
                            .filter(definition -> !isBlank(definition.definition()))
                            .limit(MAX_DEFINITIONS_PER_MEANING)
                            .map(
                                definition ->
                                    new WordDefinition(
                                        checked(
                                            definition.definition(),
                                            limits.maximumDefinitionCharacters()),
                                        checkedNullable(
                                            definition.example(),
                                            limits.maximumExampleCharacters())))
                            .toList()))
            .filter(meaning -> !meaning.definitions().isEmpty())
            .toList();
    var phonetic = firstNonBlank(first.phonetic(), phoneticTexts(responses));
    var audio = firstNonBlank(null, phoneticAudio(responses));
    if (audio != null && audio.startsWith("//")) {
      audio = "https:" + audio;
    }
    phonetic = checkedNullable(phonetic, limits.maximumPhoneticCharacters());
    audio = checkedNullable(audio, limits.maximumAudioUrlCharacters());
    return new DictionaryEntry(first.word(), phonetic, audio, meanings);
  }

  private String checked(String value, int maximumCharacters) {
    if (value.length() > maximumCharacters) {
      throw providerFailure(null);
    }
    return value;
  }

  private String checkedNullable(String value, int maximumCharacters) {
    var normalized = blankToNull(value);
    return normalized == null ? null : checked(normalized, maximumCharacters);
  }

  private List<String> phoneticTexts(FreeDictionaryResponse[] responses) {
    return Arrays.stream(responses)
        .flatMap(response -> safe(response.phonetics()).stream())
        .map(FreeDictionaryResponse.Phonetic::text)
        .toList();
  }

  private List<String> phoneticAudio(FreeDictionaryResponse[] responses) {
    return Arrays.stream(responses)
        .flatMap(response -> safe(response.phonetics()).stream())
        .map(FreeDictionaryResponse.Phonetic::audio)
        .toList();
  }

  private String firstNonBlank(String preferred, List<String> alternatives) {
    if (!isBlank(preferred)) {
      return preferred;
    }
    return alternatives.stream().filter(value -> !isBlank(value)).findFirst().orElse(null);
  }

  private <T> List<T> safe(List<T> values) {
    return values == null ? List.of() : values;
  }

  private String blankToNull(String value) {
    return isBlank(value) ? null : value;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private ExternalProviderException providerFailure(Throwable cause) {
    return new ExternalProviderException("Dictionary provider is unavailable", cause);
  }
}
