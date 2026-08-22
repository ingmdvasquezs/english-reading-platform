package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

import com.soap.soap.application.exception.ConcurrentVocabularyModificationException;
import com.soap.soap.application.exception.ExternalProviderException;
import com.soap.soap.application.exception.WordAlreadyInVocabularyException;
import com.soap.soap.application.model.DictionaryEntry;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.WordMeaning;
import com.soap.soap.application.port.out.DictionaryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.TranslationPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.WordRepositoryPort;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import com.soap.soap.infrastructure.persistence.repository.JpaWordRepository;
import com.soap.soap.infrastructure.soap.resolver.SoapExceptionResolver;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import javax.xml.transform.stream.StreamSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ws.context.DefaultMessageContext;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.SoapVersion;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers(disabledWithoutDocker = true)
class SoapApplicationTests {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private UserRepositoryPort users;
  @Autowired private WordRepositoryPort words;
  @Autowired private JpaWordRepository jpaWords;
  @Autowired private ReadingRepositoryPort readings;
  @Autowired private UserVocabularyRepositoryPort vocabulary;
  @Autowired private ApplicationContext applicationContext;
  @Autowired private SoapExceptionResolver soapExceptionResolver;
  @Autowired private MeterRegistry meterRegistry;
  @LocalServerPort private int serverPort;
  @MockitoBean private DictionaryPort dictionaryPort;
  @MockitoBean private TranslationPort translationPort;

  private User user;

  @Test
  void concurrentWordResolutionReturnsTheSingleDatabaseWinner() throws Exception {
    var value = "concurrent-" + java.util.UUID.randomUUID();
    var results =
        runConcurrently(() -> words.resolve(value, "en"), () -> words.resolve(value, "en"));

    assertThat(results.first()).isEqualTo(results.second());
    assertThat(jpaWords.countByNormalizedValueAndLanguage(value, "en")).isEqualTo(1);
  }

  @Test
  void concurrentVocabularyInsertTranslatesTheLosingUniqueConstraint() throws Exception {
    var word = words.resolve("vocabulary-race-" + java.util.UUID.randomUUID(), "en");
    var now = LocalDateTime.now();
    var first = new UserVocabulary(null, user, word, VocabularyStatus.NEW, now, null);
    var second = new UserVocabulary(null, user, word, VocabularyStatus.NEW, now, null);

    var outcomes =
        runConcurrentlyCapturing(() -> vocabulary.save(first), () -> vocabulary.save(second));

    assertThat(outcomes).filteredOn(UserVocabulary.class::isInstance).hasSize(1);
    assertThat(outcomes).filteredOn(WordAlreadyInVocabularyException.class::isInstance).hasSize(1);
    assertThat(vocabulary.findByUserIdAndWordId(user.id(), word.id())).isPresent();
  }

  @Test
  void concurrentVocabularyUpdatesDetectTheLostUpdate() throws Exception {
    var word = words.resolve("status-race-" + java.util.UUID.randomUUID(), "en");
    var saved =
        vocabulary.save(
            new UserVocabulary(null, user, word, VocabularyStatus.NEW, LocalDateTime.now(), null));
    var first = vocabulary.findByUserIdAndWordId(user.id(), word.id()).orElseThrow();
    var second = vocabulary.findByUserIdAndWordId(user.id(), word.id()).orElseThrow();
    assertThat(first.version()).isEqualTo(saved.version());

    var outcomes =
        runConcurrentlyCapturing(
            () ->
                vocabulary.save(
                    first.changeStatus(VocabularyStatus.LEARNING, java.time.Clock.systemUTC())),
            () ->
                vocabulary.save(
                    second.changeStatus(VocabularyStatus.IGNORED, java.time.Clock.systemUTC())));

    assertThat(outcomes).filteredOn(UserVocabulary.class::isInstance).hasSize(1);
    assertThat(outcomes)
        .filteredOn(ConcurrentVocabularyModificationException.class::isInstance)
        .hasSize(1);
    assertThat(vocabulary.findByUserIdAndWordId(user.id(), word.id()).orElseThrow().version())
        .isEqualTo(saved.version() + 1);
  }

