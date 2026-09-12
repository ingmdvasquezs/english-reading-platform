package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

import com.soap.soap.application.exception.ConcurrentVocabularyModificationException;
import com.soap.soap.application.exception.EmailAlreadyRegisteredException;
import com.soap.soap.application.exception.ExternalProviderException;
import com.soap.soap.application.exception.WordAlreadyInVocabularyException;
import com.soap.soap.application.model.DictionaryEntry;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PlatformReadingSummary;
import com.soap.soap.application.model.ReadingSummary;
import com.soap.soap.application.model.WordMeaning;
import com.soap.soap.application.port.in.RecommendPlatformReadingsPort;
import com.soap.soap.application.port.out.DictionaryPort;
import com.soap.soap.application.port.out.InitialVocabularyTestSourcePort;
import com.soap.soap.application.port.out.ReadingCollectionRepositoryPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.TranslationPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.WordRepositoryPort;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
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
import java.util.UUID;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
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
@Testcontainers
@ActiveProfiles("local")
class SoapApplicationTests {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private UserRepositoryPort users;
  @Autowired private WordRepositoryPort words;
  @Autowired private JpaWordRepository jpaWords;
  @Autowired private ReadingRepositoryPort readings;
  @Autowired private ReadingProgressRepositoryPort readingProgress;
  @Autowired private ReadingCollectionRepositoryPort collections;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private RecommendPlatformReadingsPort recommendPlatformReadings;
  @Autowired private InitialVocabularyTestSourcePort initialVocabularyTestSource;
  @Autowired private UserVocabularyRepositoryPort vocabulary;
  @Autowired private ApplicationContext applicationContext;
  @Autowired private SoapExceptionResolver soapExceptionResolver;
  @Autowired private MeterRegistry meterRegistry;
  @LocalServerPort private int serverPort;
  @MockitoBean private DictionaryPort dictionaryPort;
  @MockitoBean private TranslationPort translationPort;

  private User user;

