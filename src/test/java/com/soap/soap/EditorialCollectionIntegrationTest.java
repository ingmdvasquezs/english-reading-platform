package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.command.EditorialCollectionReadingItemCommand;
import com.soap.soap.application.command.ImportEditorialCollectionCommand;
import com.soap.soap.application.exception.CollectionNotFoundException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.port.in.ImportEditorialCollectionPort;
import com.soap.soap.application.port.in.ListCollectionReadingsPort;
import com.soap.soap.application.port.in.ListCollectionsPort;
import com.soap.soap.application.port.out.ReadingCollectionRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.AccessTier;
import com.soap.soap.domain.model.AdaptationKind;
import com.soap.soap.domain.model.EditorialContentType;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialRegion;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.LanguageTag;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.RightsStatus;
import com.soap.soap.domain.model.SourceKind;
import com.soap.soap.domain.model.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
class EditorialCollectionIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private ImportEditorialCollectionPort importCollectionPort;
  @Autowired private ReadingCollectionRepositoryPort collections;
  @Autowired private ReadingRepositoryPort readings;
  @Autowired private ListCollectionsPort listCollectionsPort;
  @Autowired private ListCollectionReadingsPort listCollectionReadingsPort;
  @Autowired private UserRepositoryPort users;

  private User englishUser;
  private User frenchUser;

  private String mohanKey;
  private String madremonteKey;
  private String patasolaKey;
  private String lloronaKey;
  private String candilejaKey;
  private String collectionKey;

  private Reading mohan;
  private Reading madremonte;
  private Reading patasola;
  private Reading llorona;
  private Reading candileja;

  @BeforeEach
  void setUp() {
    englishUser =
        users.save(
            new User(
                null,
                "English Learner",
                "en-" + UUID.randomUUID() + "@example.com",
                "hash",
                LocalDateTime.now(),
                true,
                "en-" + UUID.randomUUID().toString().substring(0, 8),
                25,
                "es",
                "en"));

    frenchUser =
        users.save(
            new User(
                null,
                "French Learner",
                "fr-" + UUID.randomUUID() + "@example.com",
                "hash",
                LocalDateTime.now(),
                true,
                "fr-" + UUID.randomUUID().toString().substring(0, 8),
                25,
                "es",
                "fr"));

    authenticate(englishUser.id());

    var runId = UUID.randomUUID().toString().substring(0, 8);
    mohanKey = "mohan-" + runId;
    madremonteKey = "madremonte-" + runId;
    patasolaKey = "patasola-" + runId;
    lloronaKey = "llorona-" + runId;
    candilejaKey = "candileja-" + runId;
    collectionKey = "col-myths-" + runId;

    // Seed 5 isolated published readings
    mohan =
        seedReading(
            "The Mohan Test", mohanKey, "en", EditorialLevel.B1, "Desc Mohan", "cover-mohan");
    madremonte =
        seedReading(
            "The Madremonte Test",
            madremonteKey,
            "en",
            EditorialLevel.B2,
            "Desc Madremonte",
            "cover-madremonte");
    patasola =
        seedReading(
            "The Patasola Test",
            patasolaKey,
            "en",
            EditorialLevel.B2,
            "Desc Patasola",
            "cover-patasola");
    llorona =
        seedReading(
            "La Llorona Test",
            lloronaKey,
            "en",
            EditorialLevel.B2,
            "Desc Llorona",
            "cover-llorona");
    candileja =
        seedReading(
            "The Candileja Test",
            candilejaKey,
            "en",
            EditorialLevel.B1,
            "Desc Candileja",
            "cover-candileja");
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

  private Reading seedReading(
      String title,
      String groupKey,
      String lang,
      EditorialLevel level,
      String shortDesc,
      String coverKey) {
    var reading =
        new Reading(
            null,
            null,
            title,
            "A traditional tale about "
                + title
                + ". The characters explore mountain paths and natural springs.",
            LanguageTag.of(lang),
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            level,
            "Culture, Arts & Fiction",
            coverKey,
            EditorialStatus.PUBLISHED,
            shortDesc,
            EditorialContentType.LEGEND,
            "CO",
            EditorialRegion.SOUTH_AMERICA,
            SourceKind.ORAL_TRADITION,
            RightsStatus.ORIGINAL,
            AdaptationKind.PEDAGOGICAL_ADAPTATION,
            LanguageTag.of("es"),
            "Institutional Source",
            "Community",
            "https://source.example.com",
            "Source notes",
            groupKey,
            null,
            AccessTier.FREE);
    return readings.save(reading);
  }

  @Test
  @DisplayName(
      "Imports Colombian collection, lists active collection, and returns 5 readings in editorial"
          + " order")
  void importsCollectionAndRetrievesViaSoapEndpoints() {
    var command =
        new ImportEditorialCollectionCommand(
            collectionKey,
            "Mitos y leyendas de Colombia",
            "Una coleccion de relatos tradicionales colombianos sobre espiritus.",
            1,
            true,
            null,
            List.of(
                new EditorialCollectionReadingItemCommand(mohanKey, "en", EditorialLevel.B1, 1),
                new EditorialCollectionReadingItemCommand(
                    madremonteKey, "en", EditorialLevel.B2, 2),
                new EditorialCollectionReadingItemCommand(patasolaKey, "en", EditorialLevel.B2, 3),
                new EditorialCollectionReadingItemCommand(lloronaKey, "en", EditorialLevel.B2, 4),
                new EditorialCollectionReadingItemCommand(
                    candilejaKey, "en", EditorialLevel.B1, 5)));

    var importResult = importCollectionPort.importCollection(command);

    assertThat(importResult.created()).isTrue();
    assertThat(importResult.key()).isEqualTo(collectionKey);
    assertThat(importResult.membershipsCount()).isEqualTo(5);

    // Verify ListCollections
    var collectionsList = listCollectionsPort.listCollections();
    var matchedCollection =
        collectionsList.stream().filter(c -> c.key().equals(collectionKey)).findFirst();
    assertThat(matchedCollection).isPresent();
    assertThat(matchedCollection.get().displayName()).isEqualTo("Mitos y leyendas de Colombia");
    assertThat(matchedCollection.get().displayOrder()).isEqualTo(1);
    assertThat(matchedCollection.get().active()).isTrue();

    // Verify ListCollectionReadings for English learner
    var pagedReadings =
        listCollectionReadingsPort.listCollectionReadings(collectionKey, new PageRequest(0, 10));

    assertThat(pagedReadings.totalElements()).isEqualTo(5);
    assertThat(pagedReadings.content()).hasSize(5);

    // Verify exact editorial order and preserved metadata
    var titles = pagedReadings.content().stream().map(RecommendedPlatformReading::title).toList();
    assertThat(titles)
        .containsExactly(
            "The Mohan Test",
            "The Madremonte Test",
            "The Patasola Test",
            "La Llorona Test",
            "The Candileja Test");

    var first = pagedReadings.content().get(0);
    assertThat(first.readingId()).isEqualTo(mohan.id());
    assertThat(first.editorialLevel()).isEqualTo(EditorialLevel.B1);
    assertThat(first.shortDescription()).isEqualTo("Desc Mohan");
    assertThat(first.coverKey()).isEqualTo("cover-mohan");

    var second = pagedReadings.content().get(1);
    assertThat(second.readingId()).isEqualTo(madremonte.id());
    assertThat(second.editorialLevel()).isEqualTo(EditorialLevel.B2);
    assertThat(second.shortDescription()).isEqualTo("Desc Madremonte");
    assertThat(second.coverKey()).isEqualTo("cover-madremonte");
  }

  @Test
  @DisplayName(
      "Re-importing is fully idempotent and updates metadata without duplicating memberships")
  void reimportingIsIdempotent() {
    var initialCommand =
        new ImportEditorialCollectionCommand(
            collectionKey,
            "Mitos y leyendas de Colombia",
            "Initial description",
            1,
            true,
            null,
            List.of(
                new EditorialCollectionReadingItemCommand(mohanKey, "en", EditorialLevel.B1, 1),
                new EditorialCollectionReadingItemCommand(
                    madremonteKey, "en", EditorialLevel.B2, 2),
                new EditorialCollectionReadingItemCommand(patasolaKey, "en", EditorialLevel.B2, 3),
                new EditorialCollectionReadingItemCommand(lloronaKey, "en", EditorialLevel.B2, 4),
                new EditorialCollectionReadingItemCommand(
                    candilejaKey, "en", EditorialLevel.B1, 5)));

    var firstResult = importCollectionPort.importCollection(initialCommand);
    assertThat(firstResult.created()).isTrue();

    // Reimport with updated description
    var reimportCommand =
        new ImportEditorialCollectionCommand(
            collectionKey,
            "Mitos y leyendas de Colombia",
            "Updated editorial description for reimport test",
            1,
            true,
            null,
            List.of(
                new EditorialCollectionReadingItemCommand(mohanKey, "en", EditorialLevel.B1, 1),
                new EditorialCollectionReadingItemCommand(
                    madremonteKey, "en", EditorialLevel.B2, 2),
                new EditorialCollectionReadingItemCommand(patasolaKey, "en", EditorialLevel.B2, 3),
                new EditorialCollectionReadingItemCommand(lloronaKey, "en", EditorialLevel.B2, 4),
                new EditorialCollectionReadingItemCommand(
                    candilejaKey, "en", EditorialLevel.B1, 5)));

    var secondResult = importCollectionPort.importCollection(reimportCommand);
    assertThat(secondResult.created()).isFalse();
    assertThat(secondResult.collectionId()).isEqualTo(firstResult.collectionId());
    assertThat(secondResult.membershipsCount()).isEqualTo(5);

    var pagedReadings =
        listCollectionReadingsPort.listCollectionReadings(collectionKey, new PageRequest(0, 10));
    assertThat(pagedReadings.totalElements()).isEqualTo(5);
  }

  @Test
  @DisplayName("ListCollectionReadings scopes to user learningLanguage")
  void scopesReadingsToLearningLanguage() {
    var command =
        new ImportEditorialCollectionCommand(
            collectionKey,
            "Mitos y leyendas de Colombia",
            "Desc",
            1,
            true,
            null,
            List.of(
                new EditorialCollectionReadingItemCommand(mohanKey, "en", EditorialLevel.B1, 1)));

    importCollectionPort.importCollection(command);

    // Switch authentication to French learner
    authenticate(frenchUser.id());

    var pagedReadings =
        listCollectionReadingsPort.listCollectionReadings(collectionKey, new PageRequest(0, 10));

    // English reading should not be returned for a French learning user
    assertThat(pagedReadings.totalElements()).isEqualTo(0);
    assertThat(pagedReadings.content()).isEmpty();
  }

  @Test
  @DisplayName(
      "Inactive collection is excluded from listCollections and rejects listCollectionReadings")
  void inactiveCollectionHandling() {
    var inactiveKey = "inactive-" + UUID.randomUUID().toString().substring(0, 8);
    var command =
        new ImportEditorialCollectionCommand(
            inactiveKey,
            "Inactive Collection",
            "Desc",
            2,
            false,
            null,
            List.of(
                new EditorialCollectionReadingItemCommand(mohanKey, "en", EditorialLevel.B1, 1)));

    importCollectionPort.importCollection(command);

    var collectionsList = listCollectionsPort.listCollections();
    assertThat(collectionsList).noneMatch(c -> c.key().equals(inactiveKey));

    assertThatThrownBy(
            () ->
                listCollectionReadingsPort.listCollectionReadings(
                    inactiveKey, new PageRequest(0, 10)))
        .isInstanceOf(CollectionNotFoundException.class);
  }
}