  @Test
  void oversizedAuthenticatedRegisterReadingReturnsHttp413BeforeSecurityOrSoapDispatch()
      throws Exception {
    var token = createHttpUserToken();

    var normalReading = registerReadingEnvelope("A short reading");
    var unauthenticated = postSoap(normalReading, null);
    assertThat(unauthenticated.statusCode()).isNotEqualTo(200).isNotEqualTo(403);
    assertThat(unauthenticated.body()).contains("Fault");
    assertThat(postSoap(normalReading, token).statusCode()).isEqualTo(200);

    var oversized = postSoap(registerReadingEnvelope("a".repeat(1_200_001)), token);
    assertThat(oversized.statusCode()).isEqualTo(413);
    assertThat(oversized.body()).isEqualTo("Request too large").doesNotContain("Fault");
    assertThat(oversized.headers().firstValue("X-Correlation-ID")).isPresent();
  }

  @Test
  void serverFaultIsSanitizedAndReturnsTheCorrelationId() throws Exception {
    var token = createHttpUserToken();
    org.mockito.Mockito.when(dictionaryPort.lookup("bridge", "en"))
        .thenThrow(
            new ExternalProviderException(
                "Dictionary provider is unavailable",
                new IllegalStateException("api-key=secret provider=https://private.example")));
    var lookup =
        soapEnvelope(
            "<lookupWordRequest xmlns=\"http://soap.com/english-reading/readings\">"
                + "<word>bridge</word></lookupWordRequest>");

    var response = postSoap(lookup, token, "server-fault-42");

    assertThat(response.statusCode()).isEqualTo(500);
    assertThat(response.headers().firstValue("X-Correlation-ID")).contains("server-fault-42");
    assertThat(response.body())
        .contains("Internal server error")
        .doesNotContain("secret", "private.example", "api-key");
  }

  private String createHttpUserToken() throws Exception {
    var email = "http-e2e-" + java.util.UUID.randomUUID() + "@example.com";
    var register =
        soapEnvelope(
            "<registerUserRequest xmlns=\"http://soap.com/english-reading/readings\">"
                + "<name>Ada</name><email>"
                + email
                + "</email><password>secret123</password></registerUserRequest>");
    var login =
        soapEnvelope(
            "<loginRequest xmlns=\"http://soap.com/english-reading/readings\"><email>"
                + email
                + "</email><password>secret123</password></loginRequest>");

    assertThat(postSoap(register, null).statusCode()).isEqualTo(200);
    var loginResponse = postSoap(login, null);
    assertThat(loginResponse.statusCode()).isEqualTo(200);
    var tokenMatcher =
        java.util.regex.Pattern.compile("<(?:\\w+:)?accessToken>([^<]+)</(?:\\w+:)?accessToken>")
            .matcher(loginResponse.body());
    assertThat(tokenMatcher.find()).isTrue();
    return tokenMatcher.group(1);
  }

  @Test
  void correlationIdIsGeneratedReusedAndReturnedForSoapFaults() throws Exception {
    var generated = postSoap(soapEnvelope("<invalid/>"), null);
    assertThat(generated.headers().firstValue("X-Correlation-ID"))
        .hasValueSatisfying(value -> assertThat(value).matches("[0-9a-f-]{36}"));

    var received = postSoap(soapEnvelope("<invalid/>"), null, "client-request_42");
    assertThat(received.headers().firstValue("X-Correlation-ID")).contains("client-request_42");

    var invalid = postSoap(soapEnvelope("<invalid/>"), null, "unsafe value");
    assertThat(invalid.headers().firstValue("X-Correlation-ID"))
        .hasValueSatisfying(value -> assertThat(value).matches("[0-9a-f-]{36}"));
  }

