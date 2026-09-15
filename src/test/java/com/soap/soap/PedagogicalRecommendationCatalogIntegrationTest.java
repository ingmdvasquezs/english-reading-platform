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
import com.soap.soap.application.service.ReadingLexicalIndexer;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import jakarta.persistence.EntityManager;
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

@SpringBootTest(properties = {"security.jwt.secret=test-only-secret-with-at-least-32-bytes"})
@Testcontainers
@ActiveProfiles("local")
@Transactional
class PedagogicalRecommendationCatalogIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private RecommendPlatformReadingsPort recommendations;
  @Autowired private com.soap.soap.application.port.in.ListContinueReadingPort continueReading;
  @Autowired private CompleteInitialVocabularyTestPort completeOnboarding;
  @Autowired private InitialVocabularyTestSourcePort onboardingTest;
  @Autowired private ReadingRepositoryPort readings;
  @Autowired private ReadingProgressRepositoryPort progress;
  @Autowired private UserRepositoryPort users;
  @Autowired private UserVocabularyRepositoryPort vocabulary;
  @Autowired private WordRepositoryPort words;
  @Autowired private TextWordProcessor processor;
  @Autowired private ReadingLexicalIndexer lexicalIndexer;
  @Autowired private EntityManager entityManager;

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
    seedControlledCatalog();
    catalog =
        readings.findAllPlatformReadings().stream()
            .collect(Collectors.toMap(Reading::title, Function.identity()));
  }

  private void seedControlledCatalog() {
    createPlatformReading(
        "The Lost Blue Scarf",
        "Every morning Lily walks through the green park wearing a warm coat. She would always search for her lost blue scarf under the old oak tree near the river bank while birds sing sweet songs.",
        EditorialLevel.A1,
        "Daily Life & Relationships",
        "the-lost-blue-scarf");

    createPlatformReading(
        "The Sparrow and the Red Cup",
        "A small sparrow sits on the wooden table outside the kitchen. A red cup of sweet water is waiting for the bird. It drinks slowly and hops across the bright garden with gentle chirps.",
        EditorialLevel.A1,
        "Daily Life & Relationships",
        "the-sparrow-and-the-red-cup");

    createPlatformReading(
        "A Morning at the Library",
        "Nora visits the public library early in the morning. She likes to read simple books and study quiet stories while the librarian smiles warmly and organizes shelves with care.",
        EditorialLevel.A1,
        "Work & Society",
        "a-morning-at-the-library");

    createPlatformReading(
        "Elena in the Harbor",
        "Elena arrived in the harbor town on a rainy morning carrying a small suitcase and a quiet desire to build a fresh life. She walked down the busy street toward the local market. The friendly baker greeted her with a warm smile. Elena visited the community library to organize archives. She welcomed each deliberate challenge and valuable opportunity to learn everyday words with genuine curiosity and laughter.",
        EditorialLevel.A1,
        "Culture, Arts & Fiction",
        null);

    createPlatformReading(
        "Why Cities Need Trees",
        "Modern cities face rising temperatures and noisy streets. Planting tall green trees provides natural shade cleaner air and peaceful urban spaces. Neighborhoods with dense tree canopies experience better health and stronger social connections among residents.",
        EditorialLevel.A2,
        "Nature & Environment",
        "why-cities-need-trees");

    createPlatformReading(
        "A Walk by the River",
        "Walking along the winding river helps people relax after busy working hours. Water currents carry floating leaves toward distant hills while fishermen prepare their nets along the grassy banks under the afternoon sun.",
        EditorialLevel.A2,
        "Nature & Environment",
        null);

    createPlatformReading(
        "The Empty Lot Project",
        "Residents gathered to improve their urban neighborhood. They wanted to provide safe areas and change an abandoned square into a vibrant community garden. Through shared experience and collective effort they solved each unexpected challenge. The project offered a valuable opportunity for neighbors to notice local needs decide priorities together and instead build lasting civic solutions.",
        EditorialLevel.B1,
        "Work & Society",
        null);

    createPlatformReading(
        "Learning to Ask Better Questions",
        "Effective communication requires asking thoughtful and insightful questions. Rather than accepting superficial answers curious learners explore root causes and examine alternative viewpoints. Developing this cognitive habit enhances problem solving and strengthens collaboration across diverse professional teams.",
        EditorialLevel.B1,
        "Work & Society",
        null);

    createPlatformReading(
        "The Cost of Constant Attention",
        "Digital distractions continuously fragment modern human focus and disrupt everyday cognitive workflow. When notifications constantly interrupt deep professional work creative productivity plummets rapidly and chronic intellectual fatigue inevitably accumulates over prolonged periods. Reclaiming sustained concentration demands disciplined personal boundary setting structured restorative downtime and intentional strategic disconnection from overwhelming informational streams. In complex contemporary environments mastering mental presence constitutes an invaluable competitive advantage for reflective thinkers.",
        EditorialLevel.B2,
        "Science & Technology",
        "the-cost-of-constant-attention");

    createPlatformReading(
        "The Changing Nature of Work",
        "Technological transformations and remote collaboration are reshaping employment paradigms worldwide. Organizations now emphasize adaptable skill sets cross functional teamwork and continuous self directed education over rigid organizational hierarchies.",
        EditorialLevel.B2,
        "Work & Society",
        null);

    createPlatformReading(
        "The Museum of Unfinished Things",
        "Hidden inside an forgotten archival vault the museum exhibits abandoned manuscripts discarded inventions and preliminary sketches of legendary creators. Each incomplete artifact reveals creative vulnerability celebrating intellectual ambition over commercial perfection and inviting philosophical contemplation about human potential and artistic evolution in the modern city.",
        EditorialLevel.C1,
        "Culture, Arts & Fiction",
        null);

    createPlatformReading(
        "A City That Predicts Its Citizens",
        "Algorithmic governance and predictive municipal infrastructure now anticipate urban movements allocating public transport and monitoring citizen consumption patterns in real time across the modern city. While efficiency benchmarks rise dramatically civil liberties advocates question algorithmic surveillance loss of civic spontaneity and autonomous human decision making in civic society.",
        EditorialLevel.C1,
        "Science & Technology",
        null);

    createPlatformReading(
        "A Republic of Echoes",
        "In an era dominated by hyper partisan rhetoric and self reinforcing informational silos civic discourse degenerates into cacophonous tribalism where deliberative consensus becomes elusive. Epistemological humility and hermeneutic vigilance are indispensable virtues if democratic institutions hope to transcend ideological polarization dismantle systemic dogmatism and restore nuanced societal deliberation for thoughtful citizens seeking collective wisdom.",
        EditorialLevel.C2,
        "Philosophy, Thought & Meaning",
        "a-republic-of-echoes");

    createPlatformReading(
        "The Cartographer of Vanishing Roads",
        "Mapping ephemeral pathways across disappearing landscapes requires obsolete geographical instruments intuitive topographical deductions and exquisite perceptual precision. The cartographer documents subterranean fissures forgotten nomadic routes and shifting geological contours before anthropogenic encroachment renders ancestral territories entirely unrecognizable and forever erased from fragile memory.",
        EditorialLevel.C2,
        "History & Society",
        null);
  }

  private void createPlatformReading(
      String title, String content, EditorialLevel level, String category, String coverKey) {
    var saved =
        readings.save(
            new Reading(
                null,
                null,
                title,
                content,
                "en",
                LocalDateTime.now(),
                ReadingOrigin.PLATFORM,
                level,
                category,
                coverKey,
                EditorialStatus.PUBLISHED));
    entityManager.flush();
    lexicalIndexer.indexReading(saved.id(), saved.language(), saved.content());
  }

  @Test
  void sparseProfileIsConservativeDeterministicAndPaginatesGlobally() {
    classify(List.of("the", "and", "a"), VocabularyStatus.KNOWN);

    var firstRun = ranked();
    var secondRun = ranked();
    var page0 = recommendations.recommendPlatformReadings(new PageRequest(0, 4));
    var page1 = recommendations.recommendPlatformReadings(new PageRequest(1, 4));
    var page2 = recommendations.recommendPlatformReadings(new PageRequest(2, 4));

    assertThat(firstRun).hasSize(14);
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
  void separatesContinueReadingFromRecommendationsAndExcludesStartedReadings() {
    var initialCatalog = ranked();
    assertThat(initialCatalog).hasSize(14);

    var notStarted = reading(initialCatalog.get(0).title());
    var inProgress = reading(initialCatalog.get(1).title());
    var completed = reading(initialCatalog.get(2).title());

    progress.startIfAbsent(user.id(), inProgress.id(), LocalDateTime.now());
    progress.complete(user.id(), completed.id(), LocalDateTime.now());

    // Criteria D: IN_PROGRESS appears in listContinueReading
    var continueReadingResult = continueReading.listContinueReading(new PageRequest(0, 10));
    assertThat(continueReadingResult.content())
        .extracting(com.soap.soap.application.model.ContinueReadingItem::readingId)
        .contains(inProgress.id())
        .doesNotContain(completed.id(), notStarted.id());

    // Criteria A & B: IN_PROGRESS and COMPLETED do NOT appear in recommendPlatformReadings
    var recommended = ranked();
    assertThat(ids(recommended))
        .doesNotContain(inProgress.id())
        .doesNotContain(completed.id())
        .contains(notStarted.id());

    // Criteria E: No reading appears simultaneously in both responses
    var continueReadingIds =
        continueReadingResult.content().stream()
            .map(com.soap.soap.application.model.ContinueReadingItem::readingId)
            .collect(Collectors.toSet());
    assertThat(ids(recommended)).noneMatch(continueReadingIds::contains);

    // Criteria F: totalElements excludes IN_PROGRESS and COMPLETED (14 - 2 = 12)
    var pagedRecs = recommendations.recommendPlatformReadings(new PageRequest(0, 10));
    assertThat(pagedRecs.totalElements()).isEqualTo(12);
    assertThat(recommended).hasSize(12);

    // Criteria I: When all catalog readings are completed or in progress, recommendations is empty
    for (var reading : catalog.values()) {
      progress.complete(user.id(), reading.id(), LocalDateTime.now());
    }
    var allCompleted = recommendations.recommendPlatformReadings(new PageRequest(0, 10));
    assertThat(allCompleted.totalElements()).isEqualTo(0);
    assertThat(allCompleted.content()).isEmpty();
  }

  @Test
  void allControlledRecommendationsAreReachableAcrossPages() {
    var collected = new ArrayList<RecommendedPlatformReading>();
    for (var page = 0; page < 4; page++) {
      var result = recommendations.recommendPlatformReadings(new PageRequest(page, 4));
      assertThat(result.totalElements()).isEqualTo(14);
      collected.addAll(result.content());
    }

    assertThat(collected).hasSize(14);
    assertThat(ids(collected)).doesNotHaveDuplicates();
    assertThat(ids(collected)).containsExactlyElementsOf(ids(ranked()));
  }

  @Test
  void controlledCatalogHasExpectedLevelsLanguageCategoriesAndSubstantialContent() {
    assertThat(catalog).hasSize(14);
    assertThat(catalog.values())
        .allSatisfy(
            reading -> {
              assertThat(reading.language()).isEqualTo("en");
              assertThat(reading.category()).isNotBlank();
              assertThat(reading.content()).isNotBlank();
            });
    assertThat(catalog.values())
        .filteredOn(r -> r.editorialLevel() == EditorialLevel.A1)
        .hasSize(4);
    assertThat(catalog.values())
        .filteredOn(r -> r.editorialLevel() == EditorialLevel.A2)
        .hasSize(2);
    assertThat(catalog.values())
        .filteredOn(r -> r.editorialLevel() == EditorialLevel.B1)
        .hasSize(2);
    assertThat(catalog.values())
        .filteredOn(r -> r.editorialLevel() == EditorialLevel.B2)
        .hasSize(2);
    assertThat(catalog.values())
        .filteredOn(r -> r.editorialLevel() == EditorialLevel.C1)
        .hasSize(2);
    assertThat(catalog.values())
        .filteredOn(r -> r.editorialLevel() == EditorialLevel.C2)
        .hasSize(2);
    assertThat(catalog.values())
        .allSatisfy(reading -> assertThat(processor.tokenize(reading.content())).isNotEmpty());
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
            "The Lost Blue Scarf", "the-lost-blue-scarf",
            "The Sparrow and the Red Cup", "the-sparrow-and-the-red-cup",
            "A Morning at the Library", "a-morning-at-the-library",
            "Why Cities Need Trees", "why-cities-need-trees",
            "The Cost of Constant Attention", "the-cost-of-constant-attention",
            "A Republic of Echoes", "a-republic-of-echoes");

    expected.forEach(
        (title, coverKey) -> assertThat(reading(title).coverKey()).isEqualTo(coverKey));
    assertThat(catalog.values()).filteredOn(reading -> reading.coverKey() != null).hasSize(6);
    assertThat(catalog.values()).filteredOn(reading -> reading.coverKey() == null).hasSize(8);
    assertThat(reading("A Morning at the Library").coverKey())
        .isEqualTo("a-morning-at-the-library");
    assertThat(reading("Why Cities Need Trees").coverKey()).isEqualTo("why-cities-need-trees");
    assertThat(
            List.of(
                reading("The Changing Nature of Work"),
                reading("The Museum of Unfinished Things"),
                reading("The Empty Lot Project"),
                reading("Elena in the Harbor")))
        .allSatisfy(item -> assertThat(item.coverKey()).isNull());
    var rankedById =
        ranked().stream().collect(java.util.stream.Collectors.toMap(r -> r.readingId(), r -> r));
    assertThat(rankedById.values()).filteredOn(item -> item.coverKey() != null).hasSize(6);
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
