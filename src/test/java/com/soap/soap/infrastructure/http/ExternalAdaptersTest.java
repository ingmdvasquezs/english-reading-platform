package com.soap.soap.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.soap.soap.application.exception.*;
import com.soap.soap.infrastructure.http.adapter.*;
import com.soap.soap.infrastructure.http.configuration.*;
import com.soap.soap.infrastructure.observability.ExternalProviderObservation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.*;

class ExternalAdaptersTest {
  private static final String KEY = "test-merriam-key";

  @Test
  void representativeCommonDirectEntriesSucceed() {
    for (var word : List.of("grandmother", "walk")) {
      var context =
          dictionary(
              "[{\"meta\":{\"id\":\""
                  + word
                  + ":1\"},\"fl\":\"noun\",\"shortdef\":[\"a valid definition\"]}]",
              word);
      assertThat(context.adapter.lookup(word, "en").meanings()).isNotEmpty();
      context.server.verify();
    }
  }

  @Test
  void merriamMapsMultipleEntriesAndAllSupportedFields() {
    var json =
        """
        [{"meta":{"id":"apple:1"},"hwi":{"hw":"ap*ple","prs":[{"ipa":"ˈæpəl","sound":{"audio":"apple001"}}]},"fl":"noun",
        "def":[{"sseq":[[["sense",{"dt":[["text","{bc}a round {it}fruit{/it}"],["vis",[{"t":"{it}fresh apples{/it}"}]]]}]]]}]},
        {"meta":{"id":"apple:2"},"fl":"verb","shortdef":["to furnish with apples"]}]
        """;
    var c = dictionary(json, "apple");
    var result = c.adapter.lookup("apple", "en");
    assertThat(result.word()).isEqualTo("apple");
    assertThat(result.phonetic()).isEqualTo("ˈæpəl");
    assertThat(result.audioUrl()).endsWith("/a/apple001.mp3");
    assertThat(result.meanings()).extracting("partOfSpeech").containsExactly("noun", "verb");
    assertThat(result.meanings().getFirst().definitions().getFirst().definition())
        .isEqualTo("a round fruit");
    assertThat(result.meanings().getFirst().definitions().getFirst().example())
        .isEqualTo("fresh apples");
    c.server.verify();
  }

  @Test
  void merriamHandlesFallbackMissingOptionalFieldsSuggestionsEmptyAndMalformed() {
    var c =
        dictionary(
            "[{\"meta\":{\"id\":\"plain:1\"},\"fl\":\"noun\",\"shortdef\":[\"simple\"]}]", "plain");
    var result = c.adapter.lookup("plain", "en");
    assertThat(result.phonetic()).isNull();
    assertThat(result.audioUrl()).isNull();
    assertThat(result.meanings().getFirst().definitions().getFirst().definition())
        .isEqualTo("simple");
    for (var value : List.of("[]", "[\"apple\",\"apply\"]"))
      assertDictionaryFailure(value, WordNotFoundException.class);
    for (var value : List.of("{}", "not-json"))
      assertDictionaryFailure(value, DictionaryInvalidResponseException.class);
  }

  @Test
  void commonWordsWithMoreEntriesThanTheOutputLimitRemainValid() {
    var entries =
        java.util.stream.IntStream.rangeClosed(1, 8)
            .mapToObj(
                number ->
                    "{\"meta\":{\"id\":\"leave:"
                        + number
                        + "\"},\"fl\":\"verb\",\"shortdef\":[\"definition "
                        + number
                        + "\"]}")
            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    var context = dictionary(entries, "leave");

    var result = context.adapter.lookup("leave", "en");

    assertThat(result.word()).isEqualTo("leave");
    assertThat(result.meanings()).isNotEmpty();
    context.server.verify();
  }

  @Test
  void resolvesConservativeInflectionSuggestionWithOnlyOneFollowUp() {
    var builder = RestClient.builder().baseUrl("https://dictionary.test");
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(once(), requestTo(url("walks")))
        .andRespond(withSuccess("[\"walk\",\"walls\"]", MediaType.APPLICATION_JSON));
    server
        .expect(once(), requestTo(url("walk")))
        .andRespond(
            withSuccess(
                "[{\"meta\":{\"id\":\"walk:1\"},\"fl\":\"verb\",\"shortdef\":[\"to move on foot\"]}]",
                MediaType.APPLICATION_JSON));

    var result = adapter(builder).lookup("walks", "en");

    assertThat(result.word()).isEqualTo("walk");
    server.verify();
  }