  @Test
  void actuatorExposesOnlyOperationalEndpointsAndReadinessIncludesDatabase() throws Exception {
    assertThat(get("/actuator/health").body()).contains("\"status\":\"UP\"");
    assertThat(get("/actuator/health/liveness").body()).contains("\"status\":\"UP\"");
    assertThat(get("/actuator/health/readiness").body()).contains("\"status\":\"UP\"");
    assertThat(get("/actuator/prometheus").statusCode()).isEqualTo(200);
    assertThat(get("/actuator/env").statusCode()).isIn(403, 404);
    assertThat(get("/actuator/beans").statusCode()).isIn(403, 404);
  }

  @Test
  void soapMetricsAreCreatedWithoutHighCardinalityTags() throws Exception {
    postSoap(soapEnvelope("<invalid/>"), null);

    assertThat(meterRegistry.find("soap.requests").meters()).isNotEmpty();
    assertThat(meterRegistry.find("soap.request.duration").meters()).isNotEmpty();
    assertThat(
            meterRegistry.find("soap.requests").meters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getKey()))
        .doesNotContain("userId", "readingId", "word", "email", "correlationId");
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void protectedSoapOperationWithoutAuthenticationReturnsAClientFault() {
    var payload =
        """
        <getReadingRequest xmlns="http://soap.com/english-reading/readings">
          <readingId>00000000-0000-0000-0000-000000000001</readingId>
        </getReadingRequest>
        """;
    MockWebServiceClient.createClient(applicationContext)
        .sendRequest(withPayload(new StreamSource(new StringReader(payload))))
        .andExpect(clientOrSenderFault());
  }

  @Test
  void vocabularyPageSizeZeroReturnsAClientFault() {
    assertVocabularyPaginationClientFault(0, 0);
  }

  @Test
  void vocabularyPageSizeAboveMaximumReturnsAClientFault() {
    assertVocabularyPaginationClientFault(0, 101);
  }

  @Test
  void negativeVocabularyPageReturnsAClientFault() {
    assertVocabularyPaginationClientFault(-1, 10);
  }

  private void assertVocabularyPaginationClientFault(int page, int size) {
    var payload =
        """
        <listUserVocabularyRequest xmlns="http://soap.com/english-reading/readings">
          <page>%d</page>
          <size>%d</size>
        </listUserVocabularyRequest>
        """
            .formatted(page, size);
    MockWebServiceClient.createClient(applicationContext)
        .sendRequest(withPayload(new StreamSource(new StringReader(payload))))
        .andExpect(clientOrSenderFault());
  }

  @Test
  void unexpectedExceptionsRemainServerFaults() {
    var messageFactory = new SaajSoapMessageFactory();
    messageFactory.afterPropertiesSet();
    var messageContext = new DefaultMessageContext(messageFactory);

    assertThat(
            soapExceptionResolver.resolveException(
                messageContext,
                null,
                new RuntimeException("jdbc:postgresql://db/private password=secret")))
        .isTrue();
    var response = (SoapMessage) messageContext.getResponse();

    assertThat(response.getSoapBody().getFault().getFaultCode())
        .isEqualTo(SoapVersion.SOAP_11.getServerOrReceiverFaultName());
    assertThat(response.getSoapBody().getFault().getFaultStringOrReason())
        .isEqualTo("Internal server error")
        .doesNotContain("jdbc", "password", "secret");
  }

  @Test
  void optimisticLockConflictIsAControlledClientFault() {
    var messageFactory = new SaajSoapMessageFactory();
    messageFactory.afterPropertiesSet();
    var messageContext = new DefaultMessageContext(messageFactory);

    assertThat(
            soapExceptionResolver.resolveException(
                messageContext, null, new ConcurrentVocabularyModificationException()))
        .isTrue();
    var response = (SoapMessage) messageContext.getResponse();

    assertThat(response.getSoapBody().getFault().getFaultCode())
        .isEqualTo(SoapVersion.SOAP_11.getClientOrSenderFaultName());
    assertThat(response.getSoapBody().getFault().getFaultStringOrReason())
        .isEqualTo("Vocabulary entry was modified concurrently");
  }