  @Test
  @Transactional
  void editorialCollectionMigrationSeedsTheAuditedMembershipsWithoutDuplicates() {
    var active = collections.findAllActive();

    assertThat(active)
        .hasSize(6)
        .extracting(com.soap.soap.domain.model.ReadingCollection::key)
        .containsExactly(
            "everyday-life-human-connections",
            "mysteries-imagination",
            "science-technology-ideas",
            "nature-environment",
            "travel-places-memory",
            "culture-work-society");
    assertThat(active)
        .extracting(com.soap.soap.domain.model.ReadingCollection::displayOrder)
        .containsExactly(1, 2, 3, 4, 5, 6);
    assertThat(active)
        .allMatch(com.soap.soap.domain.model.ReadingCollection::active)
        .allMatch(collection -> collection.coverKey() == null);
    assertThat(
            jdbcTemplate.queryForList(
                """
                select c.key, count(rc.reading_id) as membership_count
                from collections c join reading_collections rc on rc.collection_id = c.id
                group by c.key order by min(c.display_order)
                """))
        .extracting(row -> ((Number) row.get("membership_count")).longValue())
        .containsExactly(11L, 14L, 20L, 18L, 19L, 33L);
    assertThat(jdbcTemplate.queryForObject("select count(*) from reading_collections", Long.class))
        .isEqualTo(115L);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*) from (
                  select collection_id, reading_id from reading_collections
                  group by collection_id, reading_id having count(*) > 1
                ) duplicates
                """,
                Long.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*) from reading_collections rc
                join readings r on r.id = rc.reading_id
                where r.title = 'An Island Made of Fog'
                """,
                Long.class))
        .isEqualTo(3L);

    jdbcTemplate.update(
        "update collections set active = false where key = ?", "mysteries-imagination");
    assertThat(collections.findAllActive())
        .hasSize(5)
        .extracting(com.soap.soap.domain.model.ReadingCollection::key)
        .doesNotContain("mysteries-imagination");
  }

  @Test
  void collectionSoapOperationsRequireAuthenticationAndPageInEditorialOrder() {
    var client = MockWebServiceClient.createClient(applicationContext);
    client
        .sendRequest(
            withPayload(
                source(
                    """
                    <listCollectionsRequest xmlns="http://soap.com/english-reading/readings"/>
                    """)))
        .andExpect(clientOrSenderFault());

    authenticateUser();
    try {
      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <listCollectionsRequest xmlns="http://soap.com/english-reading/readings"/>
                      """)))
          .andExpect(noFault())
          .andExpect(xpath("count(//*[local-name()='collections'])").evaluatesTo("6"))
          .andExpect(
              xpath("(//*[local-name()='collections'])[1]/*[local-name()='displayOrder']")
                  .evaluatesTo("1"));
      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <listCollectionReadingsRequest xmlns="http://soap.com/english-reading/readings">
                        <collectionKey>everyday-life-human-connections</collectionKey>
                        <page>0</page><size>2</size>
                      </listCollectionReadingsRequest>
                      """)))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='page']").evaluatesTo("0"))
          .andExpect(xpath("//*[local-name()='size']").evaluatesTo("2"))
          .andExpect(xpath("//*[local-name()='totalElements']").evaluatesTo("11"))
          .andExpect(xpath("count(//*[local-name()='readings'])").evaluatesTo("2"))
          .andExpect(
              xpath("(//*[local-name()='readings'])[1]/*[local-name()='uniqueWords']").exists())
          .andExpect(
              xpath("(//*[local-name()='readings'])[1]/*[local-name()='knownWords']").exists())
          .andExpect(
              xpath("(//*[local-name()='readings'])[1]/*[local-name()='learningWords']").exists())
          .andExpect(
              xpath("(//*[local-name()='readings'])[1]/*[local-name()='explicitNewWords']")
                  .exists())
          .andExpect(
              xpath("(//*[local-name()='readings'])[1]/*[local-name()='ignoredWords']").exists())
          .andExpect(
              xpath("(//*[local-name()='readings'])[1]/*[local-name()='unclassifiedWords']")
                  .exists())
          .andExpect(
              xpath("(//*[local-name()='readings'])[1]/*[local-name()='vocabularyFitPercentage']")
                  .exists())
          .andExpect(
              xpath(
                      "(//*[local-name()='readings'])[1]/*[local-name()='classificationConfidencePercentage']")
                  .exists())
          .andExpect(
              xpath(
                      "//*[local-name()='readings'][*[local-name()='readingId']='10000000-0000-0000-0000-000000000001']/*[local-name()='coverKey']")
                  .evaluatesTo("a-morning-at-the-library"));
      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <listCollectionReadingsRequest xmlns="http://soap.com/english-reading/readings">
                        <collectionKey>does-not-exist</collectionKey><page>0</page><size>10</size>
                      </listCollectionReadingsRequest>
                      """)))
          .andExpect(clientOrSenderFault());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  @Transactional
  void pedagogicalRecommendationsReinforceLearningWithoutExcessiveChallenge() {
    var now = LocalDateTime.now();
    for (var existing : readings.findAllPlatformReadings()) {
      readingProgress.complete(user.id(), existing.id(), now);
    }
    var knownWords =
        List.of("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota");
    for (var value : knownWords) {
      var word = words.save(new Word(null, value, "ped"));
      vocabulary.save(new UserVocabulary(null, user, word, VocabularyStatus.KNOWN, now, now));
    }
    for (var value : List.of("learnone", "learntwo")) {
      var word = words.save(new Word(null, value, "ped"));
      vocabulary.save(new UserVocabulary(null, user, word, VocabularyStatus.LEARNING, now, null));
    }
    var explicitNew = words.save(new Word(null, "novel", "ped"));
    vocabulary.save(new UserVocabulary(null, user, explicitNew, VocabularyStatus.NEW, now, null));

    var ideal =
        readings.save(
            new Reading(
                null,
                null,
                "Contextual reinforcement",
                "alpha beta gamma delta epsilon zeta learnone learntwo novel uncertain",
                "ped",
                now,
                ReadingOrigin.PLATFORM,
                com.soap.soap.domain.model.EditorialLevel.A2,
                "Pedagogy"));
    readings.save(
        new Reading(
            null,
            null,
            "Too easy",
            "alpha beta gamma delta epsilon zeta eta theta iota uncertain",
            "ped",
            now.plusSeconds(1),
            ReadingOrigin.PLATFORM,
            com.soap.soap.domain.model.EditorialLevel.B1,
            "Pedagogy"));
    readings.save(
        new Reading(
            null,
            null,
            "Too difficult",
            "alpha beta gamma delta learnone unknownone unknowntwo unknownthree unknownfour unknownfive",
            "ped",
            now.plusSeconds(2),
            ReadingOrigin.PLATFORM,
            com.soap.soap.domain.model.EditorialLevel.B2,
            "Pedagogy"));
    authenticateUser();
    try {
      var result = recommendPlatformReadings.recommendPlatformReadings(new PageRequest(0, 3));

      assertThat(result.content().getFirst().readingId()).isEqualTo(ideal.id());
      assertThat(result.content().getFirst().learningWords()).isEqualTo(2);
      assertThat(result.content().getFirst().explicitNewWords()).isEqualTo(1);
      assertThat(result.content().getFirst().unclassifiedWords()).isEqualTo(1);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void readingProgressFlowIsPersistentIdempotentAndIndependentFromVocabulary() {
    var reading =
        readings.save(new Reading(null, user, "Progress flow", "Work remains here", "en", null));
    authenticateUser();
    var client = MockWebServiceClient.createClient(applicationContext);
    try {
      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <getReadingReaderDataRequest xmlns="http://soap.com/english-reading/readings">
                        <readingId>%s</readingId>
                      </getReadingReaderDataRequest>
                      """
                          .formatted(reading.id()))))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='progressStatus']").evaluatesTo("IN_PROGRESS"));

      var started = readingProgress.findByUserIdAndReadingId(user.id(), reading.id()).orElseThrow();
      assertThat(started.status().name()).isEqualTo("IN_PROGRESS");
      assertThat(started.completedAt()).isNull();
      assertThat(started.currentPartOrdinal()).isNull();
      assertThat(started.paginationVersion()).isNull();

      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <updateReadingProgressRequest xmlns="http://soap.com/english-reading/readings">
                        <readingId>%s</readingId>
                        <progressStatus>IN_PROGRESS</progressStatus>
                        <currentPartOrdinal>8</currentPartOrdinal>
                        <paginationVersion>1</paginationVersion>
                      </updateReadingProgressRequest>
                      """
                          .formatted(reading.id()))))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='currentPartOrdinal']").evaluatesTo("8"))
          .andExpect(xpath("//*[local-name()='paginationVersion']").evaluatesTo("1"));

      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <updateReadingProgressRequest xmlns="http://soap.com/english-reading/readings">
                        <readingId>%s</readingId>
                        <progressStatus>IN_PROGRESS</progressStatus>
                      </updateReadingProgressRequest>
                      """
                          .formatted(reading.id()))))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='currentPartOrdinal']").evaluatesTo("8"));

      setVocabularyStatus(client, "work", "LEARNING");

      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <completeReadingRequest xmlns="http://soap.com/english-reading/readings">
                        <readingId>%s</readingId>
                      </completeReadingRequest>
                      """
                          .formatted(reading.id()))))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='status']").evaluatesTo("COMPLETED"));
      var completed =
          readingProgress.findByUserIdAndReadingId(user.id(), reading.id()).orElseThrow();

      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <completeReadingRequest xmlns="http://soap.com/english-reading/readings">
                        <readingId>%s</readingId>
                      </completeReadingRequest>
                      """
                          .formatted(reading.id()))))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='status']").evaluatesTo("COMPLETED"));
      var completedAgain =
          readingProgress.findByUserIdAndReadingId(user.id(), reading.id()).orElseThrow();

      assertThat(completed.startedAt()).isEqualTo(started.startedAt());
      assertThat(completedAgain.completedAt()).isEqualTo(completed.completedAt());
      assertThat(completed.currentPartOrdinal()).isEqualTo(8);
      assertThat(completed.paginationVersion()).isEqualTo(1);
      assertThat(
              vocabulary.findStatusesByNormalizedValues(
                  user.id(), "en", java.util.Set.of("work", "remains")))
          .containsEntry("work", VocabularyStatus.LEARNING)
          .doesNotContainKey("remains");

      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <getReadingReaderDataRequest xmlns="http://soap.com/english-reading/readings">
                        <readingId>%s</readingId>
                      </getReadingReaderDataRequest>
                      """
                          .formatted(reading.id()))))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='progressStatus']").evaluatesTo("COMPLETED"))
          .andExpect(xpath("//*[local-name()='currentPartOrdinal']").evaluatesTo("8"))
          .andExpect(xpath("//*[local-name()='paginationVersion']").evaluatesTo("1"));
      assertThat(
              readingProgress
                  .findByUserIdAndReadingId(user.id(), reading.id())
                  .orElseThrow()
                  .completedAt())
          .isEqualTo(completed.completedAt());

      var directlyCompleted =
          readings.save(
              new Reading(null, user, "Direct completion", "Still unclassified", "en", null));
      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <completeReadingRequest xmlns="http://soap.com/english-reading/readings">
                        <readingId>%s</readingId>
                      </completeReadingRequest>
                      """
                          .formatted(directlyCompleted.id()))))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='status']").evaluatesTo("COMPLETED"));
      var directProgress =
          readingProgress.findByUserIdAndReadingId(user.id(), directlyCompleted.id()).orElseThrow();
      assertThat(directProgress.startedAt()).isEqualTo(directProgress.completedAt());
      assertThat(
              vocabulary.findStatusesByNormalizedValues(
                  user.id(), "en", java.util.Set.of("still", "unclassified")))
          .isEmpty();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void readingProgressPartPairIsValidatedBySoapAndDatabase() {
    var reading = readings.save(new Reading(null, user, "Pair validation", "Text", "en", null));
    authenticateUser();
    var client = MockWebServiceClient.createClient(applicationContext);
    try {
      client
          .sendRequest(
              withPayload(
                  source(
                      """
              <updateReadingProgressRequest xmlns="http://soap.com/english-reading/readings">
                <readingId>%s</readingId><progressStatus>IN_PROGRESS</progressStatus>
                <currentPartOrdinal>1</currentPartOrdinal>
              </updateReadingProgressRequest>
              """
                          .formatted(reading.id()))))
          .andExpect(clientOrSenderFault());
      assertThat(readingProgress.findByUserIdAndReadingId(user.id(), reading.id())).isEmpty();

      client
          .sendRequest(
              withPayload(
                  source(
                      """
              <updateReadingProgressRequest xmlns="http://soap.com/english-reading/readings">
                <readingId>%s</readingId><progressStatus>IN_PROGRESS</progressStatus>
                <currentPartOrdinal>0</currentPartOrdinal><paginationVersion>1</paginationVersion>
              </updateReadingProgressRequest>
              """
                          .formatted(reading.id()))))
          .andExpect(clientOrSenderFault());
      assertThat(readingProgress.findByUserIdAndReadingId(user.id(), reading.id())).isEmpty();
    } finally {
      SecurityContextHolder.clearContext();
    }

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_name='reading_progress' and column_name in ('current_part_ordinal','pagination_version')",
                Integer.class))
        .isEqualTo(2);
  }

  @Test
  @Transactional
  void continueReadingCombinesOwnedAndPlatformProgressInStartedOrderWithPagination() {
    var now = LocalDateTime.now();
    var owned =
        readings.save(new Reading(null, user, "Owned in progress", "Owned text", "en", now));
    var platform =
        readings.save(
            new Reading(
                null,
                null,
                "Platform in progress",
                "Platform text",
                "en",
                now,
                ReadingOrigin.PLATFORM,
                EditorialLevel.B1,
                "Science",
                "continue-cover"));
    var completed =
        readings.save(new Reading(null, user, "Completed", "Completed text", "en", now));
    readings.save(new Reading(null, user, "Not started", "Not started text", "en", now));
    var otherUser =
        users.save(
            new User(
                null,
                "Other",
                "continue-" + UUID.randomUUID() + "@example.com",
                "{bcrypt}$2a$10$invalidlegacycredentialinvalidlegacycredentialinv",
                null));
    var otherReading =
        readings.save(new Reading(null, otherUser, "Other user's reading", "Private", "en", now));
    readingProgress.startIfAbsent(user.id(), owned.id(), now.minusMinutes(2));
    readingProgress.startIfAbsent(user.id(), platform.id(), now.minusMinutes(1));
    readingProgress.complete(user.id(), completed.id(), now);
    readingProgress.startIfAbsent(otherUser.id(), otherReading.id(), now.plusMinutes(1));

    var direct = readingProgress.findInProgressReadings(user.id(), new PageRequest(0, 10));
    assertThat(direct.content())
        .extracting(com.soap.soap.application.model.ContinueReadingItem::readingId)
        .containsExactly(platform.id(), owned.id())
        .doesNotHaveDuplicates();
    assertThat(direct.totalElements()).isEqualTo(2);

    var client = MockWebServiceClient.createClient(applicationContext);
    client
        .sendRequest(
            withPayload(
                source(
                    """
                    <listContinueReadingRequest xmlns="http://soap.com/english-reading/readings">
                      <page>0</page><size>1</size>
                    </listContinueReadingRequest>
                    """)))
        .andExpect(clientOrSenderFault());

    authenticateUser();
    try {
      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <listContinueReadingRequest xmlns="http://soap.com/english-reading/readings">
                        <page>0</page><size>1</size>
                      </listContinueReadingRequest>
                      """)))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='totalElements']").evaluatesTo("2"))
          .andExpect(xpath("count(//*[local-name()='readings'])").evaluatesTo("1"))
          .andExpect(
              xpath("//*[local-name()='readings']/*[local-name()='readingId']")
                  .evaluatesTo(platform.id().toString()))
          .andExpect(xpath("//*[local-name()='origin']").evaluatesTo("PLATFORM"))
          .andExpect(xpath("//*[local-name()='progressStatus']").evaluatesTo("IN_PROGRESS"))
          .andExpect(xpath("//*[local-name()='coverKey']").evaluatesTo("continue-cover"))
          .andExpect(xpath("//*[local-name()='editorialLevel']").evaluatesTo("B1"))
          .andExpect(xpath("//*[local-name()='category']").evaluatesTo("Science"));
      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <listContinueReadingRequest xmlns="http://soap.com/english-reading/readings">
                        <page>1</page><size>1</size>
                      </listContinueReadingRequest>
                      """)))
          .andExpect(noFault())
          .andExpect(
              xpath("//*[local-name()='readings']/*[local-name()='readingId']")
                  .evaluatesTo(owned.id().toString()))
          .andExpect(xpath("//*[local-name()='origin']").evaluatesTo("USER"))
          .andExpect(xpath("//*[local-name()='coverKey']").doesNotExist())
          .andExpect(xpath("//*[local-name()='editorialLevel']").doesNotExist())
          .andExpect(xpath("//*[local-name()='category']").doesNotExist());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void registerLoginCompleteAndLoginAgainPersistsOnboardingForOnlyTheAuthenticatedUser()
      throws Exception {
    var email = "onboarding-flow-" + java.util.UUID.randomUUID() + "@example.com";
    var otherEmail = "onboarding-other-" + java.util.UUID.randomUUID() + "@example.com";
    var other = users.save(new User(null, "Other", otherEmail, "hash", null));
    var register =
        soapEnvelope(
            "<registerUserRequest xmlns=\"http://soap.com/english-reading/readings\">"
                + "<name>Onboarding User</name><email>"
                + email
                + "</email><password>secret123</password></registerUserRequest>");
    assertThat(postSoap(register, null).statusCode()).isEqualTo(200);

    var before = loginOverHttp(email, "secret123");
    assertThat(before.onboardingCompleted()).isFalse();

    var emptyCompletion =
        soapEnvelope(
            "<completeInitialVocabularyTestRequest xmlns=\"http://soap.com/english-reading/readings\">"
                + "<testId>"
                + initialVocabularyTestSource.load().testId()
                + "</testId></completeInitialVocabularyTestRequest>");
    assertThat(postSoap(emptyCompletion, before.accessToken()).statusCode()).isEqualTo(500);
    assertThat(loginOverHttp(email, "secret123").onboardingCompleted()).isFalse();
    assertThat(
            vocabulary
                .findByUserId(users.findByEmail(email).orElseThrow().id(), new PageRequest(0, 10))
                .content())
        .isEmpty();

    var invalidCompletion =
        soapEnvelope(
            "<completeInitialVocabularyTestRequest xmlns=\"http://soap.com/english-reading/readings\">"
                + "<testId>"
                + initialVocabularyTestSource.load().testId()
                + "</testId>"
                + classificationsXml(
                    List.of(
                        "work", "elena", "autumn", "market", "morning", "bread", "bakery", "train",
                        "library"),
                    "NEW")
                + "<classifications><word>not-in-the-test</word><status>NEW</status>"
                + "</classifications>"
                + "</completeInitialVocabularyTestRequest>");
    assertThat(postSoap(invalidCompletion, before.accessToken()).statusCode()).isEqualTo(500);
    assertThat(loginOverHttp(email, "secret123").onboardingCompleted()).isFalse();

    var completion =
        soapEnvelope(
            "<completeInitialVocabularyTestRequest xmlns=\"http://soap.com/english-reading/readings\">"
                + "<testId>"
                + initialVocabularyTestSource.load().testId()
                + "</testId>"
                + "<classifications><word>work</word><status>LEARNING</status></classifications>"
                + "<classifications><word>Elena</word><status>NEW</status></classifications>"
                + "<classifications><word>autumn</word><status>KNOWN</status></classifications>"
                + "<classifications><word>market</word><status>IGNORED</status></classifications>"
                + classificationsXml(
                    List.of("morning", "bread", "bakery", "train", "library", "learning"), "NEW")
                + "</completeInitialVocabularyTestRequest>");
    assertThat(postSoap(completion, before.accessToken()).statusCode()).isEqualTo(200);

    assertThat(loginOverHttp(email, "secret123").onboardingCompleted()).isTrue();
    var completedUser = users.findByEmail(email).orElseThrow();
    assertThat(
            vocabulary.findStatusesByNormalizedValues(
                completedUser.id(),
                "en",
                java.util.Set.of("work", "elena", "autumn", "market", "morning")))
        .containsExactlyInAnyOrderEntriesOf(
            java.util.Map.of(
                "work", VocabularyStatus.LEARNING,
                "elena", VocabularyStatus.NEW,
                "autumn", VocabularyStatus.KNOWN,
                "market", VocabularyStatus.IGNORED,
                "morning", VocabularyStatus.NEW));
    assertThat(users.findById(other.id()).orElseThrow().onboardingCompleted()).isFalse();
    assertThat(postSoap(completion, before.accessToken()).statusCode()).isEqualTo(500);
  }

  @Test
  void migrationSeedsTheActiveOnboardingReadingAndSoapReturnsItsPersistedContent() {
    var persistedTest = initialVocabularyTestSource.load();
    assertThat(persistedTest.testId()).isEqualTo("onboarding-reading-3-v3");
    assertThat(persistedTest.text())
        .startsWith("Elena arrived in the quiet harbor town on a rainy morning in early autumn")
        .contains("\n\nWithin a month")
        .endsWith("another opportunity to learn, observe, and belong.");

    authenticateUser();
    try {
      MockWebServiceClient.createClient(applicationContext)
          .sendRequest(
              withPayload(
                  source(
                      """
                      <getInitialVocabularyTestRequest xmlns="http://soap.com/english-reading/readings"/>
                      """)))
          .andExpect(noFault())
          .andExpect(
              xpath("/*[local-name()='getInitialVocabularyTestResponse']/*[local-name()='testId']")
                  .evaluatesTo(persistedTest.testId()))
          .andExpect(
              xpath("/*[local-name()='getInitialVocabularyTestResponse']/*[local-name()='text']")
                  .evaluatesTo(persistedTest.text()));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

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

  private LoginHttpResult loginOverHttp(String email, String password) throws Exception {
    var login =
        soapEnvelope(
            "<loginRequest xmlns=\"http://soap.com/english-reading/readings\"><email>"
                + email
                + "</email><password>"
                + password
                + "</password></loginRequest>");
    var response = postSoap(login, null);
    assertThat(response.statusCode()).isEqualTo(200);
    var tokenMatcher =
        java.util.regex.Pattern.compile("<(?:\\w+:)?accessToken>([^<]+)</(?:\\w+:)?accessToken>")
            .matcher(response.body());
    var onboardingMatcher =
        java.util.regex.Pattern.compile(
                "<(?:\\w+:)?onboardingCompleted>(true|false)</(?:\\w+:)?onboardingCompleted>")
            .matcher(response.body());
    assertThat(tokenMatcher.find()).isTrue();
    assertThat(onboardingMatcher.find()).isTrue();
    return new LoginHttpResult(
        tokenMatcher.group(1), Boolean.parseBoolean(onboardingMatcher.group(1)));
  }

  private record LoginHttpResult(String accessToken, boolean onboardingCompleted) {}

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
  void listUserVocabularySupportsLegacyRequestAndReturnsSummary() {
    authenticateUser();
    var client = MockWebServiceClient.createClient(applicationContext);
    try {
      setVocabularyStatus(client, "journey", "LEARNING");
      setVocabularyStatus(client, "book", "KNOWN");

      var legacyPayload =
          """
          <listUserVocabularyRequest xmlns="http://soap.com/english-reading/readings">
            <page>0</page>
            <size>10</size>
          </listUserVocabularyRequest>
          """;

      client
          .sendRequest(withPayload(source(legacyPayload)))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='page']").evaluatesTo("0"))
          .andExpect(xpath("//*[local-name()='size']").evaluatesTo("10"))
          .andExpect(xpath("//*[local-name()='totalElements']").evaluatesTo("2"))
          .andExpect(
              xpath("//*[local-name()='summary']/*[local-name()='totalCount']").evaluatesTo("2"))
          .andExpect(
              xpath("//*[local-name()='summary']/*[local-name()='learningCount']").evaluatesTo("1"))
          .andExpect(
              xpath("//*[local-name()='summary']/*[local-name()='knownCount']").evaluatesTo("1"))
          .andExpect(
              xpath("//*[local-name()='summary']/*[local-name()='newCount']").evaluatesTo("0"))
          .andExpect(
              xpath("//*[local-name()='summary']/*[local-name()='ignoredCount']").evaluatesTo("0"))
          .andExpect(xpath("count(//*[local-name()='entries'])").evaluatesTo("2"));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void listUserVocabularyFiltersByStatusWhilePreservingGlobalSummary() {
    authenticateUser();
    var client = MockWebServiceClient.createClient(applicationContext);
    try {
      setVocabularyStatus(client, "filterjourney", "LEARNING");
      setVocabularyStatus(client, "filterbook", "KNOWN");

      var filterPayload =
          """
          <listUserVocabularyRequest xmlns="http://soap.com/english-reading/readings">
            <page>0</page>
            <size>10</size>
            <status>LEARNING</status>
          </listUserVocabularyRequest>
          """;

      client
          .sendRequest(withPayload(source(filterPayload)))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='totalElements']").evaluatesTo("1"))
          .andExpect(xpath("count(//*[local-name()='entries'])").evaluatesTo("1"))
          .andExpect(
              xpath("//*[local-name()='entries'][1]/*[local-name()='word']")
                  .evaluatesTo("filterjourney"))
          .andExpect(
              xpath("//*[local-name()='entries'][1]/*[local-name()='status']")
                  .evaluatesTo("LEARNING"))
          .andExpect(
              xpath("//*[local-name()='summary']/*[local-name()='totalCount']").evaluatesTo("2"));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void listUserVocabularySearchesByPrefixCaseInsensitively() {
    authenticateUser();
    var client = MockWebServiceClient.createClient(applicationContext);
    try {
      setVocabularyStatus(client, "prefixone", "LEARNING");
      setVocabularyStatus(client, "prefixtwo", "KNOWN");
      setVocabularyStatus(client, "otherword", "NEW");

      var searchPayload =
          """
          <listUserVocabularyRequest xmlns="http://soap.com/english-reading/readings">
            <page>0</page>
            <size>10</size>
            <search>  Prefix  </search>
          </listUserVocabularyRequest>
          """;

      client
          .sendRequest(withPayload(source(searchPayload)))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='totalElements']").evaluatesTo("2"))
          .andExpect(xpath("count(//*[local-name()='entries'])").evaluatesTo("2"))
          .andExpect(
              xpath("//*[local-name()='summary']/*[local-name()='totalCount']").evaluatesTo("3"));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void listUserVocabularyCombinesStatusAndSearchCriteria() {
    authenticateUser();
    var client = MockWebServiceClient.createClient(applicationContext);
    try {
      setVocabularyStatus(client, "combinearrival", "LEARNING");
      setVocabularyStatus(client, "combineapply", "KNOWN");

      var matchPayload =
          """
          <listUserVocabularyRequest xmlns="http://soap.com/english-reading/readings">
            <page>0</page>
            <size>10</size>
            <status>LEARNING</status>
            <search>combine</search>
          </listUserVocabularyRequest>
          """;

      client
          .sendRequest(withPayload(source(matchPayload)))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='totalElements']").evaluatesTo("1"))
          .andExpect(
              xpath("//*[local-name()='entries'][1]/*[local-name()='word']")
                  .evaluatesTo("combinearrival"));

      var noMatchPayload =
          """
          <listUserVocabularyRequest xmlns="http://soap.com/english-reading/readings">
            <page>0</page>
            <size>10</size>
            <status>NEW</status>
            <search>combine</search>
          </listUserVocabularyRequest>
          """;

      client
          .sendRequest(withPayload(source(noMatchPayload)))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='totalElements']").evaluatesTo("0"))
          .andExpect(xpath("count(//*[local-name()='entries'])").evaluatesTo("0"))
          .andExpect(
              xpath("//*[local-name()='summary']/*[local-name()='totalCount']").evaluatesTo("2"));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void listUserVocabularyRejectsInvalidStatusWithClientFault() {
    authenticateUser();
    var client = MockWebServiceClient.createClient(applicationContext);
    try {
      var invalidStatusPayload =
          """
          <listUserVocabularyRequest xmlns="http://soap.com/english-reading/readings">
            <page>0</page>
            <size>10</size>
            <status>MASTERED</status>
          </listUserVocabularyRequest>
          """;

      client
          .sendRequest(withPayload(source(invalidStatusPayload)))
          .andExpect(clientOrSenderFault("Invalid vocabulary status: MASTERED"));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void setVocabularyStatusReflectsImmediatelyInListUserVocabulary() {
    authenticateUser();
    var client = MockWebServiceClient.createClient(applicationContext);
    try {
      setVocabularyStatus(client, "mutableword", "NEW");

      var listPayload =
          """
          <listUserVocabularyRequest xmlns="http://soap.com/english-reading/readings">
            <page>0</page>
            <size>10</size>
            <search>mutableword</search>
          </listUserVocabularyRequest>
          """;

      client
          .sendRequest(withPayload(source(listPayload)))
          .andExpect(noFault())
          .andExpect(
              xpath("//*[local-name()='entries'][1]/*[local-name()='status']").evaluatesTo("NEW"));

      setVocabularyStatus(client, "mutableword", "LEARNING");

      client
          .sendRequest(withPayload(source(listPayload)))
          .andExpect(noFault())
          .andExpect(
              xpath("//*[local-name()='entries'][1]/*[local-name()='status']")
                  .evaluatesTo("LEARNING"));
    } finally {
      SecurityContextHolder.clearContext();
    }
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
  void runtimeSchemaRejectsOversizedLoginCredentialsAsClientFaults() {
    assertSchemaClientFault(
        """
        <loginRequest xmlns="http://soap.com/english-reading/readings">
          <email>%s</email><password>secret123</password>
        </loginRequest>
        """
            .formatted("a".repeat(255)));
    assertSchemaClientFault(
        """
        <loginRequest xmlns="http://soap.com/english-reading/readings">
          <email>ada@example.com</email><password>%s</password>
        </loginRequest>
        """
            .formatted("x".repeat(129)));
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

  private String classificationsXml(List<String> values, String status) {
    return values.stream()
        .map(
            value ->
                "<classifications><word>"
                    + value
                    + "</word><status>"
                    + status
                    + "</status></classifications>")
        .collect(java.util.stream.Collectors.joining());
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
    org.mockito.Mockito.when(translationPort.translateBatch(List.of("bridge"), "en", "es"))
        .thenReturn(List.of("puente"));

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

  @Test
  void lookupWordWouldWithEmptyTranslationReturnsSuccessWithoutServerFault() {
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
    org.mockito.Mockito.when(dictionaryPort.lookup("would", "en"))
        .thenReturn(new DictionaryEntry("would", "/wʊd/", "audio-url", List.<WordMeaning>of()));
    org.mockito.Mockito.when(translationPort.translateBatch(List.of("would"), "en", "es"))
        .thenReturn(List.of(""));

    try {
      MockWebServiceClient.createClient(applicationContext)
          .sendRequest(
              withPayload(
                  new StreamSource(
                      new StringReader(
                          """
                          <lookupWordRequest xmlns="http://soap.com/english-reading/readings">
                            <word>would</word>
                          </lookupWordRequest>
                          """))))
          .andExpect(noFault())
          .andExpect(
              xpath("/*[local-name()='lookupWordResponse']/*[local-name()='word']")
                  .evaluatesTo("would"))
          .andExpect(
              xpath("/*[local-name()='lookupWordResponse']/*[local-name()='phonetic']")
                  .evaluatesTo("/wʊd/"));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void prepareAndRecordVocabularyReviewEndToEndOverSoap() {
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

    var word = words.resolve("soapreviewword", "en");
    var nowUtc = LocalDateTime.now();
    vocabulary.save(
        new UserVocabulary(
            null,
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(3),
            null,
            0L,
            0,
            null,
            nowUtc.minusHours(1)));

    try {
      var client = MockWebServiceClient.createClient(applicationContext);

      // 1. Prepare review
      client
          .sendRequest(
              withPayload(
                  new StreamSource(
                      new StringReader(
                          """
                          <prepareVocabularyReviewRequest xmlns="http://soap.com/english-reading/readings">
                            <size>10</size>
                          </prepareVocabularyReviewRequest>
                          """))))
          .andExpect(noFault())
          .andExpect(
              xpath("/*[local-name()='prepareVocabularyReviewResponse']/*[local-name()='dueCount']")
                  .evaluatesTo("1"))
          .andExpect(
              xpath(
                      "/*[local-name()='prepareVocabularyReviewResponse']/*[local-name()='entries'][1]/*[local-name()='word']")
                  .evaluatesTo("soapreviewword"));

      // 2. Record review: REMEMBERED -> transitions to KNOWN
      client
          .sendRequest(
              withPayload(
                  new StreamSource(
                      new StringReader(
                          """
                          <recordVocabularyReviewRequest xmlns="http://soap.com/english-reading/readings">
                            <wordId>%s</wordId>
                            <assessment>REMEMBERED</assessment>
                          </recordVocabularyReviewRequest>
                          """
                              .formatted(word.id())))))
          .andExpect(noFault())
          .andExpect(
              xpath(
                      "/*[local-name()='recordVocabularyReviewResponse']/*[local-name()='entry']/*[local-name()='wordId']")
                  .evaluatesTo(word.id().toString()))
          .andExpect(
              xpath(
                      "/*[local-name()='recordVocabularyReviewResponse']/*[local-name()='entry']/*[local-name()='status']")
                  .evaluatesTo("KNOWN"));

      // 3. Verify in database
      var updated = vocabulary.findByUserIdAndWordId(user.id(), word.id()).orElseThrow();
      assertThat(updated.status()).isEqualTo(VocabularyStatus.KNOWN);
      assertThat(updated.reviewStage()).isEqualTo(1);
      assertThat(updated.nextReviewAt()).isAfter(nowUtc);
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
    authenticateUser(user.id());
  }

  private void authenticateUser(java.util.UUID userId) {
    var now = Instant.now();
    var jwt =
        new Jwt(
            "test-token",
            now,
            now.plusSeconds(60),
            Map.of("alg", "HS256"),
            Map.of("sub", userId.toString()));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(jwt, jwt, List.of()));
  }

  @Test
  void profileOperationsRequireAuthentication() {
    var client = MockWebServiceClient.createClient(applicationContext);

    client
        .sendRequest(
            withPayload(
                source(
                    """
                    <getMyProfileRequest xmlns="http://soap.com/english-reading/readings"/>
                    """)))
        .andExpect(clientOrSenderFault());
    client
        .sendRequest(
            withPayload(
                source(
                    """
                    <updateMyProfileRequest xmlns="http://soap.com/english-reading/readings"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                      <name>Ada</name><alias xsi:nil="true"/><age xsi:nil="true"/>
                      <nativeLanguage xsi:nil="true"/><learningLanguage>en</learningLanguage>
                    </updateMyProfileRequest>
                    """)))
        .andExpect(clientOrSenderFault());
  }

  @Test
  void authenticatedUserCanUpdateGetAndClearOwnProfileOverSoap() {
    var profileWord = words.save(new Word(null, "profileword", "en"));
    vocabulary.save(
        new UserVocabulary(
            null, user, profileWord, VocabularyStatus.LEARNING, LocalDateTime.now(), null));
    var platformReading = readings.findAllPlatformReadings().getFirst();
    readingProgress.startIfAbsent(user.id(), platformReading.id(), LocalDateTime.now());
    authenticateUser();
    var client = MockWebServiceClient.createClient(applicationContext);
    try {
      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <updateMyProfileRequest xmlns="http://soap.com/english-reading/readings">
                        <name> Ada Updated </name><alias>AdaPublic</alias><age>37</age>
                        <nativeLanguage>ES-co</nativeLanguage><learningLanguage>en</learningLanguage>
                      </updateMyProfileRequest>
                      """)))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='name']").evaluatesTo("Ada Updated"))
          .andExpect(xpath("//*[local-name()='alias']").evaluatesTo("AdaPublic"))
          .andExpect(xpath("//*[local-name()='nativeLanguage']").evaluatesTo("es-CO"))
          .andExpect(xpath("//*[local-name()='email']").evaluatesTo(user.email()));

      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <getMyProfileRequest xmlns="http://soap.com/english-reading/readings"/>
                      """)))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='alias']").evaluatesTo("AdaPublic"))
          .andExpect(xpath("//*[local-name()='age']").evaluatesTo("37"));

      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <updateMyProfileRequest xmlns="http://soap.com/english-reading/readings"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                        <name>Ada Updated</name><alias xsi:nil="true"/><age xsi:nil="true"/>
                        <nativeLanguage>es</nativeLanguage><learningLanguage>en</learningLanguage>
                      </updateMyProfileRequest>
                      """)))
          .andExpect(noFault())
          .andExpect(xpath("count(//*[local-name()='alias'])").evaluatesTo("0"))
          .andExpect(xpath("count(//*[local-name()='age'])").evaluatesTo("0"));

      var persisted = users.findById(user.id()).orElseThrow();
      assertThat(persisted.email()).isEqualTo(user.email());
      assertThat(persisted.passwordHash()).isEqualTo(user.passwordHash());
      assertThat(persisted.onboardingCompleted()).isEqualTo(user.onboardingCompleted());
      assertThat(vocabulary.findByUserIdAndWordId(user.id(), profileWord.id())).isPresent();
      assertThat(readingProgress.findByUserIdAndReadingId(user.id(), platformReading.id()))
          .isPresent();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void aliasUniquenessIsCaseInsensitiveAndAllowsMultipleNulls() {
    var first = user.updateProfile(user.name(), "UniqueAlias", null, null, "en");
    users.save(first);
    var second =
        users.save(new User(null, "Second", UUID.randomUUID() + "@example.com", "hash", null));

    assertThat(users.existsByAliasIgnoreCaseAndIdNot("uniquealias", second.id())).isTrue();
    assertThatThrownBy(
            () -> users.save(second.updateProfile(second.name(), "UNIQUEALIAS", null, null, "en")))
        .isInstanceOf(com.soap.soap.application.exception.AliasAlreadyInUseException.class);

    var third =
        users.save(new User(null, "Third", UUID.randomUUID() + "@example.com", "hash", null));
    assertThat(second.alias()).isNull();
    assertThat(third.alias()).isNull();
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
    assertThat(reading.origin()).isEqualTo(ReadingOrigin.USER);
    assertThat(reading.user().id()).isEqualTo(user.id());
    assertThat(reading.editorialLevel()).isNull();
    assertThat(reading.category()).isNull();
    assertThat(readings.findById(reading.id())).contains(reading);
    assertThat(readings.findSummariesByUserId(user.id(), new PageRequest(0, 10)).content())
        .singleElement()
        .satisfies(
            summary -> {
              assertThat(summary.id()).isEqualTo(reading.id());
              assertThat(summary.title()).isEqualTo(reading.title());
              assertThat(summary.language()).isEqualTo(reading.language());
            });
  }

  @Test
  @Transactional
  void loadsTheSeededPlatformCatalogWithoutFakeOwners() {
    var catalog = readings.findPlatformSummaries(new PageRequest(0, 100));

    assertThat(catalog.totalElements()).isEqualTo(74);
    assertThat(catalog.content())
        .extracting(PlatformReadingSummary::title)
        .contains(
            "A Morning at the Library",
            "Why Cities Need Trees",
            "The Changing Nature of Work",
            "When Algorithms Shape Attention",
            "The Lost Blue Scarf",
            "The Sparrow and the Red Cup",
            "A Boat for the Little Island",
            "The Garden Behind the School",
            "The Train to Harbor Town",
            "A Quiet Morning by the River",
            "The Light in Room Twelve",
            "Lunch for the Night Team",
            "The Phone-Free Table",
            "The Empty Lot Project",
            "Learning to Ask Better Questions",
            "The Road Beyond Pine Hill",
            "The Cost of Constant Attention",
            "A Market Changes Its Rhythm",
            "Listening to the Forest at Night",
            "The Key Beneath the Floor",
            "The Museum of Unfinished Things",
            "A City That Predicts Its Citizens",
            "The Language of the Evening Square",
            "The Cartographer of Vanishing Roads");
    assertThat(catalog.content())
        .filteredOn(summary -> summary.editorialLevel() == EditorialLevel.A1)
        .hasSize(13);
    assertThat(catalog.content())
        .filteredOn(summary -> summary.editorialLevel() == EditorialLevel.A2)
        .hasSize(13);
    assertThat(catalog.content())
        .filteredOn(summary -> summary.editorialLevel() == EditorialLevel.B1)
        .hasSize(14);
    assertThat(catalog.content())
        .filteredOn(summary -> summary.editorialLevel() == EditorialLevel.B2)
        .hasSize(14);
    assertThat(catalog.content())
        .filteredOn(summary -> summary.editorialLevel() == EditorialLevel.C1)
        .hasSize(12);
    assertThat(catalog.content())
        .filteredOn(summary -> summary.editorialLevel() == EditorialLevel.C2)
        .hasSize(8);
    assertThat(catalog.content())
        .allSatisfy(
            summary -> {
              assertThat(summary.language()).isEqualTo("en");
              assertThat(summary.category()).isNotBlank();
            });
    assertThat(catalog.content()).filteredOn(summary -> summary.coverKey() != null).hasSize(63);
    assertThat(catalog.content())
        .filteredOn(summary -> summary.title().equals("The Camera on Platform Three"))
        .singleElement()
        .extracting(PlatformReadingSummary::coverKey)
        .isEqualTo("the-camera-on-platform-three");
    assertThat(catalog.content())
        .filteredOn(summary -> summary.title().equals("A Morning at the Library"))
        .singleElement()
        .extracting(PlatformReadingSummary::coverKey)
        .isEqualTo("a-morning-at-the-library");
    assertThat(catalog.content())
        .filteredOn(summary -> summary.title().equals("Why Cities Need Trees"))
        .singleElement()
        .extracting(PlatformReadingSummary::coverKey)
        .isEqualTo("why-cities-need-trees");
    assertThat(catalog.content())
        .filteredOn(summary -> summary.title().equals("Minor Gods of the Waiting Room"))
        .singleElement()
        .extracting(PlatformReadingSummary::coverKey)
        .isEqualTo("minor-gods-of-the-waiting-room");
    var pagedIds = new java.util.ArrayList<java.util.UUID>();
    for (var page = 0; page < 8; page++) {
      pagedIds.addAll(
          readings.findPlatformSummaries(new PageRequest(page, 10)).content().stream()
              .map(PlatformReadingSummary::id)
              .toList());
    }
    assertThat(pagedIds).hasSize(74).doesNotHaveDuplicates();

    var platformReading =
        readings
            .findById(java.util.UUID.fromString("10000000-0000-0000-0000-000000000001"))
            .orElseThrow();
    assertThat(platformReading.origin()).isEqualTo(ReadingOrigin.PLATFORM);
    assertThat(platformReading.user()).isNull();
    assertThat(platformReading.editorialLevel().name()).isEqualTo("A1");
    assertThat(platformReading.category()).isEqualTo("Daily Life");
  }

  @Test
  void authenticatedUserCanListReadAndAnalyzePlatformCatalogEntriesOverSoap() {
    authenticateUser();
    var client = MockWebServiceClient.createClient(applicationContext);
    try {
      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <listPlatformReadingsRequest xmlns="http://soap.com/english-reading/readings">
                        <page>0</page><size>100</size>
                      </listPlatformReadingsRequest>
                      """)))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='totalElements']").evaluatesTo("74"))
          .andExpect(
              xpath(
                      "//*[local-name()='readings'][*[local-name()='readingId']='10000000-0000-0000-0000-000000000003']/*[local-name()='editorialLevel']")
                  .evaluatesTo("B1"))
          .andExpect(
              xpath(
                      "//*[local-name()='readings'][*[local-name()='readingId']='10000000-0000-0000-0000-000000000003']/*[local-name()='category']")
                  .evaluatesTo("Work"))
          .andExpect(
              xpath(
                      "//*[local-name()='readings'][*[local-name()='readingId']='30000000-0000-0000-0000-000000000009']/*[local-name()='coverKey']")
                  .evaluatesTo("the-camera-on-platform-three"))
          .andExpect(
              xpath(
                      "//*[local-name()='readings'][*[local-name()='readingId']='10000000-0000-0000-0000-000000000001']/*[local-name()='coverKey']")
                  .evaluatesTo("a-morning-at-the-library"));

      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <getReadingRequest xmlns="http://soap.com/english-reading/readings">
                        <readingId>10000000-0000-0000-0000-000000000001</readingId>
                      </getReadingRequest>
                      """)))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='reading']/*[local-name()='userId']").doesNotExist())
          .andExpect(
              xpath("//*[local-name()='reading']/*[local-name()='origin']")
                  .evaluatesTo("PLATFORM"));

      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <getReadingReaderDataRequest xmlns="http://soap.com/english-reading/readings">
                        <readingId>10000000-0000-0000-0000-000000000001</readingId>
                      </getReadingReaderDataRequest>
                      """)))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='title']").evaluatesTo("A Morning at the Library"));

      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <analyzeReadingRequest xmlns="http://soap.com/english-reading/readings">
                        <readingId>10000000-0000-0000-0000-000000000001</readingId>
                      </analyzeReadingRequest>
                      """)))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='totalTokens']").exists());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void recommendsSeededPlatformReadingsOverTheValidatedSoapContract() {
    authenticateUser();
    var client = MockWebServiceClient.createClient(applicationContext);
    try {
      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <recommendPlatformReadingsRequest xmlns="http://soap.com/english-reading/readings">
                        <page>0</page><size>2</size>
                      </recommendPlatformReadingsRequest>
                      """)))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='page']").evaluatesTo("0"))
          .andExpect(xpath("//*[local-name()='size']").evaluatesTo("2"))
          .andExpect(xpath("//*[local-name()='totalElements']").evaluatesTo("74"))
          .andExpect(xpath("count(//*[local-name()='readings'])").evaluatesTo("2"))
          .andExpect(
              xpath("//*[local-name()='readings'][1]/*[local-name()='vocabularyFitPercentage']")
                  .evaluatesTo("30.00"))
          .andExpect(
              xpath(
                      "//*[local-name()='readings'][1]/*[local-name()='classificationConfidencePercentage']")
                  .evaluatesTo("0.00"))
          .andExpect(
              xpath("//*[local-name()='readings'][1]/*[local-name()='explicitNewWords']")
                  .evaluatesTo("0"));

      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <recommendPlatformReadingsRequest xmlns="http://soap.com/english-reading/readings">
                        <page>0</page><size>100</size>
                      </recommendPlatformReadingsRequest>
                      """)))
          .andExpect(noFault())
          .andExpect(
              xpath(
                      "//*[local-name()='readings'][*[local-name()='readingId']='30000000-0000-0000-0000-000000000009']/*[local-name()='coverKey']")
                  .evaluatesTo("the-camera-on-platform-three"))
          .andExpect(
              xpath(
                      "//*[local-name()='readings'][*[local-name()='readingId']='10000000-0000-0000-0000-000000000001']/*[local-name()='coverKey']")
                  .evaluatesTo("a-morning-at-the-library"));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void readerDataReflectsUpsertedStatusesForEveryOccurrenceAndSeparatesPlatformUsers() {
    var platformId = "10000000-0000-0000-0000-000000000001";
    var client = MockWebServiceClient.createClient(applicationContext);
    authenticateUser();
    try {
      expectReaderStatus(client, platformId, "the", null, 3);
      setVocabularyStatus(client, "the", "KNOWN");
      expectReaderStatus(client, platformId, "the", "KNOWN", 3);
      setVocabularyStatus(client, "the", "LEARNING");
      expectReaderStatus(client, platformId, "the", "LEARNING", 3);

      var other =
          users.save(
              new User(
                  null,
                  "Other Reader",
                  "other-reader-" + java.util.UUID.randomUUID() + "@example.com",
                  "hash",
                  null));
      authenticateUser(other.id());
      expectReaderStatus(client, platformId, "the", null, 3);
      setVocabularyStatus(client, "the", "IGNORED");
      expectReaderStatus(client, platformId, "the", "IGNORED", 3);

      authenticateUser(user.id());
      expectReaderStatus(client, platformId, "the", "LEARNING", 3);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private void setVocabularyStatus(MockWebServiceClient client, String word, String status) {
    client
        .sendRequest(
            withPayload(
                source(
                    """
                    <setVocabularyStatusRequest xmlns="http://soap.com/english-reading/readings">
                      <word>%s</word><language>en</language><status>%s</status>
                    </setVocabularyStatusRequest>
                    """
                        .formatted(word, status))))
        .andExpect(noFault())
        .andExpect(xpath("//*[local-name()='entry']/*[local-name()='status']").evaluatesTo(status));
  }

  private void expectReaderStatus(
      MockWebServiceClient client,
      String readingId,
      String normalizedWord,
      String expectedStatus,
      int occurrences) {
    var response =
        client.sendRequest(
            withPayload(
                source(
                    """
                    <getReadingReaderDataRequest xmlns="http://soap.com/english-reading/readings">
                      <readingId>%s</readingId>
                    </getReadingReaderDataRequest>
                    """
                        .formatted(readingId))));
    response
        .andExpect(noFault())
        .andExpect(
            xpath(
                    "count(//*[local-name()='tokens'][*[local-name()='normalizedValue']='%s'])"
                        .formatted(normalizedWord))
                .evaluatesTo(Integer.toString(occurrences)));
    var statusPath =
        "//*[local-name()='tokens'][*[local-name()='normalizedValue']='%s']/*[local-name()='status']"
            .formatted(normalizedWord);
    if (expectedStatus == null) {
      response.andExpect(xpath(statusPath).doesNotExist());
    } else {
      response
          .andExpect(xpath("count(" + statusPath + ")").evaluatesTo(Integer.toString(occurrences)))
          .andExpect(xpath(statusPath + "[1]").evaluatesTo(expectedStatus));
    }
  }

  @Test
  void postgresEmailConstraintMapsOnlyTheNormalizedEmailConflict() {
    var email = "constraint-" + java.util.UUID.randomUUID() + "@example.com";
    users.save(new User(null, "First", email, "hash", null));

    assertThatThrownBy(
            () -> users.save(new User(null, "Second", email.toUpperCase(), "hash", null)))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
  }

  @Test
  void postgresUnrelatedIntegrityFailureRemainsAServerSidePersistenceFailure() {
    assertThatThrownBy(
            () ->
                users.save(
                    new User(
                        null,
                        "x".repeat(101),
                        "long-name-" + java.util.UUID.randomUUID() + "@example.com",
                        "hash",
                        null)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .isNotInstanceOf(EmailAlreadyRegisteredException.class);
  }

  @Test
  @Transactional
  void listsOnlyOwnedReadingSummariesInDescendingOrderWithPagination() {
    var now = LocalDateTime.now();
    var older =
        readings.save(new Reading(null, user, "Older", "secret older", "en", now.minusMinutes(1)));
    var newer = readings.save(new Reading(null, user, "Newer", "secret newer", "es", now));
    var otherUser =
        users.save(
            new User(
                null,
                "Grace",
                "grace-" + java.util.UUID.randomUUID() + "@example.com",
                "{bcrypt}$2a$10$invalidlegacycredentialinvalidlegacycredentialinv",
                null));
    readings.save(new Reading(null, otherUser, "Not owned", "secret foreign", "en", null));

    var firstPage = readings.findSummariesByUserId(user.id(), new PageRequest(0, 1));
    var secondPage = readings.findSummariesByUserId(user.id(), new PageRequest(1, 1));

    assertThat(firstPage.totalElements()).isEqualTo(2);
    assertThat(firstPage.content()).extracting(ReadingSummary::id).containsExactly(newer.id());
    assertThat(secondPage.content()).extracting(ReadingSummary::id).containsExactly(older.id());
    assertThat(firstPage.content()).noneMatch(summary -> summary.title().equals("Not owned"));
    assertThat(ReadingSummary.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .doesNotContain("content");
  }

  @Test
  void soapListingOmitsContentWhileGetReadingReturnsIt() {
    var reading =
        readings.save(new Reading(null, user, "Summary title", "Work work WORK other", "en", null));
    authenticateUser();
    var client = MockWebServiceClient.createClient(applicationContext);
    try {
      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <listUserReadingsRequest xmlns="http://soap.com/english-reading/readings">
                        <page>0</page><size>10</size>
                      </listUserReadingsRequest>
                      """)))
          .andExpect(noFault())
          .andExpect(
              xpath("//*[local-name()='readings']/*[local-name()='readingId']")
                  .evaluatesTo(reading.id().toString()))
          .andExpect(xpath("//*[local-name()='readings']/*[local-name()='content']").doesNotExist())
          .andExpect(
              xpath("//*[local-name()='readings']/*[local-name()='uniqueWords']").evaluatesTo("2"))
          .andExpect(
              xpath("//*[local-name()='readings']/*[local-name()='knownWords']").evaluatesTo("0"))
          .andExpect(
              xpath("//*[local-name()='readings']/*[local-name()='unclassifiedWords']")
                  .evaluatesTo("2"))
          .andExpect(
              xpath("//*[local-name()='readings']/*[local-name()='vocabularyFitPercentage']")
                  .evaluatesTo("30.00"))
          .andExpect(
              xpath(
                      "//*[local-name()='readings']/*[local-name()='classificationConfidencePercentage']")
                  .evaluatesTo("0.00"));

      setVocabularyStatus(client, "work", "KNOWN");

      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <listUserReadingsRequest xmlns="http://soap.com/english-reading/readings">
                        <page>0</page><size>10</size>
                      </listUserReadingsRequest>
                      """)))
          .andExpect(noFault())
          .andExpect(
              xpath("//*[local-name()='readings']/*[local-name()='knownWords']").evaluatesTo("1"))
          .andExpect(
              xpath("//*[local-name()='readings']/*[local-name()='unclassifiedWords']")
                  .evaluatesTo("1"))
          .andExpect(
              xpath("//*[local-name()='readings']/*[local-name()='vocabularyFitPercentage']")
                  .evaluatesTo("65.00"))
          .andExpect(
              xpath(
                      "//*[local-name()='readings']/*[local-name()='classificationConfidencePercentage']")
                  .evaluatesTo("50.00"));

      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <getReadingRequest xmlns="http://soap.com/english-reading/readings">
                        <readingId>%s</readingId>
                      </getReadingRequest>
                      """
                          .formatted(reading.id()))))
          .andExpect(noFault())
          .andExpect(
              xpath("//*[local-name()='reading']/*[local-name()='content']")
                  .evaluatesTo("Work work WORK other"));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void deleteReadingSoapRemovesOnlyTheOwnedTextReadingAndPreservesUnrelatedData() {
    var target =
        readings.save(new Reading(null, user, "Delete me", "known learning ignored", "en", null));
    var survivor =
        readings.save(new Reading(null, user, "Keep me", "unrelated content", "en", null));
    var otherUser =
        users.save(new User(null, "Grace", UUID.randomUUID() + "@example.com", "hash", null));
    var foreign =
        readings.save(new Reading(null, otherUser, "Foreign", "private content", "en", null));
    readingProgress.startIfAbsent(user.id(), target.id(), LocalDateTime.now());

    for (var entry :
        List.of(
            Map.entry("deleteknown-" + UUID.randomUUID(), VocabularyStatus.KNOWN),
            Map.entry("deletelearning-" + UUID.randomUUID(), VocabularyStatus.LEARNING),
            Map.entry("deleteignored-" + UUID.randomUUID(), VocabularyStatus.IGNORED))) {
      var word = words.save(new Word(null, entry.getKey(), "en"));
      var learnedAt = entry.getValue() == VocabularyStatus.KNOWN ? LocalDateTime.now() : null;
      vocabulary.save(
          new UserVocabulary(null, user, word, entry.getValue(), LocalDateTime.now(), learnedAt));
    }
    var vocabularyBefore = vocabulary.findByUserId(user.id(), new PageRequest(0, 100)).content();
    var platformCountBefore = readings.findAllPlatformReadings().size();
    var membershipsBefore =
        jdbcTemplate.queryForObject("select count(*) from reading_collections", Long.class);
    var documentsBefore =
        jdbcTemplate.queryForObject("select count(*) from imported_documents", Long.class);

    authenticateUser();
    var client = MockWebServiceClient.createClient(applicationContext);
    try {
      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <deleteReadingRequest xmlns="http://soap.com/english-reading/readings">
                        <readingId>%s</readingId>
                      </deleteReadingRequest>
                      """
                          .formatted(target.id()))))
          .andExpect(noFault())
          .andExpect(xpath("//*[local-name()='success']").evaluatesTo("true"));

      assertThat(readings.findById(target.id())).isEmpty();
      assertThat(readingProgress.findByUserIdAndReadingId(user.id(), target.id())).isEmpty();
      assertThat(readings.findById(survivor.id())).isPresent();
      client
          .sendRequest(
              withPayload(
                  source(
                      """
                      <listUserReadingsRequest xmlns="http://soap.com/english-reading/readings">
                        <page>0</page><size>100</size>
                      </listUserReadingsRequest>
                      """)))
          .andExpect(noFault())
          .andExpect(
              xpath(
                      "count(//*[local-name()='readings'][*[local-name()='readingId']='%s'])"
                          .formatted(target.id()))
                  .evaluatesTo("0"))
          .andExpect(
              xpath(
                      "count(//*[local-name()='readings'][*[local-name()='readingId']='%s'])"
                          .formatted(survivor.id()))
                  .evaluatesTo("1"));
      assertThat(vocabulary.findByUserId(user.id(), new PageRequest(0, 100)).content())
          .containsAll(vocabularyBefore);
      assertThat(readings.findAllPlatformReadings()).hasSize(platformCountBefore);
      assertThat(
              jdbcTemplate.queryForObject("select count(*) from reading_collections", Long.class))
          .isEqualTo(membershipsBefore);
      assertThat(jdbcTemplate.queryForObject("select count(*) from imported_documents", Long.class))
          .isEqualTo(documentsBefore);

      for (var protectedId :
          List.of(target.id(), foreign.id(), readings.findAllPlatformReadings().getFirst().id())) {
        client
            .sendRequest(
                withPayload(
                    source(
                        """
                        <deleteReadingRequest xmlns="http://soap.com/english-reading/readings">
                          <readingId>%s</readingId>
                        </deleteReadingRequest>
                        """
                            .formatted(protectedId))))
            .andExpect(clientOrSenderFault());
      }
      assertThat(readings.findById(foreign.id())).isPresent();
    } finally {
      SecurityContextHolder.clearContext();
    }

    client
        .sendRequest(
            withPayload(
                source(
                    """
                    <deleteReadingRequest xmlns="http://soap.com/english-reading/readings">
                      <readingId>%s</readingId>
                    </deleteReadingRequest>
                    """
                        .formatted(survivor.id()))))
        .andExpect(clientOrSenderFault());
    assertThat(readings.findById(survivor.id())).isPresent();
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
