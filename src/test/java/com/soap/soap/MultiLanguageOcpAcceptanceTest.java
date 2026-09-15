package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.port.in.ListCollectionReadingsPort;
import com.soap.soap.application.port.in.ListPlatformReadingsPort;
import com.soap.soap.application.port.in.RecommendPlatformReadingsPort;
import com.soap.soap.application.port.in.UpdateMyProfilePort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingCollectionRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.service.ReadingLexicalIndexer;
import com.soap.soap.domain.model.AccessTier;
import com.soap.soap.domain.model.AdaptationKind;
import com.soap.soap.domain.model.EditorialCategory;
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
import com.soap.soap.infrastructure.persistence.entity.ReadingCollectionEntity;
import com.soap.soap.infrastructure.persistence.entity.ReadingCollectionMembershipEntity;
import com.soap.soap.infrastructure.persistence.entity.ReadingCollectionMembershipId;
import com.soap.soap.infrastructure.persistence.repository.JpaReadingCollectionMembershipRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaReadingCollectionRepository;
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
class MultiLanguageOcpAcceptanceTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private UserRepositoryPort users;
  @Autowired private ReadingRepositoryPort readings;
  @Autowired private UpdateMyProfilePort updateProfile;
  @Autowired private RecommendPlatformReadingsPort recommendations;
  @Autowired private ListPlatformReadingsPort listPlatformReadings;
  @Autowired private ReadingLexicalIndexer lexicalIndexer;
  @Autowired private CurrentUserPort currentUserPort;
  @Autowired private JpaReadingCollectionRepository jpaCollections;
  @Autowired private JpaReadingCollectionMembershipRepository jpaMemberships;
  @Autowired private ReadingCollectionRepositoryPort collectionsPort;
  @Autowired private ListCollectionReadingsPort listCollectionReadings;

  private User user;

  @BeforeEach
  void setUp() {
    user =
        users.save(
            new User(
                null,
                "Polyglot Learner",
                "polyglot-" + UUID.randomUUID() + "@example.com",
                "hash",
                LocalDateTime.now(),
                true,
                "poly-" + UUID.randomUUID().toString().substring(0, 8),
                25,
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

  @Test
  @DisplayName(
      "1. User can update learningLanguage to any valid BCP-47 tag without code modification")
  void userCanUpdateLearningLanguageOpenly() {
    // Update to French
    var profileFr =
        updateProfile.updateMyProfile(
            new com.soap.soap.application.command.UpdateMyProfileCommand(
                user.name(), user.alias(), user.age(), user.nativeLanguage(), "fr"));
    assertThat(profileFr.learningLanguage()).isEqualTo("fr");

    // Update to Brazilian Portuguese
    var profilePt =
        updateProfile.updateMyProfile(
            new com.soap.soap.application.command.UpdateMyProfileCommand(
                user.name(), user.alias(), user.age(), user.nativeLanguage(), "pt-BR"));
    assertThat(profilePt.learningLanguage()).isEqualTo("pt-BR");

    // Update to German
    var profileDe =
        updateProfile.updateMyProfile(
            new com.soap.soap.application.command.UpdateMyProfileCommand(
                user.name(), user.alias(), user.age(), user.nativeLanguage(), "de"));
    assertThat(profileDe.learningLanguage()).isEqualTo("de");
  }

  @Test
  @DisplayName(
      "2. Platform readings in French and Portuguese are created and retrieved with complete metadata")
  void platformReadingsInMultipleLanguagesArePersisted() {
    Reading readingFr =
        readings.save(
            new Reading(
                null,
                null,
                "Le Petit Prince (Extrait)",
                "Toutes les grandes personnes ont d'abord ete des enfants.",
                LanguageTag.of("fr"),
                LocalDateTime.now(),
                ReadingOrigin.PLATFORM,
                EditorialLevel.A2,
                EditorialCategory.CULTURE_ARTS_AND_FICTION.displayName(),
                "cover-fr",
                EditorialStatus.PUBLISHED,
                "Un classique de la litterature pour apprendre le francais.",
                EditorialContentType.FICTION,
                "FR",
                EditorialRegion.EUROPE,
                SourceKind.LITERARY_WORK,
                RightsStatus.PUBLIC_DOMAIN,
                AdaptationKind.CURATED_EXCERPT,
                null,
                "Le Petit Prince",
                "Antoine de Saint-Exupery",
                null,
                "Extrait de la dedicace a Leon Werth.",
                "petit-prince-fr",
                null,
                AccessTier.FREE));

    Reading readingPt =
        readings.save(
            new Reading(
                null,
                null,
                "A Lenda do Curupira",
                "O Curupira e um ser fantastico das matas brasileiras.",
                LanguageTag.of("pt-BR"),
                LocalDateTime.now(),
                ReadingOrigin.PLATFORM,
                EditorialLevel.B1,
                EditorialCategory.CULTURE_ARTS_AND_FICTION.displayName(),
                "cover-pt",
                EditorialStatus.PUBLISHED,
                "Lenda do folclore brasileiro com pes virados para tras.",
                EditorialContentType.LEGEND,
                "BR",
                EditorialRegion.SOUTH_AMERICA,
                SourceKind.ORAL_TRADITION,
                RightsStatus.PUBLIC_DOMAIN,
                AdaptationKind.PEDAGOGICAL_ADAPTATION,
                null,
                "Folclore do Brasil",
                null,
                null,
                "Adaptacao pedagogica para nivel intermediario.",
                "curupira-pt",
                null,
                AccessTier.FREE));

    // Verify retrieval by ID
    var loadedFr = readings.findById(readingFr.id()).orElseThrow();
    assertThat(loadedFr.language()).isEqualTo(LanguageTag.of("fr"));
    assertThat(loadedFr.shortDescription()).contains("litterature");
    assertThat(loadedFr.contentType()).isEqualTo(EditorialContentType.FICTION);
    assertThat(loadedFr.region()).isEqualTo(EditorialRegion.EUROPE);
    assertThat(loadedFr.sourceKind()).isEqualTo(SourceKind.LITERARY_WORK);

    var loadedPt = readings.findById(readingPt.id()).orElseThrow();
    assertThat(loadedPt.language()).isEqualTo(LanguageTag.of("pt-BR"));
    assertThat(loadedPt.shortDescription()).contains("folclore");
    assertThat(loadedPt.contentType()).isEqualTo(EditorialContentType.LEGEND);
    assertThat(loadedPt.region()).isEqualTo(EditorialRegion.SOUTH_AMERICA);
    assertThat(loadedPt.sourceKind()).isEqualTo(SourceKind.ORAL_TRADITION);
  }

  @Test
  @DisplayName("3. Recommendation engine filters candidates by user learningLanguage (OCP)")
  void recommendationsFilterCandidatesByLearningLanguage() {
    // 1. Create reading in French
    Reading readingFr =
        readings.save(
            new Reading(
                null,
                null,
                "Contes de la tour Eiffel",
                "Paris est une ville magnifique avec de nombreux monuments historiques.",
                LanguageTag.of("fr"),
                LocalDateTime.now(),
                ReadingOrigin.PLATFORM,
                EditorialLevel.A2,
                EditorialCategory.TRAVEL_AND_PLACES.displayName(),
                "cover-fr-tour",
                EditorialStatus.PUBLISHED,
                "Decouverte de Paris en francais facile.",
                EditorialContentType.TRAVEL_NARRATIVE,
                "FR",
                EditorialRegion.EUROPE,
                SourceKind.ORIGINAL_EDITORIAL,
                RightsStatus.ORIGINAL,
                AdaptationKind.ORIGINAL,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                AccessTier.FREE));
    lexicalIndexer.indexReading(readingFr.id(), readingFr.language().value(), readingFr.content());

    // 2. Create reading in Portuguese
    Reading readingPt =
        readings.save(
            new Reading(
                null,
                null,
                "Viagem pelo Rio Amazonas",
                "O Rio Amazonas e o maior rio do mundo em volume de agua.",
                LanguageTag.of("pt-BR"),
                LocalDateTime.now(),
                ReadingOrigin.PLATFORM,
                EditorialLevel.A2,
                EditorialCategory.NATURE_AND_ENVIRONMENT.displayName(),
                "cover-pt-rio",
                EditorialStatus.PUBLISHED,
                "Uma jornada pelo maior rio do planeta.",
                EditorialContentType.TRAVEL_NARRATIVE,
                "BR",
                EditorialRegion.SOUTH_AMERICA,
                SourceKind.ORIGINAL_EDITORIAL,
                RightsStatus.ORIGINAL,
                AdaptationKind.ORIGINAL,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                AccessTier.FREE));
    lexicalIndexer.indexReading(readingPt.id(), readingPt.language().value(), readingPt.content());

    // 3. Create reading in English
    Reading readingEn =
        readings.save(
            new Reading(
                null,
                null,
                "Journey Through the Highlands",
                "The Scottish Highlands offer breathtaking mountain landscapes and lochs.",
                LanguageTag.of("en"),
                LocalDateTime.now(),
                ReadingOrigin.PLATFORM,
                EditorialLevel.A2,
                EditorialCategory.TRAVEL_AND_PLACES.displayName(),
                "cover-en-high",
                EditorialStatus.PUBLISHED,
                "Explore the wild nature of Scotland.",
                EditorialContentType.TRAVEL_NARRATIVE,
                "GB",
                EditorialRegion.EUROPE,
                SourceKind.ORIGINAL_EDITORIAL,
                RightsStatus.ORIGINAL,
                AdaptationKind.ORIGINAL,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                AccessTier.FREE));
    lexicalIndexer.indexReading(readingEn.id(), readingEn.language().value(), readingEn.content());

    // Case A: User's learning language is "fr"
    updateProfile.updateMyProfile(
        new com.soap.soap.application.command.UpdateMyProfileCommand(
            user.name(), user.alias(), user.age(), user.nativeLanguage(), "fr"));
    var recsFr = recommendations.recommendPlatformReadings(new PageRequest(0, 10));
    assertThat(recsFr.content()).isNotEmpty();
    assertThat(recsFr.content())
        .allMatch(rec -> "fr".equalsIgnoreCase(rec.language()))
        .extracting(rec -> rec.readingId())
        .contains(readingFr.id())
        .doesNotContain(readingPt.id(), readingEn.id());

    // Case B: User's learning language is "pt-BR"
    updateProfile.updateMyProfile(
        new com.soap.soap.application.command.UpdateMyProfileCommand(
            user.name(), user.alias(), user.age(), user.nativeLanguage(), "pt-BR"));
    var recsPt = recommendations.recommendPlatformReadings(new PageRequest(0, 10));
    assertThat(recsPt.content()).isNotEmpty();
    assertThat(recsPt.content())
        .allMatch(rec -> "pt-BR".equalsIgnoreCase(rec.language()))
        .extracting(rec -> rec.readingId())
        .contains(readingPt.id())
        .doesNotContain(readingFr.id(), readingEn.id());

    // Case C: User's learning language is "en"
    updateProfile.updateMyProfile(
        new com.soap.soap.application.command.UpdateMyProfileCommand(
            user.name(), user.alias(), user.age(), user.nativeLanguage(), "en"));
    var recsEn = recommendations.recommendPlatformReadings(new PageRequest(0, 10));
    assertThat(recsEn.content()).isNotEmpty();
    assertThat(recsEn.content())
        .allMatch(rec -> "en".equalsIgnoreCase(rec.language()))
        .extracting(rec -> rec.readingId())
        .contains(readingEn.id())
        .doesNotContain(readingFr.id(), readingPt.id());

    // Case D: User's learning language is "ja" (no Japanese readings in catalog)
    // Must return empty page without fallback to English
    updateProfile.updateMyProfile(
        new com.soap.soap.application.command.UpdateMyProfileCommand(
            user.name(), user.alias(), user.age(), user.nativeLanguage(), "ja"));
    var recsJa = recommendations.recommendPlatformReadings(new PageRequest(0, 10));
    assertThat(recsJa.content()).isEmpty();
    assertThat(recsJa.totalElements()).isEqualTo(0);
  }

  @Test
  @DisplayName("4. ListPlatformReadings filters platform catalog by user learningLanguage")
  void listPlatformReadingsFiltersByLearningLanguage() {
    Reading rFr =
        createReading(
            "Franchise de Paris",
            "Bienvenue a Paris la ville lumiere.",
            "fr",
            EditorialContentType.REAL_STORY);
    Reading rPt =
        createReading(
            "Passeio em Lisboa",
            "Lisboa e uma cidade historica encantadora.",
            "pt-BR",
            EditorialContentType.TRAVEL_NARRATIVE);
    Reading rEn =
        createReading(
            "London Calling",
            "London has rich history and modern vibe.",
            "en",
            EditorialContentType.TRAVEL_NARRATIVE);

    // Case A: learningLanguage = "fr"
    updateProfile.updateMyProfile(
        new com.soap.soap.application.command.UpdateMyProfileCommand(
            user.name(), user.alias(), user.age(), user.nativeLanguage(), "fr"));
    var pageFr = listPlatformReadings.listPlatformReadings(new PageRequest(0, 10));
    assertThat(pageFr.content()).isNotEmpty();
    assertThat(pageFr.content())
        .allMatch(item -> "fr".equalsIgnoreCase(item.language()))
        .extracting(item -> item.id())
        .contains(rFr.id())
        .doesNotContain(rPt.id(), rEn.id());

    // Case B: learningLanguage = "pt-BR"
    updateProfile.updateMyProfile(
        new com.soap.soap.application.command.UpdateMyProfileCommand(
            user.name(), user.alias(), user.age(), user.nativeLanguage(), "pt-BR"));
    var pagePt = listPlatformReadings.listPlatformReadings(new PageRequest(0, 10));
    assertThat(pagePt.content()).isNotEmpty();
    assertThat(pagePt.content())
        .allMatch(item -> "pt-BR".equalsIgnoreCase(item.language()))
        .extracting(item -> item.id())
        .contains(rPt.id())
        .doesNotContain(rFr.id(), rEn.id());

    // Case C: learningLanguage = "ja" (empty, no fallback)
    updateProfile.updateMyProfile(
        new com.soap.soap.application.command.UpdateMyProfileCommand(
            user.name(), user.alias(), user.age(), user.nativeLanguage(), "ja"));
    var pageJa = listPlatformReadings.listPlatformReadings(new PageRequest(0, 10));
    assertThat(pageJa.content()).isEmpty();
    assertThat(pageJa.totalElements()).isEqualTo(0);
  }

  @Test
  @DisplayName(
      "5. ReadingCollectionMembership repository and adapter filter by language before pagination")
  void collectionMembershipRepositoryFiltersByLanguageWithPagination() {
    String collectionKey = "world-stories-" + UUID.randomUUID().toString().substring(0, 8);
    var collection = createCollection(collectionKey, "World Stories");

    Reading rEn =
        createReading(
            "English Tale",
            "Once upon a time in a faraway forest.",
            "en",
            EditorialContentType.FICTION);
    Reading rFr1 =
        createReading(
            "Conte Francais 1",
            "Il etait une fois dans un beau chateau.",
            "fr",
            EditorialContentType.LEGEND);
    Reading rFr2 =
        createReading(
            "Conte Francais 2",
            "Le voyageur arriva au village au crepuscule.",
            "fr",
            EditorialContentType.MYTH);
    Reading rPt =
        createReading(
            "Conto Brasileiro",
            "O rio serpenteava entre as arvores verdes.",
            "pt-BR",
            EditorialContentType.ANECDOTE);

    addReadingToCollection(collection.getId(), rEn.id(), 1);
    addReadingToCollection(collection.getId(), rFr1.id(), 2);
    addReadingToCollection(collection.getId(), rFr2.id(), 3);
    addReadingToCollection(collection.getId(), rPt.id(), 4);

    // Query JPA repository directly: language = "fr", page size 1 -> pagination occurs AFTER
    // language filter
    var pageFr0 =
        jpaMemberships.findReadingsByCollectionKeyAndLanguage(
            collectionKey, "fr", org.springframework.data.domain.PageRequest.of(0, 1));
    assertThat(pageFr0.getTotalElements()).isEqualTo(2);
    assertThat(pageFr0.getContent()).hasSize(1);
    assertThat(pageFr0.getContent().getFirst().getId()).isEqualTo(rFr1.id());

    var pageFr1 =
        jpaMemberships.findReadingsByCollectionKeyAndLanguage(
            collectionKey, "fr", org.springframework.data.domain.PageRequest.of(1, 1));
    assertThat(pageFr1.getTotalElements()).isEqualTo(2);
    assertThat(pageFr1.getContent()).hasSize(1);
    assertThat(pageFr1.getContent().getFirst().getId()).isEqualTo(rFr2.id());

    // Query JPA repository directly: language = "pt-BR"
    var pagePt =
        jpaMemberships.findReadingsByCollectionKeyAndLanguage(
            collectionKey, "pt-BR", org.springframework.data.domain.PageRequest.of(0, 10));
    assertThat(pagePt.getTotalElements()).isEqualTo(1);
    assertThat(pagePt.getContent()).hasSize(1);
    assertThat(pagePt.getContent().getFirst().getId()).isEqualTo(rPt.id());

    // Query persistence adapter: language = "fr"
    var adapterPageFr = collectionsPort.findReadings(collectionKey, "fr", new PageRequest(0, 10));
    assertThat(adapterPageFr.totalElements()).isEqualTo(2);
    assertThat(adapterPageFr.content()).hasSize(2);
    assertThat(adapterPageFr.content())
        .extracting(Reading::id)
        .containsExactly(rFr1.id(), rFr2.id());

    // Query persistence adapter: language = "pt-BR"
    var adapterPagePt =
        collectionsPort.findReadings(collectionKey, "pt-BR", new PageRequest(0, 10));
    assertThat(adapterPagePt.totalElements()).isEqualTo(1);
    assertThat(adapterPagePt.content().getFirst().id()).isEqualTo(rPt.id());

    // Query persistence adapter: language = "ja" -> 0 results
    var adapterPageJa = collectionsPort.findReadings(collectionKey, "ja", new PageRequest(0, 10));
    assertThat(adapterPageJa.totalElements()).isEqualTo(0);
    assertThat(adapterPageJa.content()).isEmpty();
  }

  @Test
  @DisplayName(
      "6. ListCollectionReadings use case filters collection readings by authenticated user's learningLanguage")
  void listCollectionReadingsUseCaseFiltersByAuthenticatedUserLanguage() {
    String collectionKey = "curated-gems-" + UUID.randomUUID().toString().substring(0, 8);
    var collection = createCollection(collectionKey, "Curated Gems");

    Reading rFr =
        createReading(
            "Fables Nouvelles",
            "Le renard et les raisins dans le vignoble.",
            "fr",
            EditorialContentType.FICTION);
    Reading rPt =
        createReading(
            "Lendas Urbanas",
            "Historias contadas pelas ruas de Sao Paulo.",
            "pt-BR",
            EditorialContentType.REAL_STORY);
    Reading rEn =
        createReading(
            "Folklore Echoes",
            "Old myths remembered through winter nights.",
            "en",
            EditorialContentType.LEGEND);

    addReadingToCollection(collection.getId(), rFr.id(), 1);
    addReadingToCollection(collection.getId(), rPt.id(), 2);
    addReadingToCollection(collection.getId(), rEn.id(), 3);

    // Case A: User's learning language is "fr"
    updateProfile.updateMyProfile(
        new com.soap.soap.application.command.UpdateMyProfileCommand(
            user.name(), user.alias(), user.age(), user.nativeLanguage(), "fr"));
    var resFr =
        listCollectionReadings.listCollectionReadings(collectionKey, new PageRequest(0, 10));
    assertThat(resFr.totalElements()).isEqualTo(1);
    assertThat(resFr.content()).hasSize(1);
    assertThat(resFr.content().getFirst().readingId()).isEqualTo(rFr.id());
    assertThat(resFr.content().getFirst().language()).isEqualTo("fr");

    // Case B: User's learning language is "pt-BR"
    updateProfile.updateMyProfile(
        new com.soap.soap.application.command.UpdateMyProfileCommand(
            user.name(), user.alias(), user.age(), user.nativeLanguage(), "pt-BR"));
    var resPt =
        listCollectionReadings.listCollectionReadings(collectionKey, new PageRequest(0, 10));
    assertThat(resPt.totalElements()).isEqualTo(1);
    assertThat(resPt.content()).hasSize(1);
    assertThat(resPt.content().getFirst().readingId()).isEqualTo(rPt.id());
    assertThat(resPt.content().getFirst().language()).isEqualTo("pt-BR");

    // Case C: User's learning language is "ja" -> empty, no fallback
    updateProfile.updateMyProfile(
        new com.soap.soap.application.command.UpdateMyProfileCommand(
            user.name(), user.alias(), user.age(), user.nativeLanguage(), "ja"));
    var resJa =
        listCollectionReadings.listCollectionReadings(collectionKey, new PageRequest(0, 10));
    assertThat(resJa.totalElements()).isEqualTo(0);
    assertThat(resJa.content()).isEmpty();
  }

  private Reading createReading(
      String title, String content, String lang, EditorialContentType type) {
    return readings.save(
        new Reading(
            null,
            null,
            title,
            content,
            LanguageTag.of(lang),
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.A2,
            EditorialCategory.CULTURE_ARTS_AND_FICTION.displayName(),
            "cover-" + lang,
            EditorialStatus.PUBLISHED,
            "Short desc for " + title,
            type,
            "US",
            EditorialRegion.GLOBAL,
            SourceKind.ORIGINAL_EDITORIAL,
            RightsStatus.ORIGINAL,
            AdaptationKind.ORIGINAL,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            AccessTier.FREE));
  }

  private ReadingCollectionEntity createCollection(String key, String name) {
    var c = new ReadingCollectionEntity();
    c.setId(UUID.randomUUID());
    c.setKey(key);
    c.setDisplayName(name);
    c.setDescription("Description for " + name);
    c.setDisplayOrder(1);
    c.setActive(true);
    c.setCoverKey("cover-" + key);
    return jpaCollections.save(c);
  }

  private void addReadingToCollection(UUID collectionId, UUID readingId, int order) {
    var m = new ReadingCollectionMembershipEntity();
    m.setId(new ReadingCollectionMembershipId(collectionId, readingId));
    m.setDisplayOrder(order);
    jpaMemberships.save(m);
  }
}