  @Test
  void runtimeSchemaRejectsMissingRequiredElementAndInvalidStructure() {
    assertSchemaClientFault(
        """
        <registerUserRequest xmlns="http://soap.com/english-reading/readings">
          <name>Ada</name><password>secret123</password>
        </registerUserRequest>
        """);
    assertSchemaClientFault(
        """
        <registerUserRequest xmlns="http://soap.com/english-reading/readings">
          <email>ada@example.com</email><name>Ada</name><password>secret123</password>
        </registerUserRequest>
        """);
  }

  @Test
  void runtimeSchemaRejectsExcessiveLength() {
    assertSchemaClientFault(
        """
        <registerUserRequest xmlns="http://soap.com/english-reading/readings">
          <name>%s</name><email>long@example.com</email><password>secret123</password>
        </registerUserRequest>
        """
            .formatted("a".repeat(101)));
  }

  @Test
  void publicRegisterAndLoginRemainValidSoapOperations() {
    var email = "public-" + java.util.UUID.randomUUID() + "@example.com";
    var register =
        """
        <registerUserRequest xmlns="http://soap.com/english-reading/readings">
          <name>Ada</name><email>%s</email><password>secret123</password>
        </registerUserRequest>
        """
            .formatted(email);
    var login =
        """
        <loginRequest xmlns="http://soap.com/english-reading/readings">
          <email>%s</email><password>secret123</password>
        </loginRequest>
        """
            .formatted(email);
    var client = MockWebServiceClient.createClient(applicationContext);
    client.sendRequest(withPayload(source(register))).andExpect(noFault());
    client.sendRequest(withPayload(source(login))).andExpect(noFault());
  }

  @Test
  void saajRejectsDoctypeExternalEntitiesAndEntityExpansion() throws Exception {
    assertXmlParserRejects(
        "<!DOCTYPE x [<!ENTITY local SYSTEM \"file:///C:/Windows/win.ini\">]>"
            + soapEnvelope(
                "<loginRequest xmlns=\"http://soap.com/english-reading/readings\">"
                    + "<email>&local;</email><password>secret123</password></loginRequest>"));
    assertXmlParserRejects(
        "<!DOCTYPE x [<!ENTITY % remote SYSTEM \"https://127.0.0.1/entity.dtd\">%remote;]>"
            + soapEnvelope(
                "<loginRequest xmlns=\"http://soap.com/english-reading/readings\">"
                    + "<email>a@b.co</email><password>secret123</password></loginRequest>"));
    assertXmlParserRejects(
        "<!DOCTYPE x [<!ENTITY a \"1234567890\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">"
            + "<!ENTITY c \"&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;\">]>"
            + soapEnvelope(
                "<loginRequest xmlns=\"http://soap.com/english-reading/readings\">"
                    + "<email>&c;@x.co</email><password>secret123</password></loginRequest>"));
  }

  @Test
  void runtimeSchemaRejectsExcessiveXmlNesting() {
    assertThatThrownBy(
            () ->
                assertSchemaClientFault(
                    "<loginRequest xmlns=\"http://soap.com/english-reading/readings\">"
                        + "<email>a@b.co"
                        + "<nested>".repeat(200)
                        + "x"
                        + "</nested>".repeat(200)
                        + "</email><password>secret123</password></loginRequest>"))
        .hasMessageContaining("maxElementDepth");
  }

  private void assertSchemaClientFault(String payload) {
    MockWebServiceClient.createClient(applicationContext)
        .sendRequest(withPayload(source(payload)))
        .andExpect(clientOrSenderFault());
  }

  private StreamSource source(String payload) {
    return new StreamSource(new StringReader(payload));
  }

