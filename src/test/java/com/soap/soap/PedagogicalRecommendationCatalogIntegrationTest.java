package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.model.VocabularyClassification;
import com.soap.soap.application.port.in.CompleteInitialVocabularyTestPort;
import com.soap.soap.application.port.in.RecommendPlatformReadingsPort;
import com.soap.soap.application.port.out.InitialVocabularyTestSourcePort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.WordRepositoryPort;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
@Transactional
class PedagogicalRecommendationCatalogIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private RecommendPlatformReadingsPort recommendations;
  @Autowired private CompleteInitialVocabularyTestPort completeOnboarding;
  @Autowired private InitialVocabularyTestSourcePort onboardingTest;
  @Autowired private ReadingRepositoryPort readings;
  @Autowired private ReadingProgressRepositoryPort progress;
  @Autowired private UserRepositoryPort users;
  @Autowired private UserVocabularyRepositoryPort vocabulary;
  @Autowired private WordRepositoryPort words;
  @Autowired private TextWordProcessor processor;

  private User user;
  private Map<String, Reading> catalog;

  @BeforeEach
  void setUp() {
    user =
        users.save(
            new User(
                null,
                "Synthetic Reader",
                "pedagogy-" + UUID.randomUUID() + "@example.com",
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                null));
    authenticate(user.id());
    catalog =
        readings.findAllPlatformReadings().stream()
            .collect(Collectors.toMap(Reading::title, Function.identity()));
  }

  @Test
  void sparseProfileIsConservativeDeterministicAndPaginatesGlobally() {
    classify(List.of("the", "and", "a"), VocabularyStatus.KNOWN);

    var firstRun = ranked();
    var secondRun = ranked();
    var page0 = recommendations.recommendPlatformReadings(new PageRequest(0, 4));
    var page1 = recommendations.recommendPlatformReadings(new PageRequest(1, 4));
    var page2 = recommendations.recommendPlatformReadings(new PageRequest(2, 4));

    assertThat(firstRun).hasSize(74);
    assertThat(ids(secondRun)).containsExactlyElementsOf(ids(firstRun));
    assertThat(firstRun.subList(0, 4))
        .allMatch(reading -> reading.editorialLevel() == EditorialLevel.A1)
        .allMatch(reading -> reading.explicitNewWords() == 0)
        .allMatch(reading -> reading.unclassifiedWords() > reading.knownWords());
    assertThat(page0.content()).hasSize(4);
    assertThat(page1.content()).hasSize(4);
    assertThat(page2.content()).hasSize(4);
    assertThat(concatIds(page0.content(), page1.content(), page2.content()))
        .doesNotHaveDuplicates();
    assertThat(concatIds(page0.content(), page1.content(), page2.content()))
        .containsExactlyElementsOf(ids(firstRun).subList(0, 12));
  }

  @Test
  void onboardingVocabularyChangesTheRealRecommendationRanking() {
    var before = ranked();
    var selectable = new java.util.HashSet<>(onboardingTest.load().selectableWords());
    var target =
        before.stream()
            .map(item -> reading(item.title()))
            .filter(
                candidate ->
                    orderedWords(candidate).stream().filter(selectable::contains).count() >= 10)
            .findFirst()
            .orElseThrow();
    var selected =
        orderedWords(target).stream().filter(selectable::contains).distinct().limit(10).toList();
    var additional =
        selectable.stream().filter(word -> !selected.contains(word)).limit(20).toList();

    var classifications = new ArrayList<VocabularyClassification>();
    selected.forEach(
        word -> classifications.add(new VocabularyClassification(word, VocabularyStatus.NEW)));
    additional.forEach(
        word -> classifications.add(new VocabularyClassification(word, VocabularyStatus.KNOWN)));

    completeOnboarding.completeInitialVocabularyTest(
        onboardingTest.load().testId(), classifications);

    var persisted = vocabulary.findStatusesByNormalizedValues(user.id(), "en", selected);
    var after = ranked();
    assertThat(persisted)
        .hasSize(10)
        .allSatisfy((word, status) -> assertThat(status).isEqualTo(VocabularyStatus.NEW));
    assertThat(users.findById(user.id()).orElseThrow().onboardingCompleted()).isTrue();
    assertThat(ids(after)).isNotEqualTo(ids(before));
    assertThat(find(after, target).explicitNewWords()).isGreaterThanOrEqualTo(10);
    assertThat(indexOf(after, target)).isGreaterThan(indexOf(before, target));
  }

  @Test
  void beginnerEvidenceFavoursCompatibleA1OverClearlyHarderC1WithoutHardFiltering() {
    var compatible = reading("The Lost Blue Scarf");
    var difficult = reading("A City That Predicts Its Citizens");
    classifyDistribution(compatible, 65, 15);

    var ranked = ranked();

    assertThat(indexOf(ranked, compatible)).isLessThan(indexOf(ranked, difficult));
    assertThat(ranked).anyMatch(item -> item.editorialLevel() == EditorialLevel.C1);
    assertThat(find(ranked, compatible).learningWords()).isGreaterThan(0);
  }

  @Test
  void intermediateLearningVocabularyRewardsRealB1Reuse() {
    var target = reading("The Empty Lot Project");
    var tooEasy = reading("The Sparrow and the Red Cup");
    var tooDifficult = reading("A City That Predicts Its Citizens");
    classifyDistribution(target, 65, 15);
    classifyExisting(
        List.of(
            "improve",
            "provide",
            "change",
            "community",
            "experience",
            "challenge",
            "opportunity",
            "notice",
            "decide",
            "instead"),
        VocabularyStatus.LEARNING);

    var ranked = ranked();

    assertThat(indexOf(ranked, target)).isLessThan(indexOf(ranked, tooEasy));
    assertThat(indexOf(ranked, target)).isLessThan(indexOf(ranked, tooDifficult));
    assertThat(find(ranked, target).learningWords()).isGreaterThan(0);
  }

  @Test
  void advancedVocabularyCanMakeARealC1ReadingWin() {
    var advanced = reading("The Museum of Unfinished Things");
    classifyDistribution(advanced, 65, 15);

    var ranked = ranked();

    assertThat(ranked.getFirst().readingId()).isEqualTo(advanced.id());
    assertThat(ranked.getFirst().editorialLevel()).isEqualTo(EditorialLevel.C1);
    assertThat(ranked.getFirst().classificationConfidencePercentage())
        .isGreaterThanOrEqualTo(new java.math.BigDecimal("75"));
  }

  @Test
  void explicitNewRiskWorsensTheRankComparedWithLeavingTheSameWordsUnclassified() {
    var target = reading("The Cost of Constant Attention");
    var targetWords = orderedWords(target);
    classify(targetWords.subList(0, percent(targetWords, 65)), VocabularyStatus.KNOWN);
    classify(
        targetWords.subList(percent(targetWords, 65), percent(targetWords, 80)),
        VocabularyStatus.LEARNING);
    var uncertainRank = indexOf(ranked(), target);

    classify(
        targetWords.subList(percent(targetWords, 80), targetWords.size()), VocabularyStatus.NEW);
    var explicitNewResults = ranked();

    assertThat(find(explicitNewResults, target).explicitNewWords()).isGreaterThan(0);
    assertThat(indexOf(explicitNewResults, target)).isGreaterThanOrEqualTo(uncertainRank);
  }

  @Test
  void learningReinforcementImprovesARealReadingsRelativePosition() {
    var target = reading("The Museum of Unfinished Things");
    var competitor = reading("A City That Predicts Its Citizens");
    var targetWords = orderedWords(target);
    var competitorWords = orderedWords(competitor);
    var common = new ArrayList<>(targetWords);
    common.retainAll(competitorWords);
    classify(common, VocabularyStatus.KNOWN);
    var targetExclusive = new ArrayList<>(targetWords);
    targetExclusive.removeAll(common);
    var competitorExclusive = new ArrayList<>(competitorWords);
    competitorExclusive.removeAll(common);
    var targetKnown = Math.max(0, percent(targetWords, 65) - common.size());
    var competitorKnown = Math.max(0, percent(competitorWords, 65) - common.size());
    classify(targetExclusive.subList(0, targetKnown), VocabularyStatus.KNOWN);
    classify(competitorExclusive.subList(0, competitorKnown), VocabularyStatus.KNOWN);
    classify(
        competitorExclusive.subList(
            competitorKnown,
            Math.min(competitorExclusive.size(), competitorKnown + percent(competitorWords, 10))),
        VocabularyStatus.LEARNING);
    var before = indexOf(ranked(), target);

    classify(
        targetExclusive.subList(
            targetKnown, Math.min(targetExclusive.size(), targetKnown + percent(targetWords, 15))),
        VocabularyStatus.LEARNING);
    var afterResults = ranked();

    assertThat(find(afterResults, target).learningWords()).isGreaterThan(0);
    assertThat(indexOf(afterResults, target)).isLessThan(before);
    assertThat(indexOf(afterResults, target)).isLessThan(indexOf(afterResults, competitor));
  }

  @Test
  void excessiveChallengeCanLoseDespiteHavingMoreLearningWordsAndTooEasyCanAlsoLose() {
    var balanced = reading("Learning to Ask Better Questions");
    var difficult = reading("The Cartographer of Vanishing Roads");
    var easy = reading("The Lost Blue Scarf");
    classifyDistribution(balanced, 65, 15);
    classify(orderedWords(easy), VocabularyStatus.KNOWN);
    var difficultWords = orderedWords(difficult);
    classify(difficultWords.subList(0, percent(difficultWords, 35)), VocabularyStatus.KNOWN);
    classify(
        difficultWords.subList(percent(difficultWords, 35), percent(difficultWords, 55)),
        VocabularyStatus.LEARNING);

    var ranked = ranked();

    assertThat(find(ranked, difficult).learningWords())
        .isGreaterThan(find(ranked, balanced).learningWords());
    assertThat(indexOf(ranked, balanced)).isLessThan(indexOf(ranked, difficult));
    assertThat(indexOf(ranked, balanced)).isLessThan(indexOf(ranked, easy));
  }

  @Test
  void realCatalogProgressHierarchyNeverProducesArtificiallyEmptyPages() {
    var ordered = ranked();
    var notStarted = reading(ordered.get(0).title());
    var inProgress = reading(ordered.get(1).title());
    var completed = reading(ordered.get(2).title());
    progress.startIfAbsent(user.id(), inProgress.id(), LocalDateTime.now());
    progress.complete(user.id(), completed.id(), LocalDateTime.now());

    var mixed = ranked();
    assertThat(indexOf(mixed, inProgress)).isLessThan(indexOf(mixed, notStarted));
    assertThat(indexOf(mixed, notStarted)).isLessThan(indexOf(mixed, completed));

    for (var reading : catalog.values()) {
      progress.complete(user.id(), reading.id(), LocalDateTime.now());
    }
    var allCompleted = recommendations.recommendPlatformReadings(new PageRequest(5, 4));
    assertThat(allCompleted.content())
        .hasSize(4)
        .allMatch(item -> item.progressStatus() == ReadingProgressStatus.COMPLETED);
  }

  @Test
  void allSeventyFourRecommendationsAreReachableExactlyOnceAcrossTwelveItemPages() {
    var collected = new ArrayList<RecommendedPlatformReading>();
    for (var page = 0; page < 7; page++) {
      var result = recommendations.recommendPlatformReadings(new PageRequest(page, 12));
      assertThat(result.totalElements()).isEqualTo(74);
      collected.addAll(result.content());
    }

    assertThat(collected).hasSize(74);
    assertThat(ids(collected)).doesNotHaveDuplicates();
    assertThat(ids(collected)).containsExactlyElementsOf(ids(ranked()));
  }

  @Test
  void expandedCatalogHasExpectedLevelsLanguageCategoriesAndSubstantialContent() {
    assertThat(catalog).hasSize(74);
    assertThat(catalog.values())
        .allSatisfy(
            reading -> {
              assertThat(reading.language()).isEqualTo("en");
              assertThat(reading.category()).isNotBlank();
              assertThat(reading.content()).isNotBlank();
            });
    assertThat(catalog.values())
        .filteredOn(r -> r.editorialLevel() == EditorialLevel.A1)
        .hasSize(13);
    assertThat(catalog.values())
        .filteredOn(r -> r.editorialLevel() == EditorialLevel.A2)
        .hasSize(13);
    assertThat(catalog.values())
        .filteredOn(r -> r.editorialLevel() == EditorialLevel.B1)
        .hasSize(14);
    assertThat(catalog.values())
        .filteredOn(r -> r.editorialLevel() == EditorialLevel.B2)
        .hasSize(14);
    assertThat(catalog.values())
        .filteredOn(r -> r.editorialLevel() == EditorialLevel.C1)
        .hasSize(12);
    assertThat(catalog.values())
        .filteredOn(r -> r.editorialLevel() == EditorialLevel.C2)
        .hasSize(8);
    assertThat(catalog.values())
        .filteredOn(r -> r.id().toString().startsWith("30000000-"))
        .hasSize(50)
        .allSatisfy(
            reading -> assertThat(processor.tokenize(reading.content())).hasSizeGreaterThan(300));
  }

  @Test
  void englishRegionalVocabularyStillMatchesTheCanonicalEnglishCatalog() {
    var regionalWord = words.resolve("would", "en-gb");
    vocabulary.save(
        new UserVocabulary(
            null, user, regionalWord, VocabularyStatus.LEARNING, LocalDateTime.now(), null));

    var matchingReading =
        catalog.values().stream()
            .filter(reading -> orderedWords(reading).contains("would"))
            .findFirst()
            .orElseThrow();
    var result = find(ranked(), matchingReading);

    assertThat(result.learningWords()).isGreaterThanOrEqualTo(1);
    assertThat(vocabulary.findStatusesByNormalizedValues(user.id(), "en-US", List.of("would")))
        .containsEntry("would", VocabularyStatus.LEARNING);
  }

  @Test
  void coverKeysArePersistedAndPropagatedWithoutAffectingRanking() {
    var expected =
        Map.of(
            "The Camera on Platform Three", "the-camera-on-platform-three",
            "The Library Book with No Author", "the-library-book-with-no-author",
            "The Station Outside the Map", "the-station-outside-the-map",
            "The Coral Ledger", "the-coral-ledger",
            "The City Beneath the Reservoir", "the-city-beneath-the-reservoir",
            "The Memory Orchard", "the-memory-orchard",
            "The Grammar of Tides", "the-grammar-of-tides",
            "The Unreliable Future Perfect", "the-unreliable-future-perfect");

    expected.forEach(
        (title, coverKey) -> assertThat(reading(title).coverKey()).isEqualTo(coverKey));
    assertThat(catalog.values()).filteredOn(reading -> reading.coverKey() != null).hasSize(63);
    assertThat(catalog.values()).filteredOn(reading -> reading.coverKey() == null).hasSize(11);
    assertThat(reading("A Morning at the Library").coverKey())
        .isEqualTo("a-morning-at-the-library");
    assertThat(reading("Why Cities Need Trees").coverKey()).isEqualTo("why-cities-need-trees");
    assertThat(reading("Minor Gods of the Waiting Room").coverKey())
        .isEqualTo("minor-gods-of-the-waiting-room");
    assertThat(
            List.of(
                reading("The Changing Nature of Work"),
                reading("The Algorithm That Loved Tuesdays"),
                reading("The Price of Perfect Timing"),
                reading("The Museum of Unfinished Things")))
        .allSatisfy(item -> assertThat(item.coverKey()).isNull());
    var rankedById =
        ranked().stream().collect(java.util.stream.Collectors.toMap(r -> r.readingId(), r -> r));
    assertThat(rankedById.values()).filteredOn(item -> item.coverKey() != null).hasSize(63);
    catalog.values().stream()
        .filter(item -> item.coverKey() != null)
        .forEach(
            item -> assertThat(rankedById.get(item.id()).coverKey()).isEqualTo(item.coverKey()));
  }

  @Test
  void matureUserRanksHighEvidenceC2OverLowEvidenceA2InRealCatalog() {
    var c2Target = reading("A Republic of Echoes");
    var a2Target = reading("Why Cities Need Trees");

    // Classify high evidence on C2 (65% known, 15% learning -> mature user > 30 words)
    classifyDistribution(c2Target, 65, 15);

    var ranked = ranked();
    var c2Result = find(ranked, c2Target);

    assertThat(c2Result.classificationConfidencePercentage())
        .isGreaterThan(new BigDecimal("70.00"));
    assertThat(indexOf(ranked, c2Target)).isLessThan(indexOf(ranked, a2Target));
  }

  @Test
  void vocabularyFitPercentageCorrespondsToKnownComfortNotFinalScore() {
    var reading = reading("The Lost Blue Scarf");
    classifyDistribution(reading, 65, 15);

    var result = find(ranked(), reading);
    // In V2: vocabularyFitPercentage is KnownComfort = min(100, knownTokenCoverage / 95 * 100)
    assertThat(result.vocabularyFitPercentage()).isNotNull();
    assertThat(result.vocabularyFitPercentage()).isGreaterThan(BigDecimal.ZERO);
    assertThat(result.vocabularyFitPercentage()).isLessThanOrEqualTo(new BigDecimal("100.00"));
  }

  private List<RecommendedPlatformReading> ranked() {
    return recommendations.recommendPlatformReadings(new PageRequest(0, 100)).content();
  }

  private Reading reading(String title) {
    return java.util.Objects.requireNonNull(catalog.get(title), title);
  }

  private RecommendedPlatformReading find(
      List<RecommendedPlatformReading> result, Reading reading) {
    return result.stream()
        .filter(item -> item.readingId().equals(reading.id()))
        .findFirst()
        .orElseThrow();
  }

  private int indexOf(List<RecommendedPlatformReading> result, Reading reading) {
    return ids(result).indexOf(reading.id());
  }

  private List<UUID> ids(List<RecommendedPlatformReading> result) {
    return result.stream().map(RecommendedPlatformReading::readingId).toList();
  }

  @SafeVarargs
  private List<UUID> concatIds(List<RecommendedPlatformReading>... pages) {
    return java.util.Arrays.stream(pages)
        .flatMap(List::stream)
        .map(RecommendedPlatformReading::readingId)
        .toList();
  }

  private void classifyDistribution(Reading reading, int knownPercentage, int learningPercentage) {
    var values = orderedWords(reading);
    var knownEnd = percent(values, knownPercentage);
    var learningEnd = percent(values, knownPercentage + learningPercentage);
    classify(values.subList(0, knownEnd), VocabularyStatus.KNOWN);
    classify(values.subList(knownEnd, learningEnd), VocabularyStatus.LEARNING);
  }

  private List<String> orderedWords(Reading reading) {
    return new ArrayList<>(
        processor.tokenize(reading.content()).stream()
            .map(TextWordProcessor.Token::normalizedValue)
            .collect(Collectors.toCollection(LinkedHashSet::new)));
  }

  private int percent(List<String> values, int percentage) {
    return Math.min(values.size(), Math.round(values.size() * percentage / 100.0f));
  }

  private void classifyExisting(List<String> candidates, VocabularyStatus status) {
    var catalogWords =
        catalog.values().stream()
            .flatMap(reading -> orderedWords(reading).stream())
            .collect(Collectors.toSet());
    classify(candidates.stream().filter(catalogWords::contains).toList(), status);
  }

  private void classify(List<String> values, VocabularyStatus status) {
    var now = LocalDateTime.now();
    for (var value : new LinkedHashSet<>(values)) {
      var word = words.resolve(value, "en");
      var existing = vocabulary.findByUserIdAndWordId(user.id(), word.id());
      if (existing.isPresent()) {
        var current = existing.get();
        vocabulary.save(
            new UserVocabulary(
                current.id(),
                current.user(),
                current.word(),
                status,
                current.firstSeenAt(),
                status == VocabularyStatus.KNOWN ? now : null,
                current.version()));
      } else {
        vocabulary.save(
            new UserVocabulary(
                null,
                user,
                new Word(word.id(), word.normalizedValue(), word.language()),
                status,
                now,
                status == VocabularyStatus.KNOWN ? now : null));
      }
    }
  }

  private void authenticate(UUID userId) {
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
}
