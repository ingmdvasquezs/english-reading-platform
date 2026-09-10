package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.RecommendationShadowPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.PedagogicalRecommendationScorer;
import com.soap.soap.application.service.PlatformReadingRecommendationCalculator;
import com.soap.soap.application.service.RecommendationEvidenceV2Calculator;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.VocabularyStatus;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendPlatformReadingsUseCaseTest {
  @Mock private UserRepositoryPort users;
  @Mock private ReadingRepositoryPort readings;
  @Mock private ReadingProgressRepositoryPort progress;
  @Mock private UserVocabularyRepositoryPort vocabulary;
  @Mock private CurrentUserPort currentUser;
  @Mock private RecommendationShadowPort shadow;

  private UUID userId;
  private RecommendPlatformReadingsUseCase useCase;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    when(currentUser.requireUserId()).thenReturn(userId);
    useCase =
        new RecommendPlatformReadingsUseCase(
            users,
            readings,
            progress,
            vocabulary,
            new TextWordProcessor(),
            new PlatformReadingRecommendationCalculator(),
            new PedagogicalRecommendationScorer(),
            currentUser,
            new RecommendationEvidenceV2Calculator(),
            shadow);
  }

  @Test
  void ranksPedagogicallyPrioritizesProgressAndExcludesUserReadings() {
    var higherFit = platform("Higher fit", "known", 1);
    var highConfidence = platform("High confidence", "known new", 2);
    var lowConfidence = platform("Low confidence", "known familiar one two three four five", 3);
    var userReading =
        new Reading(
            UUID.randomUUID(),
            new User(userId, "Ada", "ada@example.com"),
            "Private",
            "known",
            "en",
            LocalDateTime.now());
    when(users.existsById(userId)).thenReturn(true);
    when(readings.findAllPlatformReadings())
        .thenReturn(List.of(userReading, lowConfidence, highConfidence, higherFit));
    when(vocabulary.findStatusesByNormalizedValues(
            eq(userId), eq("en"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            Map.of(
                "known", VocabularyStatus.KNOWN,
                "familiar", VocabularyStatus.KNOWN,
                "new", VocabularyStatus.NEW));
    when(progress.findByUserIdAndReadingIds(
            userId, Set.of(higherFit.id(), highConfidence.id(), lowConfidence.id())))
        .thenReturn(
            Map.of(
                highConfidence.id(),
                ReadingProgress.completed(
                    userId,
                    highConfidence.id(),
                    LocalDateTime.parse("2026-08-30T09:00:00"),
                    LocalDateTime.parse("2026-08-30T09:10:00"))));

    var result = useCase.recommendPlatformReadings(new PageRequest(0, 10));

    assertThat(result.content())
        .extracting(com.soap.soap.application.model.RecommendedPlatformReading::title)
        .containsExactly("Low confidence", "Higher fit", "High confidence")
        .doesNotContain("Private");
    assertThat(result.content().get(2).vocabularyFitPercentage()).isEqualByComparingTo("50.00");
    assertThat(result.content().get(2).classificationConfidencePercentage())
        .isEqualByComparingTo("100.00");
    assertThat(result.content().getFirst().vocabularyFitPercentage()).isEqualByComparingTo("50.00");
    assertThat(result.content().getFirst().classificationConfidencePercentage())
        .isEqualByComparingTo("28.57");
    assertThat(result.content().get(2).progressStatus()).isEqualTo(ReadingProgressStatus.COMPLETED);
    assertThat(result.content().getFirst().progressStatus()).isNull();
    verify(vocabulary)
        .findStatusesByNormalizedValues(
            eq(userId), eq("en"), org.mockito.ArgumentMatchers.anyCollection());
  }

  @Test
  void paginatesOnlyAfterCalculatingTheGlobalRanking() {
    when(users.existsById(userId)).thenReturn(true);
    when(readings.findAllPlatformReadings())
        .thenReturn(
            List.of(
                platform("Unknown", "absent", 1),
                platform("Known", "known", 2),
                platform("Learning", "learning", 3)));
    when(vocabulary.findStatusesByNormalizedValues(
            eq(userId), eq("en"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Map.of("known", VocabularyStatus.KNOWN, "learning", VocabularyStatus.LEARNING));

    var page = useCase.recommendPlatformReadings(new PageRequest(1, 1));

    assertThat(page.totalElements()).isEqualTo(3);
    assertThat(page.content()).singleElement().extracting("title").isEqualTo("Known");
  }

  @Test
  void aUserWithoutVocabularyGetsUnclassifiedWordsAndZeroConfidence() {
    when(users.existsById(userId)).thenReturn(true);
    when(readings.findAllPlatformReadings()).thenReturn(List.of(platform("Text", "one two", 1)));
    when(vocabulary.findStatusesByNormalizedValues(
            eq(userId), eq("en"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Map.of());

    var result = useCase.recommendPlatformReadings(new PageRequest(0, 10)).content().getFirst();

    assertThat(result.explicitNewWords()).isZero();
    assertThat(result.unclassifiedWords()).isEqualTo(2);
    assertThat(result.vocabularyFitPercentage()).isEqualByComparingTo("30.00");
    assertThat(result.classificationConfidencePercentage()).isEqualByComparingTo("0.00");
  }

  @Test
  void validatesTheRequestAfterRequiringAuthentication() {
    assertThatThrownBy(() -> useCase.recommendPlatformReadings(null))
        .isInstanceOf(InvalidApplicationArgumentException.class);
    verify(currentUser).requireUserId();
    verify(readings, never()).findAllPlatformReadings();
  }

  @Test
  void lowerEditorialLevelWinsWhenItsRealVocabularyDistributionFitsBetter() {
    var a2 = scenario("a", 68, 11, 21, EditorialLevel.A2);
    var b1 = scenario("b", 40, 5, 55, EditorialLevel.B1);
    var b2 = scenario("c", 45, 8, 47, EditorialLevel.B2);
    when(users.existsById(userId)).thenReturn(true);
    when(readings.findAllPlatformReadings()).thenReturn(List.of(b2, b1, a2));
    when(vocabulary.findStatusesByNormalizedValues(
            eq(userId), eq("en"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(statusesFor(a2, b1, b2));

    var result = useCase.recommendPlatformReadings(new PageRequest(0, 3));

    assertThat(result.content()).extracting("title").containsExactly("a", "c", "b");
    verify(progress).findByUserIdAndReadingIds(userId, Set.of(a2.id(), b1.id(), b2.id()));
  }

  @Test
  void notStartedOutranksInProgressAndCompletedWithoutFilteringEither() {
    var notStarted = platform("Not started", "known", 1);
    var inProgress = platform("In progress", "known learning", 2);
    var completed = platform("Completed", "known learning absent", 3);
    when(users.existsById(userId)).thenReturn(true);
    when(readings.findAllPlatformReadings()).thenReturn(List.of(completed, inProgress, notStarted));
    when(vocabulary.findStatusesByNormalizedValues(
            eq(userId), eq("en"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Map.of("known", VocabularyStatus.KNOWN, "learning", VocabularyStatus.LEARNING));
    when(progress.findByUserIdAndReadingIds(
            userId, Set.of(notStarted.id(), inProgress.id(), completed.id())))
        .thenReturn(
            Map.of(
                inProgress.id(),
                ReadingProgress.inProgress(userId, inProgress.id(), LocalDateTime.now()),
                completed.id(),
                ReadingProgress.completed(
                    userId, completed.id(), LocalDateTime.now(), LocalDateTime.now())));

    var result = useCase.recommendPlatformReadings(new PageRequest(0, 3));

    assertThat(result.content())
        .extracting("title")
        .containsExactly("Not started", "In progress", "Completed");
  }

  @Test
  void returnsAFullDeterministicPageWhenEveryCandidateHasProgress() {
    var first = platform("First", "known", 1);
    var second = platform("Second", "learning", 2);
    var third = platform("Third", "absent", 3);
    var candidates = List.of(first, second, third);
    when(users.existsById(userId)).thenReturn(true);
    when(readings.findAllPlatformReadings()).thenReturn(candidates);
    when(vocabulary.findStatusesByNormalizedValues(
            eq(userId), eq("en"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Map.of("known", VocabularyStatus.KNOWN, "learning", VocabularyStatus.LEARNING));
    when(progress.findByUserIdAndReadingIds(userId, Set.of(first.id(), second.id(), third.id())))
        .thenReturn(
            Map.of(
                first.id(), completed(first),
                second.id(), completed(second),
                third.id(), completed(third)));

    var firstRun = useCase.recommendPlatformReadings(new PageRequest(0, 3));
    var secondRun = useCase.recommendPlatformReadings(new PageRequest(0, 3));

    assertThat(firstRun.content())
        .hasSize(3)
        .allMatch(reading -> reading.progressStatus() == ReadingProgressStatus.COMPLETED);
    assertThat(secondRun.content())
        .extracting("readingId")
        .containsExactlyElementsOf(
            firstRun.content().stream().map(item -> item.readingId()).toList());
  }

  @Test
  void returnsResultsWhenEveryCandidateIsInProgress() {
    var first = platform("First active", "known learning", 1);
    var second = platform("Second active", "known absent", 2);
    when(users.existsById(userId)).thenReturn(true);
    when(readings.findAllPlatformReadings()).thenReturn(List.of(first, second));
    when(vocabulary.findStatusesByNormalizedValues(
            eq(userId), eq("en"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Map.of("known", VocabularyStatus.KNOWN, "learning", VocabularyStatus.LEARNING));
    when(progress.findByUserIdAndReadingIds(userId, Set.of(first.id(), second.id())))
        .thenReturn(
            Map.of(
                first.id(),
                ReadingProgress.inProgress(userId, first.id(), LocalDateTime.now()),
                second.id(),
                ReadingProgress.inProgress(userId, second.id(), LocalDateTime.now())));

    var result = useCase.recommendPlatformReadings(new PageRequest(0, 2));

    assertThat(result.content())
        .hasSize(2)
        .allMatch(reading -> reading.progressStatus() == ReadingProgressStatus.IN_PROGRESS);
  }

  @Test
  void shadowReceivesTheSameCatalogEvidenceWithoutChangingTheV1Order() {
    var first = platform("First", "known known learning absent", 1);
    var second = platform("Second", "known absent", 2);
    when(users.existsById(userId)).thenReturn(true);
    when(readings.findAllPlatformReadings()).thenReturn(List.of(second, first));
    when(vocabulary.findStatusesByNormalizedValues(
            eq(userId), eq("en"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Map.of("known", VocabularyStatus.KNOWN, "learning", VocabularyStatus.LEARNING));

    var result = useCase.recommendPlatformReadings(new PageRequest(0, 2));
    var supplierCaptor = org.mockito.ArgumentCaptor.forClass(java.util.function.Supplier.class);
    verify(shadow).observe(supplierCaptor.capture());
    @SuppressWarnings("unchecked")
    var shadowCandidates =
        (List<com.soap.soap.application.model.RecommendationShadowCandidate>)
            supplierCaptor.getValue().get();

    assertThat(shadowCandidates)
        .extracting(candidate -> candidate.reading().readingId())
        .containsExactlyElementsOf(
            result.content().stream().map(item -> item.readingId()).toList());
    var firstEvidence =
        shadowCandidates.stream()
            .filter(candidate -> candidate.reading().title().equals("First"))
            .findFirst()
            .orElseThrow()
            .v2Evidence();
    assertThat(firstEvidence.totalTokens()).isEqualTo(4);
    assertThat(firstEvidence.knownTokens()).isEqualTo(2);
    assertThat(firstEvidence.learningTokens()).isEqualTo(1);
  }

  @Test
  void shadowAdapterFailureCannotBreakTheProductiveV1Result() {
    var reading = platform("Productive", "known", 1);
    when(users.existsById(userId)).thenReturn(true);
    when(readings.findAllPlatformReadings()).thenReturn(List.of(reading));
    when(vocabulary.findStatusesByNormalizedValues(userId, "en", Set.of("known")))
        .thenReturn(Map.of("known", VocabularyStatus.KNOWN));
    doThrow(new IllegalStateException("shadow failed"))
        .when(shadow)
        .observe(org.mockito.ArgumentMatchers.any());

    var result = useCase.recommendPlatformReadings(new PageRequest(0, 1));

    assertThat(result.content()).singleElement().extracting("title").isEqualTo("Productive");
  }

  private Reading platform(String title, String content, int minute) {
    return platform(title, content, minute, EditorialLevel.A1);
  }

  private Reading platform(
      String title, String content, int minute, EditorialLevel editorialLevel) {
    return new Reading(
        UUID.randomUUID(),
        null,
        title,
        content,
        "en",
        LocalDateTime.parse("2026-08-29T12:00:00").plusMinutes(minute),
        ReadingOrigin.PLATFORM,
        editorialLevel,
        "Test");
  }

  private Reading scenario(
      String prefix, int known, int learning, int unknown, EditorialLevel level) {
    var words = new java.util.ArrayList<String>();
    for (var index = 0; index < known; index++) words.add(prefix + "known" + letters(index));
    for (var index = 0; index < learning; index++) words.add(prefix + "learning" + letters(index));
    for (var index = 0; index < unknown; index++) words.add(prefix + "unknown" + letters(index));
    return platform(prefix, String.join(" ", words), level.ordinal(), level);
  }

  private Map<String, VocabularyStatus> statusesFor(Reading... candidates) {
    var statuses = new HashMap<String, VocabularyStatus>();
    for (var candidate : candidates) {
      for (var word : candidate.content().split(" ")) {
        if (word.startsWith(candidate.title() + "known")) {
          statuses.put(word, VocabularyStatus.KNOWN);
        } else if (word.startsWith(candidate.title() + "learning")) {
          statuses.put(word, VocabularyStatus.LEARNING);
        }
      }
    }
    return statuses;
  }

  private ReadingProgress completed(Reading reading) {
    return ReadingProgress.completed(
        userId, reading.id(), LocalDateTime.now(), LocalDateTime.now());
  }

  private String letters(int value) {
    var result = new StringBuilder();
    var remaining = value;
    do {
      result.append((char) ('a' + remaining % 26));
      remaining = remaining / 26 - 1;
    } while (remaining >= 0);
    return result.reverse().toString();
  }
}