  private void assertXmlParserRejects(String payload) throws Exception {
    var factory = new SaajSoapMessageFactory();
    factory.afterPropertiesSet();
    assertThatThrownBy(
            () ->
                factory.createWebServiceMessage(
                    new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8))))
        .isNotNull();
  }

  private String soapEnvelope(String payload) {
    return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        + "<soapenv:Header/><soapenv:Body>"
        + payload
        + "</soapenv:Body></soapenv:Envelope>";
  }

  private String registerReadingEnvelope(String content) {
    return soapEnvelope(
        "<registerReadingRequest xmlns=\"http://soap.com/english-reading/readings\">"
            + "<title>HTTP E2E</title><content>"
            + content
            + "</content><language>en</language></registerReadingRequest>");
  }

  private HttpResponse<String> postSoap(String body, String token) throws Exception {
    return postSoap(body, token, null);
  }

  private HttpResponse<String> postSoap(String body, String token, String correlationId)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort + "/ws"))
            .header("Content-Type", "text/xml; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    if (correlationId != null) {
      request.header("X-Correlation-ID", correlationId);
    }
    return HttpClient.newHttpClient()
        .send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private HttpResponse<String> get(String path) throws Exception {
    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort + path))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private <T> ConcurrentResults<T> runConcurrently(
      ThrowingSupplier<T> first, ThrowingSupplier<T> second) throws Exception {
    var outcomes = runConcurrentlyCapturing(first, second);
    if (outcomes.get(0) instanceof Throwable throwable) {
      throw new AssertionError("First concurrent operation failed", throwable);
    }
    if (outcomes.get(1) instanceof Throwable throwable) {
      throw new AssertionError("Second concurrent operation failed", throwable);
    }
    @SuppressWarnings("unchecked")
    var firstResult = (T) outcomes.get(0);
    @SuppressWarnings("unchecked")
    var secondResult = (T) outcomes.get(1);
    return new ConcurrentResults<>(firstResult, secondResult);
  }

  private java.util.List<Object> runConcurrentlyCapturing(
      ThrowingSupplier<?> first, ThrowingSupplier<?> second) throws Exception {
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      java.util.concurrent.Callable<Object> firstTask = concurrentTask(first, ready, start);
      java.util.concurrent.Callable<Object> secondTask = concurrentTask(second, ready, start);
      var firstFuture = executor.submit(firstTask);
      var secondFuture = executor.submit(secondTask);
      assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      start.countDown();
      return java.util.List.of(firstFuture.get(), secondFuture.get());
    } finally {
      executor.shutdownNow();
    }
  }

  private java.util.concurrent.Callable<Object> concurrentTask(
      ThrowingSupplier<?> operation, CountDownLatch ready, CountDownLatch start) {
    return () -> {
      ready.countDown();
      start.await();
      try {
        return operation.get();
      } catch (Throwable throwable) {
        return throwable;
      }
    };
  }

  private record ConcurrentResults<T>(T first, T second) {}

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  @Test
  void invalidVocabularyStatusReturnsAClearClientFault() {
    authenticateUser();
    var payload =
        """
        <changeVocabularyStatusRequest xmlns="http://soap.com/english-reading/readings">
          <wordId>00000000-0000-0000-0000-000000000001</wordId>
          <status>MASTERED</status>
        </changeVocabularyStatusRequest>
        """;

    MockWebServiceClient.createClient(applicationContext)
        .sendRequest(withPayload(new StreamSource(new StringReader(payload))))
        .andExpect(clientOrSenderFault("Invalid vocabulary status: MASTERED"));
  }

  @Test
  void missingVocabularyStatusReturnsAClientFault() {
    authenticateUser();
    var payload =
        """
        <changeVocabularyStatusRequest xmlns="http://soap.com/english-reading/readings">
          <wordId>00000000-0000-0000-0000-000000000001</wordId>
        </changeVocabularyStatusRequest>
        """;

    MockWebServiceClient.createClient(applicationContext)
        .sendRequest(withPayload(new StreamSource(new StringReader(payload))))
        .andExpect(clientOrSenderFault("Vocabulary status must not be null"));
  }

  @Test
  void lookupWordWithoutJwtReturnsAClientFault() {
    SecurityContextHolder.clearContext();
    MockWebServiceClient.createClient(applicationContext)
        .sendRequest(withPayload(new StreamSource(new StringReader(lookupWordPayload()))))
        .andExpect(clientOrSenderFault("Authentication is required"));
  }

  @Test
  void lookupWordWithAValidJwtPrincipalReturnsTheSoapResponse() {
    var now = Instant.now();
    var jwt =
        new Jwt(
            "token",
            now,
            now.plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", user.id().toString()));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(jwt, jwt, List.of()));
    org.mockito.Mockito.when(dictionaryPort.lookup("bridge", "en"))
        .thenReturn(new DictionaryEntry("bridge", null, null, List.<WordMeaning>of()));
    org.mockito.Mockito.when(translationPort.translate("bridge", "en", "es")).thenReturn("puente");

    try {
      MockWebServiceClient.createClient(applicationContext)
          .sendRequest(withPayload(new StreamSource(new StringReader(lookupWordPayload()))))
          .andExpect(noFault())
          .andExpect(
              xpath("/*[local-name()='lookupWordResponse']/*[local-name()='translation']")
                  .evaluatesTo("puente"));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private String lookupWordPayload() {
    return """
        <lookupWordRequest xmlns="http://soap.com/english-reading/readings">
          <word>bridge</word>
        </lookupWordRequest>
        """;
  }

  private void authenticateUser() {
    var now = Instant.now();
    var jwt =
        new Jwt(
            "test-token",
            now,
            now.plusSeconds(60),
            Map.of("alg", "HS256"),
            Map.of("sub", user.id().toString()));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(jwt, jwt, List.of()));
  }

  @BeforeEach
  void createUser() {
    user =
        users.save(
            new User(
                null,
                "Ada Lovelace",
                "ada-" + java.util.UUID.randomUUID() + "@example.com",
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                null));
  }

  @Test
  @Transactional
  void persistsAndLoadsTheReadingAggregate() {
    var reading = readings.save(new Reading(null, user, "A short text", "Hello world", "en", null));

    assertThat(reading.id()).isNotNull();
    assertThat(reading.createdAt()).isNotNull();
    assertThat(readings.findById(reading.id())).contains(reading);
    assertThat(readings.findByUserId(user.id(), new PageRequest(0, 10)).content())
        .containsExactly(reading);
  }

  @Test
  @Transactional
  void treatsTheSameNormalizedValueInDifferentLanguagesAsDifferentWords() {
    var englishWord = words.save(new Word(null, "chat", "en"));
    var frenchWord = words.save(new Word(null, "chat", "fr"));

    assertThat(englishWord.id()).isNotEqualTo(frenchWord.id());
    assertThat(words.findByNormalizedValueAndLanguage("chat", "en")).contains(englishWord);
    assertThat(words.findByNormalizedValueAndLanguage("chat", "fr")).contains(frenchWord);
  }

  @Test
  @Transactional
  void loadsVocabularyWithItsLazyRelationships() {
    var word = words.save(new Word(null, "hello", "en"));
    var firstSeenAt = LocalDateTime.now();
    var saved =
        vocabulary.save(
            new UserVocabulary(null, user, word, VocabularyStatus.LEARNING, firstSeenAt, null));

    assertThat(vocabulary.findByUserIdAndWordId(user.id(), word.id())).contains(saved);
    assertThat(vocabulary.findByUserId(user.id(), new PageRequest(0, 10)).content())
        .containsExactly(saved);
  }

  @Test
  @Transactional
  void findsKnownWordsInOneLanguageFromABatchOfCandidates() {
    var known = words.save(new Word(null, "hello", "en"));
    var learning = words.save(new Word(null, "world", "en"));
    var french = words.save(new Word(null, "bonjour", "fr"));
    var now = LocalDateTime.now();
    vocabulary.save(new UserVocabulary(null, user, known, VocabularyStatus.KNOWN, now, now));
    vocabulary.save(new UserVocabulary(null, user, learning, VocabularyStatus.LEARNING, now, null));
    vocabulary.save(new UserVocabulary(null, user, french, VocabularyStatus.KNOWN, now, now));

    assertThat(
            vocabulary.findStatusesByNormalizedValues(
                user.id(), "en", java.util.Set.of("hello", "world", "bonjour", "missing")))
        .containsExactlyInAnyOrderEntriesOf(
            java.util.Map.of("hello", VocabularyStatus.KNOWN, "world", VocabularyStatus.LEARNING));
  }
}