  @Test
  void resolvesExplicitMerriamCrossReferenceAndNeverFollowsASecondReference() {
    var builder = RestClient.builder().baseUrl("https://dictionary.test");
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(once(), requestTo(url("made")))
        .andRespond(
            withSuccess(
                "[{\"meta\":{\"id\":\"made\"},\"cxs\":[{\"cxl\":\"past tense of\",\"cxtis\":[{\"cxt\":\"make\"}]}]}]",
                MediaType.APPLICATION_JSON));
    server
        .expect(once(), requestTo(url("make")))
        .andRespond(
            withSuccess(
                "[{\"meta\":{\"id\":\"make\"},\"fl\":\"verb\",\"shortdef\":[\"to create\"]}]",
                MediaType.APPLICATION_JSON));

    assertThat(adapter(builder).lookup("made", "en").word()).isEqualTo("make");
    server.verify();

    var unresolved = RestClient.builder().baseUrl("https://dictionary.test");
    var unresolvedServer = MockRestServiceServer.bindTo(unresolved).build();
    unresolvedServer
        .expect(once(), requestTo(url("running")))
        .andRespond(withSuccess("[\"run\"]", MediaType.APPLICATION_JSON));
    unresolvedServer
        .expect(once(), requestTo(url("run")))
        .andRespond(withSuccess("[\"running\"]", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> adapter(unresolved).lookup("running", "en"))
        .isInstanceOf(WordNotFoundException.class);
    unresolvedServer.verify();
  }

  @Test
  void merriamRetriesTimeoutAndTransientStatusButNotClientStatus() {
    assertDictionaryRetry(withStatus(HttpStatus.BAD_GATEWAY));
    assertDictionaryRetry(
        request -> {
          throw new ResourceAccessException("timeout", new SocketTimeoutException());
        });
    for (var status :
        List.of(
            HttpStatus.BAD_REQUEST,
            HttpStatus.UNAUTHORIZED,
            HttpStatus.FORBIDDEN,
            HttpStatus.TOO_MANY_REQUESTS)) {
      var b = RestClient.builder().baseUrl("https://dictionary.test");
      var s = MockRestServiceServer.bindTo(b).build();
      s.expect(once(), requestTo(url("bad"))).andRespond(withStatus(status));
      assertThatThrownBy(() -> adapter(b).lookup("bad", "en"))
          .isInstanceOf(DictionaryUnavailableException.class);
      s.verify();
    }
  }

  @Test
  void twoMerriamTimeoutsBecomeControlledTimeout() {
    var b = RestClient.builder().baseUrl("https://dictionary.test");
    var s = MockRestServiceServer.bindTo(b).build();
    var timeout =
        (org.springframework.test.web.client.ResponseCreator)
            request -> {
              throw new ResourceAccessException("timeout", new SocketTimeoutException());
            };
    s.expect(once(), requestTo(url("slow"))).andRespond(timeout);
    s.expect(once(), requestTo(url("slow"))).andRespond(timeout);
    assertThatThrownBy(() -> adapter(b).lookup("slow", "en"))
        .isInstanceOf(DictionaryTimeoutException.class);
    s.verify();
  }

  @Test
  void azureSendsV3RequestHeadersBodyAndMapsUnicode() {
    var b = RestClient.builder().baseUrl("https://translate.test");
    var s = MockRestServiceServer.bindTo(b).build();
    s.expect(once(), requestTo(azureUrl()))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Ocp-Apim-Subscription-Key", "test-azure-key"))
        .andExpect(header("Ocp-Apim-Subscription-Region", "eastus2"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().string(containsString("\"Text\":\"although\"")))
        .andRespond(
            withSuccess(
                "[{\"translations\":[{\"text\":\"aunque también\",\"to\":\"es\"}]}]",
                MediaType.APPLICATION_JSON));
    assertThat(azure(b).translate("although", "en", "es")).isEqualTo("aunque también");
    s.verify();
  }

  @Test
  void azureRejectsInvalidPayloadConfigurationAndDoesNotRetryClientErrors() {
    for (var payload :
        List.of(
            "[]",
            "[{\"translations\":[]}]",
            "[{\"translations\":[{\"text\":\"\"}]}]",
            "not-json")) {
      var b = RestClient.builder().baseUrl("https://translate.test");
      var s = MockRestServiceServer.bindTo(b).build();
      s.expect(once(), requestTo(azureUrl()))
          .andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));
      assertThatThrownBy(() -> azure(b).translate("word", "en", "es"))
          .isInstanceOf(ExternalProviderException.class);
      s.verify();
    }
    assertThatThrownBy(
            () ->
                new AzureTranslatorAdapter(RestClient.create(), "", "eastus2")
                    .translate("x", "en", "es"))
        .isInstanceOf(ExternalProviderException.class);
    for (var status :
        List.of(
            HttpStatus.BAD_REQUEST,
            HttpStatus.UNAUTHORIZED,
            HttpStatus.FORBIDDEN,
            HttpStatus.TOO_MANY_REQUESTS)) assertAzureStatus(status, false);
  }

