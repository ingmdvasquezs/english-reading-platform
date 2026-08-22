package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

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
import com.soap.soap.infrastructure.soap.resolver.SoapExceptionResolver;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.xml.transform.stream.StreamSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers(disabledWithoutDocker = true)
class SoapApplicationTests {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private UserRepositoryPort users;
  @Autowired private WordRepositoryPort words;
  @Autowired private ReadingRepositoryPort readings;
  @Autowired private UserVocabularyRepositoryPort vocabulary;
  @Autowired private ApplicationContext applicationContext;
  @Autowired private SoapExceptionResolver soapExceptionResolver;
  @MockitoBean private DictionaryPort dictionaryPort;
  @MockitoBean private TranslationPort translationPort;

  private User user;

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
                messageContext, null, new RuntimeException("unexpected")))
        .isTrue();
    var response = (SoapMessage) messageContext.getResponse();

    assertThat(response.getSoapBody().getFault().getFaultCode())
        .isEqualTo(SoapVersion.SOAP_11.getServerOrReceiverFaultName());
  }

  @Test
  void invalidVocabularyStatusReturnsAClearClientFault() {
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
