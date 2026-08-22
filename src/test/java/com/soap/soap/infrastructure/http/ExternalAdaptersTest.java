package com.soap.soap.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.soap.soap.application.exception.ExternalProviderException;
import com.soap.soap.application.exception.WordNotFoundException;
import com.soap.soap.infrastructure.http.adapter.FreeDictionaryAdapter;
import com.soap.soap.infrastructure.http.adapter.LibreTranslateAdapter;
import com.soap.soap.infrastructure.http.configuration.ExternalProviderLimits;
import com.soap.soap.infrastructure.http.configuration.LimitedResponseBodyInterceptor;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class ExternalAdaptersTest {
  @Test
  void eachProviderAdapterHasExactlyOneSpringInjectionConstructor() {
    assertThat(
            java.util.stream.Stream.of(FreeDictionaryAdapter.class, LibreTranslateAdapter.class)
                .map(
                    type ->
                        java.util.Arrays.stream(type.getConstructors())
                            .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                            .count()))
        .containsExactly(1L, 1L);
  }

  @Test
  void mapsDictionaryPhoneticAudioMeaningsAndLimitsDefinitions() {
    var builder = RestClient.builder().baseUrl("https://dictionary.test");
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(once(), requestTo("https://dictionary.test/api/v2/entries/en/bridge"))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                [{"word":"bridge","phonetics":[{"text":"/brɪdʒ/","audio":"//audio.test/bridge.mp3"}],
                  "meanings":[{"partOfSpeech":"noun","definitions":[
                    {"definition":"d1","example":"e1"},{"definition":"d2"},
                    {"definition":"d3"},{"definition":"d4"}]},
                    {"partOfSpeech":"verb","definitions":[{"definition":"connect"}]}]}]
                """,
                MediaType.APPLICATION_JSON));

    var result = new FreeDictionaryAdapter(builder.build()).lookup("bridge", "en");

    assertThat(result.phonetic()).isEqualTo("/brɪdʒ/");
    assertThat(result.audioUrl()).isEqualTo("https://audio.test/bridge.mp3");
    assertThat(result.meanings()).hasSize(2);
    assertThat(result.meanings().getFirst().definitions()).hasSize(3);
    server.verify();
  }

  @Test
  void toleratesMissingPhoneticAndAudio() {
    var builder = RestClient.builder().baseUrl("https://dictionary.test");
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://dictionary.test/api/v2/entries/en/plain"))
        .andRespond(
            withSuccess("[{\"word\":\"plain\",\"meanings\":[]}]", MediaType.APPLICATION_JSON));

    var result = new FreeDictionaryAdapter(builder.build()).lookup("plain", "en");

    assertThat(result.phonetic()).isNull();
    assertThat(result.audioUrl()).isNull();
  }

  @Test
  void malformedDictionaryResponseBecomesAProviderFailure() {
    var builder = RestClient.builder().baseUrl("https://dictionary.test");
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://dictionary.test/api/v2/entries/en/broken"))
        .andRespond(withSuccess("[{\"unexpected\":true}]", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> new FreeDictionaryAdapter(builder.build()).lookup("broken", "en"))
        .isInstanceOf(ExternalProviderException.class)
        .hasMessage("Dictionary provider is unavailable");
  }

  @Test
  void mapsDictionaryNotFoundAndProviderFailures() {
    assertDictionaryFailure(withResourceNotFound(), WordNotFoundException.class);
    assertDictionaryFailure(withServerError(), ExternalProviderException.class);
    assertDictionaryFailure(
        request -> {
          throw new ResourceAccessException("timeout", new SocketTimeoutException());
        },
        ExternalProviderException.class);
  }

  @Test
  void rejectsAnOversizedDictionaryBodyBeforeJsonMapping() {
    var builder =
        RestClient.builder()
            .baseUrl("https://dictionary.test")
            .requestInterceptor(new LimitedResponseBodyInterceptor(100));
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://dictionary.test/api/v2/entries/en/large"))
        .andRespond(
            withSuccess(
                "[{\"word\":\"large\",\"padding\":\"" + "x".repeat(500) + "\"}]",
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> new FreeDictionaryAdapter(builder.build()).lookup("large", "en"))
        .isInstanceOf(ExternalProviderException.class);
  }

  @Test
  void rejectsTooManyMeaningsAndOverlongDefinitions() {
    assertDictionaryPayloadRejected(
        "[{\"word\":\"large\",\"meanings\":["
            + java.util.stream.IntStream.range(0, 21)
                .mapToObj(
                    index -> "{\"partOfSpeech\":\"noun\",\"definitions\":[{\"definition\":\"d\"}]}")
                .collect(java.util.stream.Collectors.joining(","))
            + "]}]");
    assertDictionaryPayloadRejected(
        "[{\"word\":\"large\",\"meanings\":[{\"partOfSpeech\":\"noun\","
            + "\"definitions\":[{\"definition\":\""
            + "d".repeat(2_001)
            + "\"}]}]}]");
  }

  @Test
  void rejectsAnOverlongTranslation() {
    var builder = RestClient.builder().baseUrl("https://translate.test");
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://translate.test/translate"))
        .andRespond(
            withSuccess(
                "{\"translatedText\":\"" + "x".repeat(10_001) + "\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(
            () -> new LibreTranslateAdapter(builder.build(), "").translate("bridge", "en", "es"))
        .isInstanceOf(ExternalProviderException.class);
  }

  @Test
  void translatesWithoutAnApiKey() {
    assertTranslationRequest("", false);
  }

  @Test
  void translatesWithAConfiguredApiKey() {
    assertTranslationRequest("secret-key", true);
  }

  @Test
  void mapsTranslationTimeoutAndServerErrorToProviderFailure() {
    assertTranslationFailure(withServerError());
    assertTranslationFailure(
        request -> {
          throw new ResourceAccessException("timeout", new SocketTimeoutException());
        });
  }

  private void assertTranslationRequest(String apiKey, boolean expectsKey) {
    var builder = RestClient.builder().baseUrl("https://translate.test");
    var server = MockRestServiceServer.bindTo(builder).build();
    var expectation =
        server.expect(requestTo("https://translate.test/translate")).andExpect(method(POST));
    if (expectsKey) {
      expectation.andExpect(content().string(containsString("\"api_key\":\"secret-key\"")));
    } else {
      expectation.andExpect(content().string(org.hamcrest.Matchers.not(containsString("api_key"))));
    }
    expectation.andRespond(
        withSuccess("{\"translatedText\":\"puente\"}", MediaType.APPLICATION_JSON));

    assertThat(new LibreTranslateAdapter(builder.build(), apiKey).translate("bridge", "en", "es"))
        .isEqualTo("puente");
    server.verify();
  }

  private void assertDictionaryFailure(
      org.springframework.test.web.client.ResponseCreator response,
      Class<? extends Throwable> expected) {
    var builder = RestClient.builder().baseUrl("https://dictionary.test");
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://dictionary.test/api/v2/entries/en/missing"))
        .andRespond(response);
    assertThatThrownBy(() -> new FreeDictionaryAdapter(builder.build()).lookup("missing", "en"))
        .isInstanceOf(expected);
  }

  private void assertDictionaryPayloadRejected(String payload) {
    var builder = RestClient.builder().baseUrl("https://dictionary.test");
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://dictionary.test/api/v2/entries/en/large"))
        .andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));
    assertThatThrownBy(
            () ->
                new FreeDictionaryAdapter(builder.build(), ExternalProviderLimits.defaults())
                    .lookup("large", "en"))
        .isInstanceOf(ExternalProviderException.class);
  }

  private void assertTranslationFailure(
      org.springframework.test.web.client.ResponseCreator response) {
    var builder = RestClient.builder().baseUrl("https://translate.test");
    var server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("https://translate.test/translate")).andRespond(response);
    assertThatThrownBy(
            () -> new LibreTranslateAdapter(builder.build(), "").translate("bridge", "en", "es"))
        .isInstanceOf(ExternalProviderException.class)
        .hasMessage("Translation provider is unavailable");
  }
}