  @Test
  void azureRetriesOnlyTransientStatuses() {
    assertAzureStatus(HttpStatus.INTERNAL_SERVER_ERROR, true);
    assertAzureStatus(HttpStatus.SERVICE_UNAVAILABLE, true);
  }

  private void assertAzureStatus(HttpStatus status, boolean retry) {
    var b = RestClient.builder().baseUrl("https://translate.test");
    var s = MockRestServiceServer.bindTo(b).build();
    s.expect(once(), requestTo(azureUrl())).andRespond(withStatus(status));
    if (retry)
      s.expect(once(), requestTo(azureUrl()))
          .andRespond(
              withSuccess(
                  "[{\"translations\":[{\"text\":\"palabra\"}]}]", MediaType.APPLICATION_JSON));
    if (retry) assertThat(azure(b).translate("word", "en", "es")).isEqualTo("palabra");
    else
      assertThatThrownBy(() -> azure(b).translate("word", "en", "es"))
          .isInstanceOf(ExternalProviderException.class);
    s.verify();
  }

  private void assertDictionaryRetry(org.springframework.test.web.client.ResponseCreator first) {
    var b = RestClient.builder().baseUrl("https://dictionary.test");
    var s = MockRestServiceServer.bindTo(b).build();
    s.expect(once(), requestTo(url("bridge"))).andRespond(first);
    s.expect(once(), requestTo(url("bridge")))
        .andRespond(withSuccess("[{\"meta\":{\"id\":\"bridge:1\"}}]", MediaType.APPLICATION_JSON));
    assertThat(adapter(b).lookup("bridge", "en").word()).isEqualTo("bridge");
    s.verify();
  }

  private void assertDictionaryFailure(String json, Class<? extends Throwable> type) {
    var c = dictionary(json, "missing");
    assertThatThrownBy(() -> c.adapter.lookup("missing", "en")).isInstanceOf(type);
    c.server.verify();
  }

  private Context dictionary(String json, String word) {
    var b = RestClient.builder().baseUrl("https://dictionary.test");
    var s = MockRestServiceServer.bindTo(b).build();
    s.expect(once(), requestTo(url(word)))
        .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    return new Context(adapter(b), s);
  }

  private MerriamWebsterDictionaryAdapter adapter(RestClient.Builder b) {
    return new MerriamWebsterDictionaryAdapter(
        b.build(),
        KEY,
        ExternalProviderLimits.defaults(),
        new DictionaryClientPolicy(Duration.ofSeconds(7), 1, Duration.ZERO));
  }

  private AzureTranslatorAdapter azure(RestClient.Builder b) {
    return new AzureTranslatorAdapter(
        b.build(),
        "test-azure-key",
        "eastus2",
        ExternalProviderLimits.defaults(),
        new TranslationClientPolicy(Duration.ofSeconds(7), 1, Duration.ZERO),
        new ExternalProviderObservation(new SimpleMeterRegistry()));
  }

  private String url(String word) {
    return "https://dictionary.test/api/v3/references/learners/json/" + word + "?key=" + KEY;
  }

  private String azureUrl() {
    return "https://translate.test/translate?api-version=3.0&from=en&to=es";
  }

  private record Context(MerriamWebsterDictionaryAdapter adapter, MockRestServiceServer server) {}
}
