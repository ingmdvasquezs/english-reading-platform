package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PlatformReadingSummary;
import com.soap.soap.application.model.ReadingLexicalEvidence;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.ReadingWordFrequencyRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.RecommendationReasonEvaluator;
import com.soap.soap.application.service.RecommendationScorerV2;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.domain.model.RecommendationReasonCode;
import com.soap.soap.domain.model.User;
import java.time.LocalDateTime;
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
class RecommendPlatformReadingsUseCaseTest {
  @Mock private UserRepositoryPort users;
  @Mock private ReadingRepositoryPort readings;
  @Mock private ReadingProgressRepositoryPort progress;
  @Mock private UserVocabularyRepositoryPort vocabulary;
  @Mock private ReadingWordFrequencyRepositoryPort frequencyRepository;
  @Mock private CurrentUserPort currentUser;

  private final RecommendationScorerV2 scorer = new RecommendationScorerV2();
  private final RecommendationReasonEvaluator reasonEvaluator = new RecommendationReasonEvaluator();

  private UUID userId;
  private RecommendPlatformReadingsUseCase useCase;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    when(currentUser.requireUserId()).thenReturn(userId);
    lenient()
        .when(users.findById(userId))
        .thenReturn(Optional.of(new User(userId, "Test User", "test@example.com")));
    useCase =
        new RecommendPlatformReadingsUseCase(
            users,
            readings,
            progress,
            vocabulary,
            frequencyRepository,
            scorer,
            reasonEvaluator,
            currentUser,
            30);
  }

  @Test
  @DisplayName("Excluye IN_PROGRESS y COMPLETED y recomienda únicamente lecturas NOT_STARTED")
  void excludesInProgressAndCompletedAndRecommendsOnlyNotStarted() {
    var inProgressSummary = summary("In Progress Book", EditorialLevel.B2, 1);
    var notStartedSummary = summary("Not Started Book", EditorialLevel.A1, 2);
    var completedSummary = summary("Completed Book", EditorialLevel.A1, 3);

    when(users.existsById(userId)).thenReturn(true);
    when(readings.findAllPlatformReadingSummaries())
        .thenReturn(List.of(completedSummary, notStartedSummary, inProgressSummary));
    when(vocabulary.countClassifiedWordsByUserAndLanguage(userId, "en")).thenReturn(50L);

    var notStartedEvidence = evidence(notStartedSummary.id(), 100, 95, 5, 0, 50, 45, 5, 0, 0, 0);

    when(frequencyRepository.findLexicalEvidenceByUserAndLanguage(
            eq(userId), eq("en"), eq(List.of(notStartedSummary.id()))))
        .thenReturn(List.of(notStartedEvidence));

    when(progress.findByUserIdAndReadingIds(
            eq(userId),
            eq(Set.of(inProgressSummary.id(), notStartedSummary.id(), completedSummary.id()))))
        .thenReturn(
            Map.of(
                inProgressSummary.id(),
                ReadingProgress.inProgress(userId, inProgressSummary.id(), LocalDateTime.now()),
                completedSummary.id(),
                ReadingProgress.completed(
                    userId, completedSummary.id(), LocalDateTime.now(), LocalDateTime.now())));

    var result = useCase.recommendPlatformReadings(new PageRequest(0, 10));

    // Excludes IN_PROGRESS and COMPLETED: only Not Started Book is recommended
    assertThat(result.content())
        .extracting(RecommendedPlatformReading::title)
        .containsExactly("Not Started Book");
    assertThat(result.totalElements()).isEqualTo(1);

    var first = result.content().get(0);
    assertThat(first.progressStatus()).isNull();
    assertThat(first.vocabularyFitPercentage()).isEqualByComparingTo("100.00");
    assertThat(first.classificationConfidencePercentage()).isEqualByComparingTo("100.00");
    assertThat(first.reasonCode()).isEqualTo(RecommendationReasonCode.HIGH_VOCABULARY_MATCH);

    // Evidencia léxica solo se consulta para candidateIds NOT_STARTED
    verify(frequencyRepository)
        .findLexicalEvidenceByUserAndLanguage(userId, "en", List.of(notStartedSummary.id()));
  }

  @Test
  @DisplayName("Usuario con todo el catálogo iniciado o completado recibe resultado vacío")
  void whenAllCandidatesAreStartedOrCompletedReturnsEmptyResult() {
    var inProgressSummary = summary("In Progress Book", EditorialLevel.B2, 1);
    var completedSummary = summary("Completed Book", EditorialLevel.A1, 2);

    when(users.existsById(userId)).thenReturn(true);
    when(readings.findAllPlatformReadingSummaries())
        .thenReturn(List.of(inProgressSummary, completedSummary));

    when(progress.findByUserIdAndReadingIds(
            eq(userId), eq(Set.of(inProgressSummary.id(), completedSummary.id()))))
        .thenReturn(
            Map.of(
                inProgressSummary.id(),
                ReadingProgress.inProgress(userId, inProgressSummary.id(), LocalDateTime.now()),
                completedSummary.id(),
                ReadingProgress.completed(
                    userId, completedSummary.id(), LocalDateTime.now(), LocalDateTime.now())));

    var result = useCase.recommendPlatformReadings(new PageRequest(0, 10));

    assertThat(result.content()).isEmpty();
    assertThat(result.totalElements()).isEqualTo(0);
    verify(frequencyRepository, never()).findLexicalEvidenceByUserAndLanguage(any(), any(), any());
  }

  @Test
  @DisplayName("Paginación ocurre tras el cálculo del ranking global")
  void paginatesOnlyAfterCalculatingTheGlobalRanking() {
    var b1 = summary("Book 1", EditorialLevel.A1, 1);
    var b2 = summary("Book 2", EditorialLevel.A2, 2);
    var b3 = summary("Book 3", EditorialLevel.B1, 3);

    when(users.existsById(userId)).thenReturn(true);
    when(readings.findAllPlatformReadingSummaries()).thenReturn(List.of(b1, b2, b3));
    when(vocabulary.countClassifiedWordsByUserAndLanguage(userId, "en"))
        .thenReturn(0L); // cold start

    when(frequencyRepository.findLexicalEvidenceByUserAndLanguage(
            eq(userId), eq("en"), org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(
            List.of(
                evidence(b1.id(), 100, 0, 0, 0, 30, 0, 0, 0, 0, 30),
                evidence(b2.id(), 100, 0, 0, 0, 30, 0, 0, 0, 0, 30),
                evidence(b3.id(), 100, 0, 0, 0, 30, 0, 0, 0, 0, 30)));
    when(progress.findByUserIdAndReadingIds(eq(userId), any())).thenReturn(Map.of());

    var page1 = useCase.recommendPlatformReadings(new PageRequest(1, 1));

    assertThat(page1.totalElements()).isEqualTo(3);
    assertThat(page1.content())
        .singleElement()
        .extracting(RecommendedPlatformReading::title)
        .isEqualTo("Book 2");
  }

  @Test
  @DisplayName("Validaciones de request y autenticación")
  void validatesTheRequestAfterRequiringAuthentication() {
    assertThatThrownBy(() -> useCase.recommendPlatformReadings(null))
        .isInstanceOf(InvalidApplicationArgumentException.class);
    verify(currentUser).requireUserId();
    verify(readings, never()).findAllPlatformReadingSummaries();

    when(users.existsById(userId)).thenReturn(false);
    assertThatThrownBy(() -> useCase.recommendPlatformReadings(new PageRequest(0, 10)))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  @DisplayName("Colección vacía devuelve resultado vacío")
  void emptyCandidatesReturnsEmptyPage() {
    when(users.existsById(userId)).thenReturn(true);
    when(readings.findAllPlatformReadingSummaries()).thenReturn(List.of());

    var page = useCase.recommendPlatformReadings(new PageRequest(0, 10));
    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isEqualTo(0);
  }

  @Test
  @DisplayName(
      "Regresión B: Global cold start ordena A1 > A2 > B1 > B2 > C1 > C2 sin tie-breakers personalizados")
  void globalColdStartRanksByEditorialPriorAndLevelHierarchyWithoutPersonalTieBreakers() {
    var a1One = summary("A1 First", EditorialLevel.A1, 1);
    var a1Two = summary("A1 Second", EditorialLevel.A1, 2);
    var a2 = summary("A2 Reading", EditorialLevel.A2, 3);
    var b1 = summary("B1 Reading", EditorialLevel.B1, 4);
    var b2 = summary("B2 Reading", EditorialLevel.B2, 5);
    var c1 = summary("C1 Reading", EditorialLevel.C1, 6);
    var c2 = summary("C2 Reading", EditorialLevel.C2, 7);

    when(users.existsById(userId)).thenReturn(true);
    when(readings.findAllPlatformReadingSummaries())
        .thenReturn(List.of(c2, b2, a1Two, c1, a2, b1, a1One));
    // 0 classified words -> Global cold start (< 30)
    when(vocabulary.countClassifiedWordsByUserAndLanguage(userId, "en")).thenReturn(0L);

    // Give a1Two higher local confidence than a1One to prove localConfidence is NOT used as
    // tie-breaker
    var eA1One = evidence(a1One.id(), 100, 0, 0, 0, 50, 0, 0, 0, 0, 50); // 0% confidence
    var eA1Two = evidence(a1Two.id(), 100, 20, 0, 0, 50, 10, 0, 0, 0, 40); // 20% confidence
    var eA2 = evidence(a2.id(), 100, 0, 0, 0, 50, 0, 0, 0, 0, 50);
    var eB1 = evidence(b1.id(), 100, 0, 0, 0, 50, 0, 0, 0, 0, 50);
    var eB2 = evidence(b2.id(), 100, 0, 0, 0, 50, 0, 0, 0, 0, 50);
    var eC1 = evidence(c1.id(), 100, 0, 0, 0, 50, 0, 0, 0, 0, 50);
    var eC2 = evidence(c2.id(), 100, 0, 0, 0, 50, 0, 0, 0, 0, 50);

    when(frequencyRepository.findLexicalEvidenceByUserAndLanguage(eq(userId), eq("en"), any()))
        .thenReturn(List.of(eA1One, eA1Two, eA2, eB1, eB2, eC1, eC2));
    when(progress.findByUserIdAndReadingIds(eq(userId), any())).thenReturn(Map.of());

    var result = useCase.recommendPlatformReadings(new PageRequest(0, 10));

    // Order must be A1 (by createdAt DESC), then A2, B1, B2, C1, C2
    assertThat(result.content())
        .extracting(RecommendedPlatformReading::title)
        .containsExactly(
            "A1 First",
            "A1 Second",
            "A2 Reading",
            "B1 Reading",
            "B2 Reading",
            "C1 Reading",
            "C2 Reading");

    // All should have reason DISCOVERY during global cold start
    assertThat(result.content())
        .allMatch(r -> r.reasonCode() == RecommendationReasonCode.DISCOVERY);
  }

  @Test
  @DisplayName(
      "Regresión A: Usuario mature con vocabulario rico hace ganar a C2 sobre A2 con baja evidencia")
  void matureUserRanksHighEvidenceC2OverLowEvidenceA2() {
    var a2LowEvidence = summary("Why Cities Need Trees", EditorialLevel.A2, 1);
    var c2HighEvidence = summary("A Republic of Echoes", EditorialLevel.C2, 2);

    when(users.existsById(userId)).thenReturn(true);
    when(readings.findAllPlatformReadingSummaries())
        .thenReturn(List.of(a2LowEvidence, c2HighEvidence));
    // 50 classified words -> Mature user (>= 30)
    when(vocabulary.countClassifiedWordsByUserAndLanguage(userId, "en")).thenReturn(50L);

    // A2: low evidence (total 26 unique, 5 classified = 19.23% confidence, knownTokenCoverage =
    // 21.43%)
    var eA2 = evidence(a2LowEvidence.id(), 100, 21, 0, 0, 26, 5, 0, 0, 0, 21);
    // C2: high evidence (total 50 unique, 43 classified = 86.00% confidence, knownTokenCoverage =
    // 74.40%, learningUniqueRatio = 7.25%)
    var eC2 = evidence(c2HighEvidence.id(), 100, 74, 7, 0, 50, 39, 4, 1, 0, 6);

    when(frequencyRepository.findLexicalEvidenceByUserAndLanguage(eq(userId), eq("en"), any()))
        .thenReturn(List.of(eA2, eC2));
    when(progress.findByUserIdAndReadingIds(eq(userId), any())).thenReturn(Map.of());

    var result = useCase.recommendPlatformReadings(new PageRequest(0, 10));

    // In mature mode, C2 with high evidence and fit outranks A2
    assertThat(result.content().getFirst().title()).isEqualTo("A Republic of Echoes");
    assertThat(result.content().get(1).title()).isEqualTo("Why Cities Need Trees");
  }

  @Test
  @DisplayName(
      "Regresión G: Lectura PLATFORM sin perfil léxico es omitida silenciosamente para el usuario y se emite WARN")
  void platformReadingWithoutLexicalProfileIsOmitted() {
    var validReading = summary("Valid Reading", EditorialLevel.A1, 1);
    var corruptReading = summary("Missing Index Reading", EditorialLevel.A1, 2);

    when(users.existsById(userId)).thenReturn(true);
    when(readings.findAllPlatformReadingSummaries())
        .thenReturn(List.of(validReading, corruptReading));
    when(vocabulary.countClassifiedWordsByUserAndLanguage(userId, "en")).thenReturn(0L);

    // Only validReading has lexical evidence in the DB
    var validEvidence = evidence(validReading.id(), 100, 0, 0, 0, 30, 0, 0, 0, 0, 30);
    when(frequencyRepository.findLexicalEvidenceByUserAndLanguage(eq(userId), eq("en"), any()))
        .thenReturn(List.of(validEvidence));
    when(progress.findByUserIdAndReadingIds(eq(userId), any())).thenReturn(Map.of());

    var result = useCase.recommendPlatformReadings(new PageRequest(0, 10));

    // Response must ONLY contain the valid reading
    assertThat(result.content())
        .singleElement()
        .extracting(RecommendedPlatformReading::title)
        .isEqualTo("Valid Reading");
    assertThat(result.totalElements()).isEqualTo(1);
  }

  @Test
  @DisplayName("Umbral de 30 palabras clasificadas conmuta entre cold start y mature")
  void classifiedWordsThresholdBoundary() {
    var a1 = summary("A1 Easy", EditorialLevel.A1, 1);
    var b1 = summary("B1 Well-Known", EditorialLevel.B1, 2);

    when(users.existsById(userId)).thenReturn(true);
    when(readings.findAllPlatformReadingSummaries()).thenReturn(List.of(b1, a1));

    // A1 has low fit, B1 has very high fit
    var eA1 = evidence(a1.id(), 100, 10, 0, 0, 40, 4, 0, 0, 0, 36);
    var eB1 = evidence(b1.id(), 100, 95, 0, 0, 40, 40, 0, 0, 0, 0);

    when(frequencyRepository.findLexicalEvidenceByUserAndLanguage(eq(userId), eq("en"), any()))
        .thenReturn(List.of(eA1, eB1));
    when(progress.findByUserIdAndReadingIds(eq(userId), any())).thenReturn(Map.of());

    // 1. 29 words -> Cold start (A1 wins because editorialPrior of A1 = 70 > B1 = 62)
    when(vocabulary.countClassifiedWordsByUserAndLanguage(userId, "en")).thenReturn(29L);
    var coldResult = useCase.recommendPlatformReadings(new PageRequest(0, 10));
    assertThat(coldResult.content().getFirst().title()).isEqualTo("A1 Easy");

    // 2. 30 words -> Mature mode (B1 wins because personalized score is high and confident)
    when(vocabulary.countClassifiedWordsByUserAndLanguage(userId, "en")).thenReturn(30L);
    var matureResult = useCase.recommendPlatformReadings(new PageRequest(0, 10));
    assertThat(matureResult.content().getFirst().title()).isEqualTo("B1 Well-Known");

    // 3. 31 words -> Mature mode
    when(vocabulary.countClassifiedWordsByUserAndLanguage(userId, "en")).thenReturn(31L);
    var matureResult2 = useCase.recommendPlatformReadings(new PageRequest(0, 10));
    assertThat(matureResult2.content().getFirst().title()).isEqualTo("B1 Well-Known");
  }

  @Test
  @DisplayName(
      "Recomienda únicamente lecturas que coincidan con learningLanguage sin fallback silencioso a en")
  void filtersCandidatesStrictlyByLearningLanguageWithoutFallbackToEn() {
    var userFr =
        new User(
            userId,
            "French Learner",
            "fr@example.com",
            "hash",
            LocalDateTime.now(),
            true,
            "frUser",
            20,
            "es",
            "fr");
    when(users.existsById(userId)).thenReturn(true);
    when(users.findById(userId)).thenReturn(Optional.of(userFr));

    var enSummary =
        new PlatformReadingSummary(
            UUID.randomUUID(),
            "English Book",
            "en",
            EditorialLevel.A1,
            "Daily Life & Relationships",
            LocalDateTime.now(),
            null,
            "en-cover");
    var frSummary =
        new PlatformReadingSummary(
            UUID.randomUUID(),
            "Livre Français",
            "fr",
            EditorialLevel.A1,
            "Daily Life & Relationships",
            LocalDateTime.now(),
            null,
            "fr-cover");

    when(readings.findAllPlatformReadingSummaries()).thenReturn(List.of(enSummary, frSummary));
    when(vocabulary.countClassifiedWordsByUserAndLanguage(userId, "fr")).thenReturn(50L);
    var frEvidence = evidence(frSummary.id(), 100, 95, 5, 0, 50, 45, 5, 0, 0, 0);
    when(frequencyRepository.findLexicalEvidenceByUserAndLanguage(
            eq(userId), eq("fr"), eq(List.of(frSummary.id()))))
        .thenReturn(List.of(frEvidence));
    when(progress.findByUserIdAndReadingIds(eq(userId), eq(Set.of(frSummary.id()))))
        .thenReturn(Map.of());

    var result = useCase.recommendPlatformReadings(new PageRequest(0, 10));

    // Exclusively fr candidate is recommended, en is strictly omitted
    assertThat(result.content())
        .extracting(RecommendedPlatformReading::title)
        .containsExactly("Livre Français");

    // When user has a language without candidates, empty result returned - NO fallback to en
    var userJa =
        new User(
            userId,
            "Japanese Learner",
            "ja@example.com",
            "hash",
            LocalDateTime.now(),
            true,
            "jaUser",
            20,
            "es",
            "ja");
    when(users.findById(userId)).thenReturn(Optional.of(userJa));
    var resultJa = useCase.recommendPlatformReadings(new PageRequest(0, 10));
    assertThat(resultJa.content()).isEmpty();
    assertThat(resultJa.totalElements()).isEqualTo(0);
  }

  @Test
  @DisplayName("Lanza IllegalStateException si el usuario no tiene learningLanguage configurado")
  void throwsWhenUserHasNoConfiguredLearningLanguage() {
    var unconfiguredUser =
        new User(
            userId,
            "No Lang User",
            "nolang@example.com",
            "hash",
            LocalDateTime.now(),
            true,
            "nolang",
            20,
            "es",
            null);
    when(users.existsById(userId)).thenReturn(true);
    when(users.findById(userId)).thenReturn(Optional.of(unconfiguredUser));

    assertThatThrownBy(() -> useCase.recommendPlatformReadings(new PageRequest(0, 10)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("active learning language");
  }

  private PlatformReadingSummary summary(String title, EditorialLevel level, int daysAgo) {
    return new PlatformReadingSummary(
        UUID.randomUUID(),
        title,
        "en",
        level,
        "General",
        LocalDateTime.now().minusDays(daysAgo),
        null,
        "covers/" + title.toLowerCase().replace(' ', '_') + ".jpg");
  }

  private ReadingLexicalEvidence evidence(
      UUID readingId,
      int totalTokens,
      int knownTokens,
      int learningTokens,
      int ignoredTokens,
      int totalUnique,
      int knownUnique,
      int learningUnique,
      int explicitNewUnique,
      int ignoredUnique,
      int unclassifiedUnique) {
    return new ReadingLexicalEvidence(
        readingId,
        totalTokens,
        knownTokens,
        learningTokens,
        ignoredTokens,
        totalUnique,
        knownUnique,
        learningUnique,
        explicitNewUnique,
        ignoredUnique,
        unclassifiedUnique);
  }
}
