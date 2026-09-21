package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.ContinueReadingItem;
import com.soap.soap.application.model.DiscoveryCountrySummary;
import com.soap.soap.application.model.DiscoveryHomeResult;
import com.soap.soap.application.model.DiscoveryRegionOverviewResult;
import com.soap.soap.application.model.DiscoveryShelfResult;
import com.soap.soap.application.model.GetDiscoveryHomeQuery;
import com.soap.soap.application.model.LatinAmericaDiscoveryResult;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.port.in.GetDiscoveryHomePort;
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
import com.soap.soap.domain.model.DiscoveryTopic;
import com.soap.soap.domain.model.LanguageTag;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingCollection;
import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.domain.model.ReadingProgressStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetDiscoveryHomeUseCase implements GetDiscoveryHomePort {

  public static final int NEW_SHELF_PAGE_SIZE = 20;
  public static final int NEW_SHELF_HARD_CAP = 100;
  public static final int NEW_SHELF_MAX_DB_CALLS = NEW_SHELF_HARD_CAP / NEW_SHELF_PAGE_SIZE;

  private final UserRepositoryPort users;
  private final CurrentUserPort currentUser;
  private final ListContinueReadingPort listContinueReadingPort;
  private final RecommendPlatformReadingsPort recommendPlatformReadingsPort;
  private final GetDiscoveryRegionOverviewPort getDiscoveryRegionOverviewPort;
  private final ReadingRepositoryPort readingRepository;
  private final ReadingCollectionRepositoryPort readingCollectionRepository;
  private final ReadingProgressRepositoryPort readingProgressRepository;
  private final PlatformReadingPersonalizationService personalizationService;
  private final SpecializedDiscoveryBlockRegistry specializedRegistry;

  @Override
  @Transactional(readOnly = true)
  public DiscoveryHomeResult getDiscoveryHome(GetDiscoveryHomeQuery query) {
    if (query == null) {
      query = new GetDiscoveryHomeQuery(10, 8, 8);
    }

    UUID userId = currentUser.requireUserId();
    var user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    String learningLanguage =
        user.learningLanguage() != null && !user.learningLanguage().isBlank()
            ? LanguageTag.of(user.learningLanguage()).value()
            : "en";

    // 1. Continue Reading (utility surface, independent)
    List<ContinueReadingItem> continueReading =
        listContinueReadingPort
            .listContinueReading(new PageRequest(0, query.maxContinueReading()))
            .content();

    // 2. For You (personalized Recommendation V2, independent)
    List<RecommendedPlatformReading> forYou =
        recommendPlatformReadingsPort
            .recommendPlatformReadings(new PageRequest(0, query.maxForYou()))
            .content();

    // 3. Latin America Discovery Block
    DiscoveryRegionOverviewResult regionOverview =
        getDiscoveryRegionOverviewPort.getOverview("latin-america");
    List<DiscoveryCountrySummary> countries =
        regionOverview != null ? regionOverview.countries() : List.of();

    LatinAmericaDiscoveryResult latinAmericaResult = null;
    List<Reading> latamPreviewReadings = List.of();
    Set<UUID> latamPreviewReadingIds = Collections.emptySet();
    String defaultCountryCode = null;
    String defaultTopicKey = null;
    Set<DiscoveryTopic> dominantTopics = Collections.emptySet();

    if (countries != null && !countries.isEmpty()) {
      DiscoveryCountrySummary defaultCountry = countries.get(0);
      defaultCountryCode = defaultCountry.countryCode();

      DiscoveryTopic initialFilterTopic = null;
      if (defaultCountry.topics() != null && !defaultCountry.topics().isEmpty()) {
        defaultTopicKey = defaultCountry.topics().get(0).key();
        try {
          initialFilterTopic = DiscoveryTopic.fromString(defaultTopicKey);
        } catch (Exception ignored) {
        }
      }

      if (defaultCountry.topics() != null) {
        dominantTopics =
            defaultCountry.topics().stream()
                .map(
                    t -> {
                      try {
                        return DiscoveryTopic.fromString(t.key());
                      } catch (Exception e) {
                        return null;
                      }
                    })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
      }

      PageResult<Reading> latamPage =
          readingRepository.browsePlatformReadings(
              null,
              null,
              null,
              defaultCountryCode,
              initialFilterTopic,
              learningLanguage,
              new PageRequest(0, query.maxShelfReadings()));
      latamPreviewReadings = latamPage != null ? latamPage.content() : List.of();
      latamPreviewReadingIds =
          latamPreviewReadings.stream().map(Reading::id).collect(Collectors.toSet());
    }

    // 4. Dynamic "Nuevas lecturas" candidate scan using bounded paginated scan
    List<Reading> diverseNewCandidates = new ArrayList<>();
    for (int page = 0;
        page < NEW_SHELF_MAX_DB_CALLS && diverseNewCandidates.size() < query.maxShelfReadings();
        page++) {
      List<Reading> pageCandidates =
          readingRepository.findRecentPlatformReadings(learningLanguage, page, NEW_SHELF_PAGE_SIZE);
      if (pageCandidates == null || pageCandidates.isEmpty()) {
        break;
      }
      for (Reading candidate : pageCandidates) {
        if (latamPreviewReadingIds.contains(candidate.id())) {
          continue;
        }

        boolean isDominantCountry =
            defaultCountryCode != null
                && defaultCountryCode.equalsIgnoreCase(candidate.countryCode());
        boolean isDominantTopic =
            candidate.discoveryTopic() != null
                && dominantTopics.contains(candidate.discoveryTopic());

        if (isDominantCountry && isDominantTopic) {
          continue;
        }

        diverseNewCandidates.add(candidate);
        if (diverseNewCandidates.size() >= query.maxShelfReadings()) {
          break;
        }
      }
    }

    // 5. Generic editorial collections (filtered before batch query)
    List<ReadingCollection> activeCollections = readingCollectionRepository.findAllActive();
    Set<String> specializedKeys = specializedRegistry.getAllSpecializedCollectionKeys();
    List<ReadingCollection> genericCollections =
        activeCollections.stream()
            .filter(c -> !specializedKeys.contains(c.key()))
            .sorted(
                Comparator.comparingInt(ReadingCollection::displayOrder)
                    .thenComparing(ReadingCollection::key))
            .toList();

    Map<UUID, List<Reading>> topReadingsByCollection = Collections.emptyMap();
    if (!genericCollections.isEmpty()) {
      List<UUID> genericIds = genericCollections.stream().map(ReadingCollection::id).toList();
      topReadingsByCollection =
          readingCollectionRepository.findTopReadingsByCollectionIds(
              genericIds, learningLanguage, query.maxShelfReadings());
    }

    // 6. Single-pass batch personalization
    List<Reading> allReadingsToPersonalize = new ArrayList<>();
    allReadingsToPersonalize.addAll(latamPreviewReadings);
    allReadingsToPersonalize.addAll(diverseNewCandidates);
    for (ReadingCollection col : genericCollections) {
      List<Reading> colReadings =
          topReadingsByCollection.getOrDefault(col.id(), Collections.emptyList());
      allReadingsToPersonalize.addAll(colReadings);
    }

    Map<UUID, RecommendedPlatformReading> personalizedMap = Collections.emptyMap();
    if (!allReadingsToPersonalize.isEmpty()) {
      Set<UUID> allIds =
          allReadingsToPersonalize.stream().map(Reading::id).collect(Collectors.toSet());
      Map<UUID, ReadingProgress> progressMap =
          readingProgressRepository.findByUserIdAndReadingIds(userId, allIds);
      Map<UUID, ReadingProgressStatus> progressStatusMap =
          progressMap.entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().status()));

      List<RecommendedPlatformReading> personalizedList =
          personalizationService.personalizeReadings(
              userId, learningLanguage, allReadingsToPersonalize, progressStatusMap);

      personalizedMap =
          personalizedList.stream()
              .collect(
                  Collectors.toMap(
                      RecommendedPlatformReading::readingId, r -> r, (first, duplicate) -> first));
    }

    // 7. Build Latin America Result
    if (countries != null && !countries.isEmpty()) {
      final Map<UUID, RecommendedPlatformReading> pMap = personalizedMap;
      List<RecommendedPlatformReading> latamPersonalized =
          latamPreviewReadings.stream()
              .map(r -> pMap.get(r.id()))
              .filter(Objects::nonNull)
              .toList();

      latinAmericaResult =
          new LatinAmericaDiscoveryResult(
              regionOverview.region(),
              countries,
              defaultCountryCode,
              defaultTopicKey,
              latamPersonalized);
    }

    // 8. Build Shelves
    List<DiscoveryShelfResult> shelves = new ArrayList<>();

    // Dynamic "Nuevas lecturas" shelf (displayOrder = 0)
    if (!diverseNewCandidates.isEmpty()) {
      final Map<UUID, RecommendedPlatformReading> pMap = personalizedMap;
      List<RecommendedPlatformReading> newPersonalized =
          diverseNewCandidates.stream()
              .map(r -> pMap.get(r.id()))
              .filter(Objects::nonNull)
              .toList();
      if (!newPersonalized.isEmpty()) {
        shelves.add(
            new DiscoveryShelfResult(
                "new",
                "Nuevas lecturas",
                null,
                0,
                null,
                "DYNAMIC",
                newPersonalized.size(),
                newPersonalized));
      }
    }

    // Generic editorial shelves
    for (ReadingCollection col : genericCollections) {
      List<Reading> colReadings =
          topReadingsByCollection.getOrDefault(col.id(), Collections.emptyList());
      final Map<UUID, RecommendedPlatformReading> pMap = personalizedMap;
      List<RecommendedPlatformReading> shelfPersonalized =
          colReadings.stream().map(r -> pMap.get(r.id())).filter(Objects::nonNull).toList();

      if (!shelfPersonalized.isEmpty()) {
        shelves.add(
            new DiscoveryShelfResult(
                col.key(),
                col.displayName(),
                col.description(),
                col.displayOrder(),
                col.coverKey(),
                "EDITORIAL",
                shelfPersonalized.size(),
                shelfPersonalized));
      }
    }

    return new DiscoveryHomeResult(continueReading, forYou, latinAmericaResult, shelves);
  }
}
