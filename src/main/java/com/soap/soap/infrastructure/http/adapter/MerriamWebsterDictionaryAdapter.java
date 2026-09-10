package com.soap.soap.infrastructure.http.adapter;

import com.soap.soap.application.exception.DictionaryInvalidResponseException;
import com.soap.soap.application.exception.DictionaryTimeoutException;
import com.soap.soap.application.exception.DictionaryUnavailableException;
import com.soap.soap.application.exception.ExternalProviderException;
import com.soap.soap.application.exception.WordNotFoundException;
import com.soap.soap.application.model.DictionaryEntry;
import com.soap.soap.application.model.WordDefinition;
import com.soap.soap.application.model.WordMeaning;
import com.soap.soap.application.port.out.DictionaryPort;
import com.soap.soap.infrastructure.http.configuration.DictionaryClientPolicy;
import com.soap.soap.infrastructure.http.configuration.ExternalProviderLimits;
import com.soap.soap.infrastructure.observability.ExternalProviderObservation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
public class MerriamWebsterDictionaryAdapter implements DictionaryPort {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MerriamWebsterDictionaryAdapter.class);
  private static final int MAX_DEFINITIONS_PER_MEANING = 3;
  private final RestClient client;
  private final String apiKey;
  private final ExternalProviderLimits limits;
  private final DictionaryClientPolicy policy;
  private final MerriamMarkupSanitizer sanitizer;
  private final MerriamAudioUrlBuilder audioUrls;
  private final ExternalProviderObservation observation;

  public MerriamWebsterDictionaryAdapter(
      @Qualifier("merriamWebsterRestClient") RestClient client, String apiKey) {
    this(
        client,
        apiKey,
        ExternalProviderLimits.defaults(),
        DictionaryClientPolicy.defaults(),
        new MerriamMarkupSanitizer(),
        new MerriamAudioUrlBuilder(),
        new ExternalProviderObservation(new SimpleMeterRegistry()));
  }

  public MerriamWebsterDictionaryAdapter(
      @Qualifier("merriamWebsterRestClient") RestClient client,
      String apiKey,
      ExternalProviderLimits limits,
      DictionaryClientPolicy policy) {
    this(
        client,
        apiKey,
        limits,
        policy,
        new MerriamMarkupSanitizer(),
        new MerriamAudioUrlBuilder(),
        new ExternalProviderObservation(new SimpleMeterRegistry()));
  }

  @Autowired
  public MerriamWebsterDictionaryAdapter(
      @Qualifier("merriamWebsterRestClient") RestClient client,
      @Value("${dictionary.merriam-webster.api-key:}") String apiKey,
      ExternalProviderLimits limits,
      DictionaryClientPolicy policy,
      MerriamMarkupSanitizer sanitizer,
      MerriamAudioUrlBuilder audioUrls,
      ExternalProviderObservation observation) {
    this.client = client;
    this.apiKey = apiKey;
    this.limits = limits;
    this.policy = policy;
    this.sanitizer = sanitizer;
    this.audioUrls = audioUrls;
    this.observation = observation;
  }

  @Override
  public DictionaryEntry lookup(String word, String language) {
    return observation.observe("merriam_webster", () -> lookupWithResolution(word));
  }

  private DictionaryEntry lookupWithResolution(String requestedWord) {
    try {
      var entry = lookupWithRetry(requestedWord);
      logResolution(requestedWord, entry.word());
      return entry;
    } catch (ResolveReferenceException exception) {
      LOGGER.info(
          "dictionary.lookup.reference provider=merriam_webster requestedWord={} resolvedWord={}",
          requestedWord,
          exception.word());
      try {
        var entry = lookupWithRetry(exception.word());
        logResolution(requestedWord, entry.word());
        return entry;
      } catch (ResolveReferenceException repeatedReference) {
        LOGGER.info(
            "dictionary.lookup.unresolved_reference provider=merriam_webster requestedWord={}",
            requestedWord);
        throw new WordNotFoundException(requestedWord);
      }
    }
  }

  private DictionaryEntry lookupWithRetry(String word) {
    requireConfiguration();
    var deadline = System.nanoTime() + policy.timeout().toNanos();
    RetryableDictionaryException last = null;
    for (var attempt = 0; attempt <= policy.maxRetries(); attempt++) {
      try {
        return doLookup(word);
      } catch (RetryableDictionaryException exception) {
        last = exception;
        if (attempt == policy.maxRetries() || !canRetry(deadline)) {
          break;
        }
        LOGGER.warn(
            "dictionary.lookup.retry provider=merriam_webster attempt={} reason={}",
            attempt + 1,
            exception instanceof RetryableTimeoutException ? "timeout" : "transient_status");
        pause(deadline);
      }
    }
    if (last instanceof RetryableTimeoutException) {
      throw new DictionaryTimeoutException(last.getCause());
    }
    throw new DictionaryUnavailableException(last == null ? null : last.getCause());
  }

  private DictionaryEntry doLookup(String word) {
    try {
      var response =
          client
              .get()
              .uri(
                  builder ->
                      builder
                          .path("/api/v3/references/learners/json/{word}")
                          .queryParam("key", apiKey)
                          .build(word))
              .retrieve()
              .body(JsonNode.class);
      return map(response, word);
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new WordNotFoundException(word);
      }
      if (retryable(exception.getStatusCode().value())) {
        throw new RetryableStatusException(exception);
      }
      throw new DictionaryUnavailableException(exception);
    } catch (RestClientException exception) {
      if (isTimeout(exception)) {
        throw new RetryableTimeoutException(exception);
      }
      if (isJsonFailure(exception)) {
        throw invalidResponse(exception);
      }
      throw new DictionaryUnavailableException(exception);
    } catch (RuntimeException exception) {
      if (exception instanceof WordNotFoundException
          || exception instanceof ExternalProviderException
          || exception instanceof RetryableDictionaryException
          || exception instanceof ResolveReferenceException) {
        throw exception;
      }
      throw invalidResponse(exception);
    }
  }

  private DictionaryEntry map(JsonNode root, String requestedWord) {
    if (root == null || !root.isArray()) {
      throw invalidResponse(null);
    }
    if (root.isEmpty()) {
      throw new WordNotFoundException(requestedWord);
    }
    if (root.get(0).isTextual()) {
      LOGGER.debug("dictionary.lookup.suggestions provider=merriam_webster");
      var candidate = inflectionSuggestion(requestedWord, root);
      if (candidate != null) {
        throw new ResolveReferenceException(candidate);
      }
      throw new WordNotFoundException(requestedWord);
    }
    var meanings = new LinkedHashMap<String, LinkedHashSet<WordDefinition>>();
    String headword = null;
    String phonetic = null;
    String audioUrl = null;
    String referencedHeadword = null;
    var processedEntries = 0;
    for (var entry : root) {
      if (processedEntries++ >= limits.maximumEntries()) {
        break;
      }
      if (!entry.isObject()) {
        throw invalidResponse(null);
      }
      if (headword == null) {
        headword = headword(entry);
      }
      var pronunciation = pronunciation(entry);
      if (phonetic == null && pronunciation.phonetic() != null) {
        phonetic = pronunciation.phonetic();
      }
      if (audioUrl == null && pronunciation.audioUrl() != null) {
        audioUrl = pronunciation.audioUrl();
      }
      if (referencedHeadword == null) {
        referencedHeadword = crossReference(entry);
      }
      var partOfSpeech = text(entry.get("fl"));
      if (partOfSpeech == null) {
        continue;
      }
      var definitions = extractDefinitions(entry);
      if (definitions.isEmpty()) {
        definitions = shortDefinitions(entry);
      }
      if (!definitions.isEmpty()) {
        meanings
            .computeIfAbsent(partOfSpeech, ignored -> new LinkedHashSet<>())
            .addAll(definitions);
      }
    }
    if (headword == null || meanings.size() > limits.maximumMeanings()) {
      throw invalidResponse(null);
    }
    if (meanings.isEmpty()
        && referencedHeadword != null
        && !referencedHeadword.equalsIgnoreCase(requestedWord)) {
      throw new ResolveReferenceException(referencedHeadword);
    }
    var mappedMeanings =
        meanings.entrySet().stream()
            .map(
                item ->
                    new WordMeaning(
                        checked(item.getKey(), limits.maximumDefinitionCharacters()),
                        item.getValue().stream().limit(MAX_DEFINITIONS_PER_MEANING).toList()))
            .toList();
    return new DictionaryEntry(
        headword,
        checkedNullable(phonetic, limits.maximumPhoneticCharacters()),
        checkedNullable(audioUrl, limits.maximumAudioUrlCharacters()),
        mappedMeanings);
  }

  private List<WordDefinition> extractDefinitions(JsonNode entry) {
    var result = new ArrayList<WordDefinition>();
    collectDefinitionTexts(entry.get("def"), result);
    return result;
  }

  private void collectDefinitionTexts(JsonNode node, List<WordDefinition> result) {
    if (node == null || result.size() >= MAX_DEFINITIONS_PER_MEANING) {
      return;
    }
    if (node.isObject()) {
      var dt = node.get("dt");
      if (dt != null && dt.isArray()) {
        String example = firstExample(dt);
        for (var item : dt) {
          if (item.isArray() && item.size() > 1 && "text".equals(item.get(0).asText())) {
            var definition = sanitizer.sanitize(item.get(1).asText());
            if (definition != null) {
              result.add(
                  new WordDefinition(
                      checked(definition, limits.maximumDefinitionCharacters()),
                      checkedNullable(example, limits.maximumExampleCharacters())));
            }
          }
        }
      }
      for (var child : node) {
        collectDefinitionTexts(child, result);
      }
    } else if (node.isArray()) {
      for (var child : node) {
        collectDefinitionTexts(child, result);
      }
    }
  }

  private String firstExample(JsonNode dt) {
    for (var item : dt) {
      if (item.isArray() && item.size() > 1 && "vis".equals(item.get(0).asText())) {
        for (var example : item.get(1)) {
          var sanitized = sanitizer.sanitize(text(example.get("t")));
          if (sanitized != null) {
            return sanitized;
          }
        }
      }
    }
    return null;
  }

  private List<WordDefinition> shortDefinitions(JsonNode entry) {
    var shortdef = entry.get("shortdef");
    if (shortdef == null || !shortdef.isArray()) {
      return List.of();
    }
    var result = new ArrayList<WordDefinition>();
    for (var definition : shortdef) {
      var sanitized = sanitizer.sanitize(text(definition));
      if (sanitized != null) {
        result.add(
            new WordDefinition(checked(sanitized, limits.maximumDefinitionCharacters()), null));
      }
    }
    return result;
  }

  private String headword(JsonNode entry) {
    var value = text(entry.path("hwi").get("hw"));
    if (value != null) {
      return value.replace("*", "");
    }
    var id = text(entry.path("meta").get("id"));
    return id == null ? null : id.replaceFirst(":\\d+$", "");
  }

  private String crossReference(JsonNode entry) {
    var crossReferences = entry.get("cxs");
    if (crossReferences == null || !crossReferences.isArray()) {
      return null;
    }
    for (var crossReference : crossReferences) {
      var targets = crossReference.get("cxtis");
      if (targets == null || !targets.isArray()) {
        continue;
      }
      for (var target : targets) {
        var value = text(target.get("cxt"));
        if (value != null) {
          return value.replace("*", "");
        }
      }
    }
    return null;
  }

  private String inflectionSuggestion(String requestedWord, JsonNode suggestions) {
    for (var suggestion : suggestions) {
      var candidate = text(suggestion);
      if (candidate != null && isConservativeInflection(requestedWord, candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private boolean isConservativeInflection(String requestedWord, String candidate) {
    var requested = requestedWord.toLowerCase(java.util.Locale.ROOT);
    var base = candidate.toLowerCase(java.util.Locale.ROOT);
    return requested.equals(base + "s")
        || requested.equals(base + "es")
        || requested.equals(base + "ed")
        || requested.equals(base + "d")
        || requested.equals(base + "ing")
        || requested.equals(base.substring(0, Math.max(0, base.length() - 1)) + "ing")
        || doubledFinalConsonantInflection(requested, base, "ed")
        || doubledFinalConsonantInflection(requested, base, "ing");
  }

  private boolean doubledFinalConsonantInflection(String requested, String base, String suffix) {
    return !base.isEmpty() && requested.equals(base + base.charAt(base.length() - 1) + suffix);
  }

  private void logResolution(String requestedWord, String resolvedHeadword) {
    LOGGER.info(
        "dictionary.lookup.success provider=merriam_webster requestedWord={} resolvedHeadword={}",
        requestedWord,
        resolvedHeadword);
  }

  private Pronunciation pronunciation(JsonNode entry) {
    var pronunciations = entry.path("hwi").get("prs");
    if (pronunciations == null || !pronunciations.isArray()) {
      return new Pronunciation(null, null);
    }
    String firstIpa = null;
    for (var pronunciation : pronunciations) {
      var ipa = text(pronunciation.get("ipa"));
      if (firstIpa == null) {
        firstIpa = ipa;
      }
      var audio = text(pronunciation.path("sound").get("audio"));
      if (audio != null) {
        return new Pronunciation(ipa == null ? firstIpa : ipa, audioUrls.build(audio));
      }
    }
    return new Pronunciation(firstIpa, null);
  }

  private String text(JsonNode node) {
    return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
  }

  private String checkedNullable(String value, int maximum) {
    return value == null ? null : checked(value, maximum);
  }

  private String checked(String value, int maximum) {
    if (value.length() > maximum) {
      throw invalidResponse(null);
    }
    return value;
  }

  private void requireConfiguration() {
    if (apiKey == null || apiKey.isBlank()) {
      throw new DictionaryUnavailableException(
          new IllegalStateException("Merriam-Webster provider is not configured"));
    }
  }

  private boolean retryable(int status) {
    return status == 500 || status == 502 || status == 503 || status == 504 || status == 522;
  }

  private boolean isTimeout(Throwable throwable) {
    for (var current = throwable; current != null; current = current.getCause()) {
      if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
        return true;
      }
    }
    return false;
  }

  private boolean isJsonFailure(Throwable throwable) {
    for (var current = throwable; current != null; current = current.getCause()) {
      if (current.getClass().getName().startsWith("tools.jackson.")) {
        return true;
      }
    }
    return false;
  }

  private boolean canRetry(long deadline) {
    return System.nanoTime() + policy.retryDelay().toNanos() < deadline;
  }

  private void pause(long deadline) {
    var remaining = Duration.ofNanos(Math.max(0, deadline - System.nanoTime()));
    var delay = policy.retryDelay().compareTo(remaining) < 0 ? policy.retryDelay() : remaining;
    try {
      Thread.sleep(delay);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new DictionaryUnavailableException(exception);
    }
  }

  private DictionaryInvalidResponseException invalidResponse(Throwable cause) {
    LOGGER.warn("dictionary.lookup.invalid_response provider=merriam_webster");
    return new DictionaryInvalidResponseException(cause);
  }

  private record Pronunciation(String phonetic, String audioUrl) {}

  private static class RetryableDictionaryException extends RuntimeException {
    RetryableDictionaryException(Throwable cause) {
      super(cause);
    }
  }

  private static final class RetryableTimeoutException extends RetryableDictionaryException {
    RetryableTimeoutException(Throwable cause) {
      super(cause);
    }
  }

  private static final class RetryableStatusException extends RetryableDictionaryException {
    RetryableStatusException(Throwable cause) {
      super(cause);
    }
  }

  private static final class ResolveReferenceException extends RuntimeException {
    private final String word;

    ResolveReferenceException(String word) {
      this.word = word;
    }

    String word() {
      return word;
    }
  }
}
