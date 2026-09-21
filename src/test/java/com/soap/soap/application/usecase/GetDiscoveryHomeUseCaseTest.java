package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.model.ContinueReadingItem;
import com.soap.soap.application.model.DiscoveryCountrySummary;
import com.soap.soap.application.model.DiscoveryHomeResult;
import com.soap.soap.application.model.DiscoveryRegionDetails;
import com.soap.soap.application.model.DiscoveryRegionOverviewResult;
import com.soap.soap.application.model.DiscoveryTopicSummary;
import com.soap.soap.application.model.GetDiscoveryHomeQuery;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.port.in.GetDiscoveryRegionOverviewPort;
import com.soap.soap.application.port.in.ListContinueReadingPort;
import com.soap.soap.application.port.in.RecommendPlatformReadingsPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingCollectionRepositoryPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.service.PlatformReadingPersonalizationService;
import com.soap.soap.application.service.SpecializedDiscoveryBlockRegistry;
import com.soap.soap.domain.model.AccessTier;
import com.soap.soap.domain.model.AdaptationKind;
import com.soap.soap.domain.model.DiscoveryTopic;
import com.soap.soap.domain.model.EditorialContentType;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialRegion;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.LanguageTag;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingCollection;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.RecommendationReasonCode;
import com.soap.soap.domain.model.RightsStatus;
import com.soap.soap.domain.model.SourceKind;
import com.soap.soap.domain.model.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetDiscoveryHomeUseCaseTest {

  @Mock private UserRepositoryPort users;
  @Mock private CurrentUserPort currentUser;
  @Mock private ListContinueReadingPort listContinueReadingPort;
  @Mock private RecommendPlatformReadingsPort recommendPlatformReadingsPort;
  @Mock private GetDiscoveryRegionOverviewPort getDiscoveryRegionOverviewPort;
  @Mock private ReadingRepositoryPort readingRepository;
  @Mock private ReadingCollectionRepositoryPort readingCollectionRepository;
  @Mock private ReadingProgressRepositoryPort readingProgressRepository;
  @Mock private PlatformReadingPersonalizationService personalizationService;

  private final SpecializedDiscoveryBlockRegistry specializedRegistry =
      new SpecializedDiscoveryBlockRegistry();

  private GetDiscoveryHomeUseCase useCase;

  private final UUID userId = UUID.randomUUID();
  private final User testUser =
      new User(
          userId,
          "Andres",
          "andres@example.com",
          "hash",
          LocalDateTime.now(),
          true,
          "Andres",
          25,
          "es",
          "en");

  @BeforeEach
  void setUp() {
    useCase =
        new GetDiscoveryHomeUseCase(
            users,
            currentUser,
            listContinueReadingPort,
            recommendPlatformReadingsPort,
            getDiscoveryRegionOverviewPort,
            readingRepository,
            readingCollectionRepository,
            readingProgressRepository,
            personalizationService,
            specializedRegistry);

    org.mockito.Mockito.lenient().when(currentUser.requireUserId()).thenReturn(userId);
    org.mockito.Mockito.lenient().when(users.findById(userId)).thenReturn(Optional.of(testUser));
    org.mockito.Mockito.lenient()
        .when(listContinueReadingPort.listContinueReading(any()))
        .thenReturn(new PageResult<>(List.of(), 0, 10, 0));
    org.mockito.Mockito.lenient()
        .when(recommendPlatformReadingsPort.recommendPlatformReadings(any()))
        .thenReturn(new PageResult<>(List.of(), 0, 8, 0));
    org.mockito.Mockito.lenient()
        .when(readingRepository.findPublishedPlatformReadingIdsByCountryCodes(any()))
        .thenReturn(Collections.emptySet());
  }

  @Test
  @DisplayName(
      "1. Current 15 CO myths: New shelf omitted, colombian-myths generic shelf suppressed")
  void testCurrent15CoMyths_NewShelfOmitted_AndColombianMythsSuppressed() {
    var region = new DiscoveryRegionDetails("latin-america", "Latinoamérica", "Historias");
    var coTopics =
        List.of(new DiscoveryTopicSummary("MYTHS_AND_LEGENDS", "Mitos y leyendas", 1, 15));
    var coCountry =
        new DiscoveryCountrySummary(
            "CO", "Colombia", "Tagline", "Desc", 1, 15, List.of(), coTopics);
    when(getDiscoveryRegionOverviewPort.getOverview("latin-america"))
        .thenReturn(new DiscoveryRegionOverviewResult(region, List.of(coCountry)));

    List<Reading> all15CoReadings = new ArrayList<>();
    for (int i = 1; i <= 15; i++) {
      all15CoReadings.add(
          createPlatformReading(
              UUID.randomUUID(), "Mito " + i, "CO", DiscoveryTopic.MYTHS_AND_LEGENDS));
    }

    List<Reading> preview8 = all15CoReadings.subList(0, 8);
    when(readingRepository.browsePlatformReadings(
            eq(null),
            eq(null),
            eq(null),
            eq("CO"),
            eq(DiscoveryTopic.MYTHS_AND_LEGENDS),
            eq("en"),
            any()))
        .thenReturn(new PageResult<>(preview8, 0, 8, 15));

    when(readingRepository.findRecentPlatformReadings(eq("en"), eq(0), eq(20)))
        .thenReturn(all15CoReadings);

    var colombiaCollection =
        new ReadingCollection(
            UUID.randomUUID(),
            "colombian-myths-legends",
            "Mitos y leyendas de Colombia",
            "Desc",
            1,
            true,
            null);
    when(readingCollectionRepository.findAllActive()).thenReturn(List.of(colombiaCollection));

    when(personalizationService.personalizeReadings(any(), any(), anyList(), any()))
        .thenAnswer(
            inv -> {
              List<Reading> list = inv.getArgument(2);
              return list.stream().map(this::toRecommended).toList();
            });
    when(readingProgressRepository.findByUserIdAndReadingIds(any(), anySet()))
        .thenReturn(Collections.emptyMap());

    DiscoveryHomeResult result = useCase.getDiscoveryHome(new GetDiscoveryHomeQuery(10, 8, 8));

    assertThat(result.latinAmerica()).isNotNull();
    assertThat(result.latinAmerica().defaultCountryCode()).isEqualTo("CO");
    assertThat(result.latinAmerica().readings()).hasSize(8);

    // New shelf is omitted because all 15 readings are CO + MYTHS_AND_LEGENDS
    // and colombian-myths-legends generic shelf is suppressed
    assertThat(result.shelves()).isEmpty();

    // Verify batch query for generic collections was NOT called since only colombian-myths existed
    verify(readingCollectionRepository, never())
        .findTopReadingsByCollectionIds(any(), any(), anyInt());
  }

  @Test
  @DisplayName(
      "2. Meaningful diversity: MX + REAL_STORIES and Classic selected ahead of redundant CO myths")
  void testMeaningfulDiversity_MxAndClassicSelectedAheadOfRedundantCoMyths() {
    var region = new DiscoveryRegionDetails("latin-america", "Latinoamérica", "Historias");
    var coTopics =
        List.of(new DiscoveryTopicSummary("MYTHS_AND_LEGENDS", "Mitos y leyendas", 1, 5));
    var coCountry =
        new DiscoveryCountrySummary("CO", "Colombia", "Tagline", "Desc", 1, 5, List.of(), coTopics);
    when(getDiscoveryRegionOverviewPort.getOverview("latin-america"))
        .thenReturn(new DiscoveryRegionOverviewResult(region, List.of(coCountry)));

    var coReading1 =
        createPlatformReading(
            UUID.randomUUID(), "El Mohán", "CO", DiscoveryTopic.MYTHS_AND_LEGENDS);
    var coReading2 =
        createPlatformReading(
            UUID.randomUUID(), "La Llorona", "CO", DiscoveryTopic.MYTHS_AND_LEGENDS);
    when(readingRepository.browsePlatformReadings(
            eq(null),
            eq(null),
            eq(null),
            eq("CO"),
            eq(DiscoveryTopic.MYTHS_AND_LEGENDS),
            eq("en"),
            any()))
        .thenReturn(new PageResult<>(List.of(coReading1, coReading2), 0, 8, 2));

    var redundantCo3 =
        createPlatformReading(
            UUID.randomUUID(), "La Madremonte", "CO", DiscoveryTopic.MYTHS_AND_LEGENDS);
    var mxReading =
        createPlatformReading(UUID.randomUUID(), "Frida Kahlo", "MX", DiscoveryTopic.REAL_STORIES);
    var classicReading =
        createPlatformReading(UUID.randomUUID(), "Pride and Prejudice", null, null);

    when(readingRepository.findRecentPlatformReadings(eq("en"), eq(0), eq(20)))
        .thenReturn(List.of(redundantCo3, mxReading, classicReading));
    when(readingCollectionRepository.findAllActive()).thenReturn(List.of());

    when(personalizationService.personalizeReadings(any(), any(), anyList(), any()))
        .thenAnswer(
            inv -> {
              List<Reading> list = inv.getArgument(2);
              return list.stream().map(this::toRecommended).toList();
            });
    when(readingProgressRepository.findByUserIdAndReadingIds(any(), anySet()))
        .thenReturn(Collections.emptyMap());

    DiscoveryHomeResult result = useCase.getDiscoveryHome(new GetDiscoveryHomeQuery(10, 8, 8));

    assertThat(result.shelves()).hasSize(1);
    var newShelf = result.shelves().get(0);
    assertThat(newShelf.key()).isEqualTo("new");
    assertThat(newShelf.type()).isEqualTo("DYNAMIC");
    assertThat(newShelf.readings())
        .extracting(RecommendedPlatformReading::title)
        .containsExactly("Frida Kahlo", "Pride and Prejudice");
  }

  @Test
  @DisplayName("3. Exact card duplicates from Latin America preview are excluded from New shelf")
  void testExactCardDuplicatesExcludedFromNewShelf() {
    var region = new DiscoveryRegionDetails("latin-america", "Latinoamérica", "Historias");
    var coCountry =
        new DiscoveryCountrySummary(
            "CO", "Colombia", "Tagline", "Desc", 1, 1, List.of(), List.of());
    when(getDiscoveryRegionOverviewPort.getOverview("latin-america"))
        .thenReturn(new DiscoveryRegionOverviewResult(region, List.of(coCountry)));

    var previewReading =
        createPlatformReading(UUID.randomUUID(), "Mohán", "CO", DiscoveryTopic.MYTHS_AND_LEGENDS);
    when(readingRepository.browsePlatformReadings(
            eq(null), eq(null), eq(null), eq("CO"), eq(null), eq("en"), any()))
        .thenReturn(new PageResult<>(List.of(previewReading), 0, 8, 1));

    var diverseReading = createPlatformReading(UUID.randomUUID(), "Sherlock Holmes", null, null);

    // Candidates contain the exact preview reading + diverse reading
    when(readingRepository.findRecentPlatformReadings(eq("en"), eq(0), eq(20)))
        .thenReturn(List.of(previewReading, diverseReading));
    when(readingCollectionRepository.findAllActive()).thenReturn(List.of());

    when(personalizationService.personalizeReadings(any(), any(), anyList(), any()))
        .thenAnswer(
            inv -> {
              List<Reading> list = inv.getArgument(2);
              return list.stream().map(this::toRecommended).toList();
            });
    when(readingProgressRepository.findByUserIdAndReadingIds(any(), anySet()))
        .thenReturn(Collections.emptyMap());

    DiscoveryHomeResult result = useCase.getDiscoveryHome(new GetDiscoveryHomeQuery(10, 8, 8));

    assertThat(result.shelves()).hasSize(1);
    var newShelf = result.shelves().get(0);
    assertThat(newShelf.readings())
        .extracting(RecommendedPlatformReading::readingId)
        .containsExactly(diverseReading.id());
  }

  @Test
  @DisplayName(
      "4. Bounded paginated scan crosses real page boundary: page 0 redundant, page 1 discovers candidate 21")
  void testPaginationCrossesRealPageBoundary() {
    var region = new DiscoveryRegionDetails("latin-america", "Latinoamérica", "Historias");
    var coTopics =
        List.of(new DiscoveryTopicSummary("MYTHS_AND_LEGENDS", "Mitos y leyendas", 1, 20));
    var coCountry =
        new DiscoveryCountrySummary(
            "CO", "Colombia", "Tagline", "Desc", 1, 20, List.of(), coTopics);
    when(getDiscoveryRegionOverviewPort.getOverview("latin-america"))
        .thenReturn(new DiscoveryRegionOverviewResult(region, List.of(coCountry)));

    var previewReading =
        createPlatformReading(
            UUID.randomUUID(), "Mohán Preview", "CO", DiscoveryTopic.MYTHS_AND_LEGENDS);
    when(readingRepository.browsePlatformReadings(
            eq(null),
            eq(null),
            eq(null),
            eq("CO"),
            eq(DiscoveryTopic.MYTHS_AND_LEGENDS),
            eq("en"),
            any()))
        .thenReturn(new PageResult<>(List.of(previewReading), 0, 8, 1));

    // Page 0: 20 redundant CO myths
    List<Reading> page0 = new ArrayList<>();
    for (int i = 1; i <= 20; i++) {
      page0.add(
          createPlatformReading(
              UUID.randomUUID(), "CO Myth " + i, "CO", DiscoveryTopic.MYTHS_AND_LEGENDS));
    }
    when(readingRepository.findRecentPlatformReadings(eq("en"), eq(0), eq(20))).thenReturn(page0);

    // Page 1: candidate 21 is a diverse Mexican story
    var candidate21 =
        createPlatformReading(
            UUID.randomUUID(), "Mexican Revolution Hero", "MX", DiscoveryTopic.HISTORY_AND_MEMORY);
    when(readingRepository.findRecentPlatformReadings(eq("en"), eq(1), eq(20)))
        .thenReturn(List.of(candidate21));

    when(readingCollectionRepository.findAllActive()).thenReturn(List.of());

    when(personalizationService.personalizeReadings(any(), any(), anyList(), any()))
        .thenAnswer(
            inv -> {
              List<Reading> list = inv.getArgument(2);
              return list.stream().map(this::toRecommended).toList();
            });
    when(readingProgressRepository.findByUserIdAndReadingIds(any(), anySet()))
        .thenReturn(Collections.emptyMap());

    DiscoveryHomeResult result = useCase.getDiscoveryHome(new GetDiscoveryHomeQuery(10, 8, 8));

    // Verify page 0 and page 1 were both queried
    verify(readingRepository).findRecentPlatformReadings(eq("en"), eq(0), eq(20));
    verify(readingRepository).findRecentPlatformReadings(eq("en"), eq(1), eq(20));

    assertThat(result.shelves()).hasSize(1);
    assertThat(result.shelves().get(0).readings())
        .extracting(RecommendedPlatformReading::title)
        .containsExactly("Mexican Revolution Hero");
  }

  @Test
  @DisplayName(
      "5. Specialized collection suppression: colombian-myths omitted, generic collection preserved")
  void testSpecializedCollectionSuppression_AndGenericCollectionPreservation() {
    when(getDiscoveryRegionOverviewPort.getOverview("latin-america"))
        .thenReturn(
            new DiscoveryRegionOverviewResult(
                new DiscoveryRegionDetails("latin-america", "Latinoamérica", null), List.of()));

    when(readingRepository.findRecentPlatformReadings(any(), anyInt(), anyInt()))
        .thenReturn(List.of());

    var colMyths =
        new ReadingCollection(
            UUID.randomUUID(),
            "colombian-myths-legends",
            "Mitos de Colombia",
            "Desc",
            1,
            true,
            null);
    var classics =
        new ReadingCollection(
            UUID.randomUUID(), "classics", "Classic Literature", "Great classics", 2, true, null);

    when(readingCollectionRepository.findAllActive()).thenReturn(List.of(colMyths, classics));

    var classicReading = createPlatformReading(UUID.randomUUID(), "Moby Dick", null, null);
    when(readingCollectionRepository.findTopReadingsByCollectionIds(
            eq(List.of(classics.id())), eq("en"), eq(8)))
        .thenReturn(Map.of(classics.id(), List.of(classicReading)));

    when(personalizationService.personalizeReadings(any(), any(), anyList(), any()))
        .thenAnswer(
            inv -> {
              List<Reading> list = inv.getArgument(2);
              return list.stream().map(this::toRecommended).toList();
            });
    when(readingProgressRepository.findByUserIdAndReadingIds(any(), anySet()))
        .thenReturn(Collections.emptyMap());

    DiscoveryHomeResult result = useCase.getDiscoveryHome(new GetDiscoveryHomeQuery(10, 8, 8));

    assertThat(result.shelves()).hasSize(1);
    var shelf = result.shelves().get(0);
    assertThat(shelf.key()).isEqualTo("classics");
    assertThat(shelf.type()).isEqualTo("EDITORIAL");
    assertThat(shelf.readings())
        .extracting(RecommendedPlatformReading::title)
        .containsExactly("Moby Dick");

    // Verify batch query called with ONLY classics.id(), not colMyths.id()
    verify(readingCollectionRepository)
        .findTopReadingsByCollectionIds(eq(List.of(classics.id())), eq("en"), eq(8));
  }

  @Test
  @DisplayName("6. Empty generic shelves are omitted from response")
  void testEmptyGenericShelvesOmitted() {
    when(getDiscoveryRegionOverviewPort.getOverview("latin-america"))
        .thenReturn(
            new DiscoveryRegionOverviewResult(
                new DiscoveryRegionDetails("latin-america", "Latinoamérica", null), List.of()));
    when(readingRepository.findRecentPlatformReadings(any(), anyInt(), anyInt()))
        .thenReturn(List.of());

    var emptyCol =
        new ReadingCollection(
            UUID.randomUUID(), "empty-col", "Empty Collection", "Desc", 1, true, null);
    when(readingCollectionRepository.findAllActive()).thenReturn(List.of(emptyCol));
    when(readingCollectionRepository.findTopReadingsByCollectionIds(any(), any(), anyInt()))
        .thenReturn(Map.of(emptyCol.id(), List.of()));

    DiscoveryHomeResult result = useCase.getDiscoveryHome(new GetDiscoveryHomeQuery(10, 8, 8));
    assertThat(result.shelves()).isEmpty();
  }

  @Test
  @DisplayName("7. For You and Continue Reading overlaps are allowed")
  void testForYouAndContinueReadingOverlapAllowed() {
    UUID sharedReadingId = UUID.randomUUID();

    var continueItem =
        new ContinueReadingItem(
            sharedReadingId,
            "Shared Reading",
            ReadingOrigin.PLATFORM,
            ReadingProgressStatus.IN_PROGRESS,
            null,
            EditorialLevel.B1,
            "Adventure",
            LocalDateTime.now(),
            "Short desc",
            50);
    when(listContinueReadingPort.listContinueReading(any()))
        .thenReturn(new PageResult<>(List.of(continueItem), 0, 10, 1));

    var forYouReading =
        new RecommendedPlatformReading(
            sharedReadingId,
            "Shared Reading",
            "en",
            EditorialLevel.B1,
            "Adventure",
            LocalDateTime.now(),
            100,
            80,
            10,
            5,
            0,
            5,
            BigDecimal.valueOf(85),
            BigDecimal.valueOf(90),
            ReadingProgressStatus.IN_PROGRESS,
            null,
            RecommendationReasonCode.DISCOVERY,
            "Short desc",
            "CO",
            DiscoveryTopic.MYTHS_AND_LEGENDS);
    when(recommendPlatformReadingsPort.recommendPlatformReadings(any()))
        .thenReturn(new PageResult<>(List.of(forYouReading), 0, 8, 1));

    var region = new DiscoveryRegionDetails("latin-america", "Latinoamérica", null);
    var country =
        new DiscoveryCountrySummary("CO", "Colombia", "Tag", "Desc", 1, 1, List.of(), List.of());
    when(getDiscoveryRegionOverviewPort.getOverview("latin-america"))
        .thenReturn(new DiscoveryRegionOverviewResult(region, List.of(country)));

    var latamReading =
        createPlatformReading(
            sharedReadingId, "Shared Reading", "CO", DiscoveryTopic.MYTHS_AND_LEGENDS);
    when(readingRepository.browsePlatformReadings(
            any(), any(), any(), eq("CO"), any(), any(), any()))
        .thenReturn(new PageResult<>(List.of(latamReading), 0, 8, 1));

    when(readingRepository.findRecentPlatformReadings(any(), anyInt(), anyInt()))
        .thenReturn(List.of());
    when(readingCollectionRepository.findAllActive()).thenReturn(List.of());

    when(personalizationService.personalizeReadings(any(), any(), anyList(), any()))
        .thenAnswer(
            inv -> {
              List<Reading> list = inv.getArgument(2);
              return list.stream().map(this::toRecommended).toList();
            });
    when(readingProgressRepository.findByUserIdAndReadingIds(any(), anySet()))
        .thenReturn(Collections.emptyMap());

    DiscoveryHomeResult result = useCase.getDiscoveryHome(new GetDiscoveryHomeQuery(10, 8, 8));

    // Shared reading is present in continueReading, forYou, AND latinAmerica without deduplication
    assertThat(result.continueReading())
        .extracting(ContinueReadingItem::readingId)
        .containsExactly(sharedReadingId);
    assertThat(result.forYou())
        .extracting(RecommendedPlatformReading::readingId)
        .containsExactly(sharedReadingId);
    assertThat(result.latinAmerica().readings())
        .extracting(RecommendedPlatformReading::readingId)
        .containsExactly(sharedReadingId);
  }

  @Test
  @DisplayName("8. Query bounds clamping: clamps non-positive to defaults and oversized to caps")
  void testQueryBoundsClamping() {
    var q1 = new GetDiscoveryHomeQuery(-1, 0, 99);
    assertThat(q1.maxContinueReading()).isEqualTo(10);
    assertThat(q1.maxForYou()).isEqualTo(8);
    assertThat(q1.maxShelfReadings()).isEqualTo(12);

    var q2 = new GetDiscoveryHomeQuery(100, 100, 100);
    assertThat(q2.maxContinueReading()).isEqualTo(20);
    assertThat(q2.maxForYou()).isEqualTo(20);
    assertThat(q2.maxShelfReadings()).isEqualTo(12);
  }

  @Test
  @DisplayName(
      "9. Single-pass batch personalization: personalizeReadings is invoked once with all combined readings")
  void testSingleBatchPersonalization() {
    var region = new DiscoveryRegionDetails("latin-america", "Latinoamérica", null);
    var country =
        new DiscoveryCountrySummary("CO", "Colombia", "Tag", "Desc", 1, 1, List.of(), List.of());
    when(getDiscoveryRegionOverviewPort.getOverview("latin-america"))
        .thenReturn(new DiscoveryRegionOverviewResult(region, List.of(country)));

    var latamReading =
        createPlatformReading(UUID.randomUUID(), "Latam 1", "CO", DiscoveryTopic.MYTHS_AND_LEGENDS);
    when(readingRepository.browsePlatformReadings(
            any(), any(), any(), eq("CO"), any(), any(), any()))
        .thenReturn(new PageResult<>(List.of(latamReading), 0, 8, 1));

    var diverseNew =
        createPlatformReading(
            UUID.randomUUID(), "Diverse New 1", "MX", DiscoveryTopic.REAL_STORIES);
    when(readingRepository.findRecentPlatformReadings(any(), eq(0), eq(20)))
        .thenReturn(List.of(diverseNew));

    var genericCol =
        new ReadingCollection(UUID.randomUUID(), "gen-col", "Generic", "Desc", 1, true, null);
    when(readingCollectionRepository.findAllActive()).thenReturn(List.of(genericCol));

    var colReading = createPlatformReading(UUID.randomUUID(), "Col Reading 1", null, null);
    when(readingCollectionRepository.findTopReadingsByCollectionIds(any(), any(), anyInt()))
        .thenReturn(Map.of(genericCol.id(), List.of(colReading)));

    when(personalizationService.personalizeReadings(any(), any(), anyList(), any()))
        .thenAnswer(
            inv -> {
              List<Reading> list = inv.getArgument(2);
              return list.stream().map(this::toRecommended).toList();
            });
    when(readingProgressRepository.findByUserIdAndReadingIds(any(), anySet()))
        .thenReturn(Collections.emptyMap());

    useCase.getDiscoveryHome(new GetDiscoveryHomeQuery(10, 8, 8));

    // verify personalizeReadings called EXACTLY ONCE with 3 readings
    verify(personalizationService, times(1))
        .personalizeReadings(eq(userId), eq("en"), anyList(), any());
  }

  @Test
  @DisplayName(
      "10. Multi-country metadata: non-default countries (MX, PE) have NO readings queried during Home load")
  void testNonDefaultCountryReadingsNotLoadedOnHome() {
    var region = new DiscoveryRegionDetails("latin-america", "Latinoamérica", null);
    var co =
        new DiscoveryCountrySummary("CO", "Colombia", "Tag", "Desc", 1, 10, List.of(), List.of());
    var mx =
        new DiscoveryCountrySummary("MX", "México", "Tag", "Desc", 2, 10, List.of(), List.of());
    var pe = new DiscoveryCountrySummary("PE", "Perú", "Tag", "Desc", 3, 10, List.of(), List.of());

    when(getDiscoveryRegionOverviewPort.getOverview("latin-america"))
        .thenReturn(new DiscoveryRegionOverviewResult(region, List.of(co, mx, pe)));

    when(readingRepository.browsePlatformReadings(
            any(), any(), any(), eq("CO"), any(), any(), any()))
        .thenReturn(new PageResult<>(List.of(), 0, 8, 0));

    when(readingRepository.findRecentPlatformReadings(any(), anyInt(), anyInt()))
        .thenReturn(List.of());
    when(readingCollectionRepository.findAllActive()).thenReturn(List.of());

    DiscoveryHomeResult result = useCase.getDiscoveryHome(new GetDiscoveryHomeQuery(10, 8, 8));

    assertThat(result.latinAmerica().countries())
        .extracting(DiscoveryCountrySummary::countryCode)
        .containsExactly("CO", "MX", "PE");
    assertThat(result.latinAmerica().defaultCountryCode()).isEqualTo("CO");

    // browsePlatformReadings is called ONLY for default country CO
    verify(readingRepository)
        .browsePlatformReadings(any(), any(), any(), eq("CO"), any(), any(), any());
    verify(readingRepository, never())
        .browsePlatformReadings(any(), any(), any(), eq("MX"), any(), any(), any());
    verify(readingRepository, never())
        .browsePlatformReadings(any(), any(), any(), eq("PE"), any(), any(), any());
  }

  @Test
  @DisplayName(
      "11. Latin America initial topic: country with multiple topics uses first topic and does not mix topics")
  void testMultiTopicCountryInitialFilteredByDefaultTopicNoMixing() {
    var region = new DiscoveryRegionDetails("latin-america", "Latinoamérica", null);
    var topic1 = new DiscoveryTopicSummary("MYTHS_AND_LEGENDS", "Mitos y Leyendas", 1, 10);
    var topic2 = new DiscoveryTopicSummary("REAL_STORIES", "Historias Reales", 2, 5);
    var co =
        new DiscoveryCountrySummary(
            "CO", "Colombia", "Tag", "Desc", 1, 15, List.of(), List.of(topic1, topic2));

    when(getDiscoveryRegionOverviewPort.getOverview("latin-america"))
        .thenReturn(new DiscoveryRegionOverviewResult(region, List.of(co)));

    var reading1 =
        createPlatformReading(UUID.randomUUID(), "Mito 1", "CO", DiscoveryTopic.MYTHS_AND_LEGENDS);
    when(readingRepository.browsePlatformReadings(
            any(), any(), any(), eq("CO"), eq(DiscoveryTopic.MYTHS_AND_LEGENDS), any(), any()))
        .thenReturn(new PageResult<>(List.of(reading1), 0, 8, 1));

    when(readingRepository.findRecentPlatformReadings(any(), anyInt(), anyInt()))
        .thenReturn(List.of());
    when(readingCollectionRepository.findAllActive()).thenReturn(List.of());
    when(personalizationService.personalizeReadings(any(), any(), anyList(), any()))
        .thenAnswer(
            inv -> ((List<Reading>) inv.getArgument(2)).stream().map(this::toRecommended).toList());

    DiscoveryHomeResult result = useCase.getDiscoveryHome(new GetDiscoveryHomeQuery(10, 8, 8));

    assertThat(result.latinAmerica().defaultCountryCode()).isEqualTo("CO");
    assertThat(result.latinAmerica().defaultTopicKey()).isEqualTo("MYTHS_AND_LEGENDS");
    assertThat(result.latinAmerica().readings()).hasSize(1);
    assertThat(result.latinAmerica().readings().get(0).title()).isEqualTo("Mito 1");

    verify(readingRepository)
        .browsePlatformReadings(
            any(), any(), any(), eq("CO"), eq(DiscoveryTopic.MYTHS_AND_LEGENDS), any(), any());
    verify(readingRepository, never())
        .browsePlatformReadings(
            any(), any(), any(), eq("CO"), eq(DiscoveryTopic.REAL_STORIES), any(), any());
    verify(readingRepository, never())
        .browsePlatformReadings(any(), any(), any(), eq("CO"), isNull(), any(), any());
  }

  @Test
  @DisplayName(
      "12. Latin America initial topic: dynamic non-CO default country selects first country and first topic")
  void testDefaultCountryDynamicNonColombiaWithFirstTopic() {
    var region = new DiscoveryRegionDetails("latin-america", "Latinoamérica", null);
    var mxTopic = new DiscoveryTopicSummary("REAL_STORIES", "Historias Reales", 1, 8);
    var mx =
        new DiscoveryCountrySummary(
            "MX", "México", "Tag", "Desc", 1, 8, List.of(), List.of(mxTopic));
    var coTopic = new DiscoveryTopicSummary("MYTHS_AND_LEGENDS", "Mitos", 1, 12);
    var co =
        new DiscoveryCountrySummary(
            "CO", "Colombia", "Tag", "Desc", 2, 12, List.of(), List.of(coTopic));

    when(getDiscoveryRegionOverviewPort.getOverview("latin-america"))
        .thenReturn(new DiscoveryRegionOverviewResult(region, List.of(mx, co)));

    var mxReading =
        createPlatformReading(
            UUID.randomUUID(), "Historia MX 1", "MX", DiscoveryTopic.REAL_STORIES);
    when(readingRepository.browsePlatformReadings(
            any(), any(), any(), eq("MX"), eq(DiscoveryTopic.REAL_STORIES), any(), any()))
        .thenReturn(new PageResult<>(List.of(mxReading), 0, 8, 1));

    when(readingRepository.findRecentPlatformReadings(any(), anyInt(), anyInt()))
        .thenReturn(List.of());
    when(readingCollectionRepository.findAllActive()).thenReturn(List.of());
    when(personalizationService.personalizeReadings(any(), any(), anyList(), any()))
        .thenAnswer(
            inv -> ((List<Reading>) inv.getArgument(2)).stream().map(this::toRecommended).toList());

    DiscoveryHomeResult result = useCase.getDiscoveryHome(new GetDiscoveryHomeQuery(10, 8, 8));

    assertThat(result.latinAmerica().defaultCountryCode()).isEqualTo("MX");
    assertThat(result.latinAmerica().defaultTopicKey()).isEqualTo("REAL_STORIES");
    assertThat(result.latinAmerica().readings()).hasSize(1);
    assertThat(result.latinAmerica().readings().get(0).title()).isEqualTo("Historia MX 1");

    verify(readingRepository)
        .browsePlatformReadings(
            any(), any(), any(), eq("MX"), eq(DiscoveryTopic.REAL_STORIES), any(), any());
    verify(readingRepository, never())
        .browsePlatformReadings(any(), any(), any(), eq("CO"), any(), any(), any());
  }

  @Test
  @DisplayName(
      "13. Multi-country specialized exclusion: readings from active regional countries (CO, MX) excluded from New and generic shelves")
  void testMultiCountrySpecializedReadingsExcludedFromNewShelfAndGenericShelves() {
    var region = new DiscoveryRegionDetails("latin-america", "Latinoamérica", null);
    var coTopic = new DiscoveryTopicSummary("MYTHS_AND_LEGENDS", "Mitos", 1, 1);
    var co =
        new DiscoveryCountrySummary(
            "CO", "Colombia", "Tag", "Desc", 1, 1, List.of(), List.of(coTopic));
    var mxTopic = new DiscoveryTopicSummary("REAL_STORIES", "Historias", 2, 1);
    var mx =
        new DiscoveryCountrySummary(
            "MX", "México", "Tag", "Desc", 2, 1, List.of(), List.of(mxTopic));

    when(getDiscoveryRegionOverviewPort.getOverview("latin-america"))
        .thenReturn(new DiscoveryRegionOverviewResult(region, List.of(co, mx)));

    var coReading =
        createPlatformReading(UUID.randomUUID(), "Mito CO", "CO", DiscoveryTopic.MYTHS_AND_LEGENDS);
    var mxReading =
        createPlatformReading(UUID.randomUUID(), "Historia MX", "MX", DiscoveryTopic.REAL_STORIES);
    var globalReading = createPlatformReading(UUID.randomUUID(), "Classic Tale", null, null);

    when(readingRepository.findPublishedPlatformReadingIdsByCountryCodes(List.of("CO", "MX")))
        .thenReturn(Set.of(coReading.id(), mxReading.id()));

    when(readingRepository.browsePlatformReadings(
            any(), any(), any(), eq("CO"), eq(DiscoveryTopic.MYTHS_AND_LEGENDS), any(), any()))
        .thenReturn(new PageResult<>(List.of(coReading), 0, 8, 1));

    // Candidate list contains CO, MX, and global
    when(readingRepository.findRecentPlatformReadings(eq("en"), eq(0), eq(20)))
        .thenReturn(List.of(coReading, mxReading, globalReading));

    // Generic collection contains MX reading and global reading
    var genericCol =
        new ReadingCollection(
            UUID.randomUUID(), "world-stories", "World Stories", "Desc", 1, true, null);
    when(readingCollectionRepository.findAllActive()).thenReturn(List.of(genericCol));
    when(readingCollectionRepository.findTopReadingsByCollectionIds(
            eq(List.of(genericCol.id())), eq("en"), eq(8)))
        .thenReturn(Map.of(genericCol.id(), List.of(mxReading, globalReading)));

    when(personalizationService.personalizeReadings(any(), any(), anyList(), any()))
        .thenAnswer(
            inv -> ((List<Reading>) inv.getArgument(2)).stream().map(this::toRecommended).toList());
    when(readingProgressRepository.findByUserIdAndReadingIds(any(), anySet()))
        .thenReturn(Collections.emptyMap());

    DiscoveryHomeResult result = useCase.getDiscoveryHome(new GetDiscoveryHomeQuery(10, 8, 8));

    // Latin America block preview contains CO reading
    assertThat(result.latinAmerica().readings())
        .extracting(RecommendedPlatformReading::title)
        .containsExactly("Mito CO");

    // Dynamic New shelf contains ONLY globalReading; CO and MX readings are excluded
    var newShelf = result.shelves().stream().filter(s -> "new".equals(s.key())).findFirst();
    assertThat(newShelf).isPresent();
    assertThat(newShelf.get().readings())
        .extracting(RecommendedPlatformReading::title)
        .containsExactly("Classic Tale");

    // Generic shelf contains ONLY globalReading; MX reading is excluded
    var genericShelf =
        result.shelves().stream().filter(s -> "world-stories".equals(s.key())).findFirst();
    assertThat(genericShelf).isPresent();
    assertThat(genericShelf.get().readings())
        .extracting(RecommendedPlatformReading::title)
        .containsExactly("Classic Tale");
  }

  private Reading createPlatformReading(
      UUID id, String title, String countryCode, DiscoveryTopic topic) {
    return new Reading(
        id,
        null,
        title,
        "Content of " + title,
        LanguageTag.of("en"),
        LocalDateTime.now(),
        ReadingOrigin.PLATFORM,
        EditorialLevel.A1,
        "Fiction",
        null,
        EditorialStatus.PUBLISHED,
        "Short desc",
        EditorialContentType.FICTION,
        countryCode,
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
        AccessTier.FREE,
        topic);
  }

  private RecommendedPlatformReading toRecommended(Reading reading) {
    return new RecommendedPlatformReading(
        reading.id(),
        reading.title(),
        "en",
        reading.editorialLevel(),
        reading.category(),
        reading.createdAt(),
        100,
        80,
        10,
        5,
        0,
        5,
        BigDecimal.valueOf(85),
        BigDecimal.valueOf(90),
        ReadingProgressStatus.IN_PROGRESS,
        reading.coverKey(),
        RecommendationReasonCode.DISCOVERY,
        reading.shortDescription(),
        reading.countryCode(),
        reading.discoveryTopic());
  }
}
