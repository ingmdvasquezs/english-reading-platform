package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.exception.EditorialIngestionException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.port.in.CompleteReadingPort;
import com.soap.soap.application.port.in.GetReadingComprehensionQuizPort;
import com.soap.soap.application.port.in.GetReadingReaderDataPort;
import com.soap.soap.application.port.in.IngestEditorialReadingPort;
import com.soap.soap.application.port.in.ListPlatformReadingsPort;
import com.soap.soap.application.port.in.PublishPlatformReadingPort;
import com.soap.soap.application.port.in.RecommendPlatformReadingsPort;
import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.ReadingWordFrequencyRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.User;
import com.soap.soap.infrastructure.editorial.EditorialManifestParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
class EditorialContentIngestionIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private EditorialManifestParser parser;
  @Autowired private IngestEditorialReadingPort ingestion;
  @Autowired private PublishPlatformReadingPort publisher;
  @Autowired private ReadingRepositoryPort readings;
  @Autowired private ComprehensionQuizRepositoryPort comprehensionQuizzes;
  @Autowired private ReadingWordFrequencyRepositoryPort wordFrequencies;
  @Autowired private ListPlatformReadingsPort listPlatformReadings;
  @Autowired private RecommendPlatformReadingsPort recommendations;
  @Autowired private CompleteReadingPort completeReadingPort;
  @Autowired private GetReadingComprehensionQuizPort quizPort;
  @Autowired private GetReadingReaderDataPort getReadingReaderDataPort;
  @Autowired private UserRepositoryPort users;

  private User user;

  @BeforeEach
  void setUp() {
    user =
        users.save(
            new User(
                null,
                "Editorial Test Learner",
                "editorial-learner-" + UUID.randomUUID() + "@example.com",
                "hash",
                LocalDateTime.now(),
                true,
                "ed-" + UUID.randomUUID().toString().substring(0, 8),
                22,
                "es",
                "en"));
    authenticate(user.id());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate(UUID userId) {
    var jwt =
        Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject(userId.toString())
            .claim("sub", userId.toString())
            .build();
    var auth = new UsernamePasswordAuthenticationToken(jwt, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  private String createFictionalTestManifestJson(String groupKey) {
    return """
        {
          "schemaVersion": 1,
          "reading": {
            "title": "The Whispering Stone of El Dorado Peak",
            "content": "High in the misty mountain ranges, villagers speak of a singular mossy stone that whistles when dawn wind blows through its weathered crevices. A shepherd girl named Elena climbed the rocky pass each morning to tend her flock. She noticed that the whistling altered pitch before approaching storms, warning the community hours before dark rain clouds gathered over the quiet valley.",
            "language": "en",
            "editorialLevel": "B1",
            "category": "Culture, Arts & Fiction",
            "shortDescription": "A mountain shepherd girl uncovers the natural acoustic secret of a legendary whistling stone.",
            "contentType": "LEGEND",
            "countryCode": "CO",
            "region": "SOUTH_AMERICA",
            "sourceKind": "ORAL_TRADITION",
            "rightsStatus": "PUBLIC_DOMAIN",
            "adaptationKind": "PEDAGOGICAL_ADAPTATION",
            "sourceLanguage": "es",
            "sourceTitle": "Cuentos Ficticios de la Cordillera",
            "sourceAuthor": "Folklore Imaginario",
            "sourceUrl": null,
            "sourceNotes": "Completely fictional test fable crafted for pipeline integration verification.",
            "adaptationGroupKey": "%s",
            "coverKey": "fictional-whispering-stone",
            "coverAttribution": "Platform Test Studio",
            "accessTier": "FREE"
          },
          "comprehensionQuiz": [
            {
              "ordinal": 1,
              "questionType": "FACTUAL",
              "prompt": "What causes the stone to produce a whistling sound?",
              "explanation": "The text states that wind blowing through its weathered crevices produces the sound.",
              "options": [
                { "ordinal": 1, "content": "Dawn wind blowing through weathered crevices", "isCorrect": true },
                { "ordinal": 2, "content": "Underground volcanic steam", "isCorrect": false },
                { "ordinal": 3, "content": "Birds nesting inside hollow chambers", "isCorrect": false },
                { "ordinal": 4, "content": "Running mountain stream waters", "isCorrect": false }
              ]
            },
            {
              "ordinal": 2,
              "questionType": "INFERENCE",
              "prompt": "Why did the villagers find the stone valuable?",
              "explanation": "The change in sound alerted them to impending storms ahead of time.",
              "options": [
                { "ordinal": 1, "content": "It provided an early warning before mountain storms arrived", "isCorrect": true },
                { "ordinal": 2, "content": "They believed it contained hidden gold veins", "isCorrect": false },
                { "ordinal": 3, "content": "It marked the border of neighboring farms", "isCorrect": false },
                { "ordinal": 4, "content": "It provided shelter during freezing blizzards", "isCorrect": false }
              ]
            },
            {
              "ordinal": 3,
              "questionType": "MAIN_IDEA",
              "prompt": "What is the primary theme of the narrative?",
              "explanation": "The story highlights attentiveness to natural patterns and how observation benefits a community.",
              "options": [
                { "ordinal": 1, "content": "Careful observation of nature can protect a community", "isCorrect": true },
                { "ordinal": 2, "content": "Shepherding is the most profitable rural occupation", "isCorrect": false },
                { "ordinal": 3, "content": "Ancient stones should never be approached by travelers", "isCorrect": false },
                { "ordinal": 4, "content": "Mountain passes are too hazardous for daily life", "isCorrect": false }
              ]
            },
            {
              "ordinal": 4,
              "questionType": "FACTUAL",
              "prompt": "Who noticed the pattern of the stone's changing pitch?",
              "explanation": "The text explicitly introduces Elena the shepherd girl as the one who observed it.",
              "options": [
                { "ordinal": 1, "content": "Elena, the young shepherd girl", "isCorrect": true },
                { "ordinal": 2, "content": "A visiting mineral prospector", "isCorrect": false },
                { "ordinal": 3, "content": "The elder council of the village", "isCorrect": false },
                { "ordinal": 4, "content": "A lost mountaineer seeking shelter", "isCorrect": false }
              ]
            },
            {
              "ordinal": 5,
              "questionType": "INFERENCE",
              "prompt": "What can be inferred about Elena's routine?",
              "explanation": "Her daily morning climbs gave her repeated opportunities to hear and compare the sounds.",
              "options": [
                { "ordinal": 1, "content": "Her regular morning walks gave her keen familiarity with the mountain environment", "isCorrect": true },
                { "ordinal": 2, "content": "She disliked spending time with her livestock", "isCorrect": false },
                { "ordinal": 3, "content": "She only climbed the pass when town celebrations took place", "isCorrect": false },
                { "ordinal": 4, "content": "She was searching specifically for acoustic anomalies", "isCorrect": false }
              ]
            },
            {
              "ordinal": 6,
              "questionType": "MAIN_IDEA",
              "prompt": "Which lesson does the legend pass down to future generations?",
              "explanation": "The legend teaches respect for natural signs and listening carefully to local surroundings.",
              "options": [
                { "ordinal": 1, "content": "Listening closely to nature fosters harmony and resilience", "isCorrect": true },
                { "ordinal": 2, "content": "Modern meteorology makes folklore useless", "isCorrect": false },
                { "ordinal": 3, "content": "Only village elders are capable of understanding weather", "isCorrect": false },
                { "ordinal": 4, "content": "Living high in the cordillera should be avoided", "isCorrect": false }
              ]
            }
          ]
        }
        """
        .formatted(groupKey);
  }

  @Test
  @DisplayName(
      "End-to-End: manifest parse -> DRAFT -> invisible -> publish -> discoverable & quiz available -> reingest rejected")
  void fullEditorialIngestionAndPublicationLifecycle(@TempDir Path tempDir) throws Exception {
    String groupKey = "fictional-whispering-stone-" + UUID.randomUUID().toString().substring(0, 8);
    Path manifestPath = tempDir.resolve("whispering-stone.json");
    Files.writeString(manifestPath, createFictionalTestManifestJson(groupKey));

    // 1. Parse manifest from external path
    var command = parser.parse(manifestPath);
    assertThat(command.title()).isEqualTo("The Whispering Stone of El Dorado Peak");
    assertThat(command.adaptationGroupKey()).isEqualTo(groupKey);

    // 2. Ingest manifest -> DRAFT
    var ingestResult = ingestion.ingest(command);
    assertThat(ingestResult.created()).isTrue();
    assertThat(ingestResult.status()).isEqualTo(EditorialStatus.DRAFT);
    assertThat(ingestResult.questionsCount()).isEqualTo(6);

    UUID readingId = ingestResult.readingId();

    // 3. Verify database state after ingestion
    var loadedDraft = readings.findById(readingId).orElseThrow();
    assertThat(loadedDraft.origin()).isEqualTo(ReadingOrigin.PLATFORM);
    assertThat(loadedDraft.editorialStatus()).isEqualTo(EditorialStatus.DRAFT);
    assertThat(loadedDraft.user()).isNull();
    assertThat(loadedDraft.coverKey()).isEqualTo("fictional-whispering-stone");

    var quiz = comprehensionQuizzes.findByReadingId(readingId).orElseThrow();
    assertThat(quiz.questions()).hasSize(6);
    assertThat(quiz.questions()).allMatch(q -> q.options().size() == 4);
    assertThat(quiz.questions())
        .allMatch(q -> q.options().stream().filter(o -> o.isCorrect()).count() == 1);

    assertThat(wordFrequencies.existsByReadingId(readingId)).isTrue();
    var frequencies = wordFrequencies.findFrequenciesByReadingId(readingId);
    assertThat(frequencies).isNotEmpty();
    assertThat(frequencies).containsKey("stone");

    // 4. Verify DRAFT is INVISIBLE to discovery, recommendations, reader data, and quiz
    var platformPageBefore = listPlatformReadings.listPlatformReadings(new PageRequest(0, 20));
    assertThat(platformPageBefore.content()).noneMatch(item -> item.id().equals(readingId));

    var recsBefore = recommendations.recommendPlatformReadings(new PageRequest(0, 20));
    assertThat(recsBefore.content()).noneMatch(rec -> rec.readingId().equals(readingId));

    assertThatThrownBy(() -> getReadingReaderDataPort.getReadingReaderData(readingId))
        .isInstanceOf(ReadingNotFoundException.class);

    assertThatThrownBy(() -> quizPort.getQuiz(readingId))
        .isInstanceOf(ReadingNotFoundException.class);

    // 5. Publish the reading
    var published = publisher.publish(readingId);
    assertThat(published.editorialStatus()).isEqualTo(EditorialStatus.PUBLISHED);

    var loadedPublished = readings.findById(readingId).orElseThrow();
    assertThat(loadedPublished.editorialStatus()).isEqualTo(EditorialStatus.PUBLISHED);

    // 6. Verify PUBLISHED is now DISCOVERABLE
    var platformPageAfter = listPlatformReadings.listPlatformReadings(new PageRequest(0, 20));
    assertThat(platformPageAfter.content())
        .anyMatch(
            item ->
                item.id().equals(readingId)
                    && "The Whispering Stone of El Dorado Peak".equals(item.title()));

    // 7. Verify Comprehension Quiz is accessible for users once reading is completed
    completeReadingPort.completeReading(readingId);
    var quizView = quizPort.getQuiz(readingId);
    assertThat(quizView).isNotNull();
    assertThat(quizView.readingId()).isEqualTo(readingId);
    assertThat(quizView.questions()).hasSize(3); // Selection policy selects exactly 3 for display
    assertThat(quizView.questions()).allMatch(q -> q.options().size() == 4);

    // 8. Re-execute same manifest on already PUBLISHED reading -> MUST BE REJECTED
    assertThatThrownBy(() -> ingestion.ingest(command))
        .isInstanceOf(EditorialIngestionException.class)
        .hasMessageContaining("already PUBLISHED");

    // Verify reading was NOT duplicated
    var countForGroup =
        readings.findPlatformReadingByAdaptationKey(
            groupKey, "en", loadedPublished.editorialLevel());
    assertThat(countForGroup).isPresent();
    assertThat(countForGroup.get().id()).isEqualTo(readingId);
  }
}
