package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.PlatformReadingSummary;
import com.soap.soap.application.model.ReadingLexicalEvidence;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingCollectionRepositoryPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.ReadingWordFrequencyRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.PlatformReadingPersonalizationService;
import com.soap.soap.application.service.RecommendationReasonEvaluator;
import com.soap.soap.application.service.RecommendationScorerV2;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingCollection;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.RecommendationReasonCode;
import com.soap.soap.domain.model.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformVocabFitConsistencyTest {

  @Mock private UserRepositoryPort users;
  @Mock private ReadingRepositoryPort readings;
  @Mock private ReadingCollectionRepositoryPort collections;
  @Mock private ReadingProgressRepositoryPort progress;
  @Mock private UserVocabularyRepositoryPort vocabulary;
  @Mock private ReadingWordFrequencyRepositoryPort frequencyRepository;
  @Mock private CurrentUserPort currentUser;

  private RecommendationScorerV2 scorer;
  private RecommendationReasonEvaluator reasonEvaluator;
  private PlatformReadingPersonalizationService personalizer;
  private RecommendPlatformReadingsUseCase recommendUseCase;
  private ListCollectionReadingsUseCase collectionUseCase;

  private UUID userId;
  private UUID readingId;
  private User testUser;
  private Reading testReading;
  private PlatformReadingSummary testSummary;
  private ReadingCollection testCollection;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    readingId = UUID.randomUUID();

    scorer = new RecommendationScorerV2();
    reasonEvaluator = new RecommendationReasonEvaluator();
    personalizer =
        new PlatformReadingPersonalizationService(
            vocabulary, frequencyRepository, scorer, reasonEvaluator, 30);

    recommendUseCase =
        new RecommendPlatformReadingsUseCase(
            users,
            readings,
            progress,
            vocabulary,
            frequencyRepository,
            scorer,
            reasonEvaluator,
            currentUser,
            personalizer,
            30);

    collectionUseCase =
        new ListCollectionReadingsUseCase(users, collections, progress, personalizer, currentUser);

    testUser =
        new User(
            userId,
            "Learner",
            "learner@example.com",
            "hash",
            LocalDateTime.now(),
            true,
            "learner",
            25,
            "es",
            "en");

    testReading =
        new Reading(
            readingId,
            null,
            "The Mohán and the Spring of Water",
            "Sample reading content text here.",
            "en",
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Myths & Legends",
            "mohan-cover",
            EditorialStatus.PUBLISHED);

    testSummary =
        new PlatformReadingSummary(
            readingId,
            testReading.title(),
            testReading.language().value(),
            testReading.editorialLevel(),
            testReading.category(),
            testReading.createdAt(),
            null,
            testReading.coverKey());

    testCollection =
        new ReadingCollection(
            UUID.randomUUID(),
            "colombian-myths-legends",
            "Mitos y leyendas de Colombia",
            "Colección editorial",
            1,
            true,
            null);

    when(currentUser.requireUserId()).thenReturn(userId);
    when(users.existsById(userId)).thenReturn(true);
    when(users.findById(userId)).thenReturn(Optional.of(testUser));
  }

  @Test
  @DisplayName(
      "Scenario A: Cold-start user (<30 classified words) produces identical metrics and DISCOVERY reason")
  void scenarioA_coldStartUserProducesIdenticalMetrics() {
    // Cold start: 10 words classified (< 30)
    when(vocabulary.countClassifiedWordsByUserAndLanguage(userId, "en")).thenReturn(10L);

    var evidence = new ReadingLexicalEvidence(readingId, 200, 100, 20, 0, 80, 20, 10, 5, 0, 45);
    when(frequencyRepository.findLexicalEvidenceByUserAndLanguage(eq(userId), eq("en"), any()))
        .thenReturn(List.of(evidence));

    // No reading progress (unstarted)
    when(progress.findByUserIdAndReadingIds(eq(userId), any())).thenReturn(Map.of());

    // Recommendations setup
    when(readings.findAllPlatformReadingSummaries()).thenReturn(List.of(testSummary));

    // Collections setup
    when(collections.findActiveByKey("colombian-myths-legends"))
        .thenReturn(Optional.of(testCollection));
    when(collections.findReadings(eq("colombian-myths-legends"), eq("en"), any()))
        .thenReturn(new PageResult<>(List.of(testReading), 0, 10, 1));

    var pageRequest = new PageRequest(0, 10);
    var recPage = recommendUseCase.recommendPlatformReadings(pageRequest);
    var colPage = collectionUseCase.listCollectionReadings("colombian-myths-legends", pageRequest);

    assertThat(recPage.content()).hasSize(1);
    assertThat(colPage.content()).hasSize(1);

    RecommendedPlatformReading recItem = recPage.content().getFirst();
    RecommendedPlatformReading colItem = colPage.content().getFirst();

    assertThat(colItem.readingId()).isEqualTo(recItem.readingId());
    assertThat(colItem.vocabularyFitPercentage()).isEqualTo(recItem.vocabularyFitPercentage());
    assertThat(colItem.classificationConfidencePercentage())
        .isEqualTo(recItem.classificationConfidencePercentage());
    assertThat(colItem.reasonCode()).isEqualTo(RecommendationReasonCode.DISCOVERY);
    assertThat(recItem.reasonCode()).isEqualTo(RecommendationReasonCode.DISCOVERY);
    assertThat(colItem.uniqueWords()).isEqualTo(recItem.uniqueWords());
    assertThat(colItem.knownWords()).isEqualTo(recItem.knownWords());
    assertThat(colItem.learningWords()).isEqualTo(recItem.learningWords());
    assertThat(colItem.explicitNewWords()).isEqualTo(recItem.explicitNewWords());
    assertThat(colItem.unclassifiedWords()).isEqualTo(recItem.unclassifiedWords());
  }

  @Test
  @DisplayName(
      "Scenario B: Mature user with high coverage produces identical metrics and HIGH_VOCABULARY_MATCH")
  void scenarioB_matureUserProducesIdenticalMetrics() {
    // Mature user: 200 classified words (>= 30)
    when(vocabulary.countClassifiedWordsByUserAndLanguage(userId, "en")).thenReturn(200L);

    // High coverage: 95% known tokens, 0 explicit new, 5 unclassified (high confidence)
    var evidence = new ReadingLexicalEvidence(readingId, 500, 480, 10, 0, 100, 90, 5, 0, 0, 5);
    when(frequencyRepository.findLexicalEvidenceByUserAndLanguage(eq(userId), eq("en"), any()))
        .thenReturn(List.of(evidence));

    when(progress.findByUserIdAndReadingIds(eq(userId), any())).thenReturn(Map.of());
    when(readings.findAllPlatformReadingSummaries()).thenReturn(List.of(testSummary));
    when(collections.findActiveByKey("colombian-myths-legends"))
        .thenReturn(Optional.of(testCollection));
    when(collections.findReadings(eq("colombian-myths-legends"), eq("en"), any()))
        .thenReturn(new PageResult<>(List.of(testReading), 0, 10, 1));

    var pageRequest = new PageRequest(0, 10);
    var recPage = recommendUseCase.recommendPlatformReadings(pageRequest);
    var colPage = collectionUseCase.listCollectionReadings("colombian-myths-legends", pageRequest);

    RecommendedPlatformReading recItem = recPage.content().getFirst();
    RecommendedPlatformReading colItem = colPage.content().getFirst();

    assertThat(colItem.vocabularyFitPercentage()).isEqualTo(recItem.vocabularyFitPercentage());
    assertThat(colItem.classificationConfidencePercentage())
        .isEqualTo(recItem.classificationConfidencePercentage());
    assertThat(colItem.reasonCode()).isEqualTo(RecommendationReasonCode.HIGH_VOCABULARY_MATCH);
    assertThat(recItem.reasonCode()).isEqualTo(RecommendationReasonCode.HIGH_VOCABULARY_MATCH);
  }

  @Test
  @DisplayName(
      "Scenario C: Low local confidence (<25%) produces identical metrics and DISCOVERY reason")
  void scenarioC_lowConfidenceProducesIdenticalMetrics() {
    when(vocabulary.countClassifiedWordsByUserAndLanguage(userId, "en")).thenReturn(50L);

    // Only 10 of 100 words classified => 10% local confidence (< 25%)
    var evidence = new ReadingLexicalEvidence(readingId, 300, 20, 5, 0, 100, 8, 2, 0, 0, 90);
    when(frequencyRepository.findLexicalEvidenceByUserAndLanguage(eq(userId), eq("en"), any()))
        .thenReturn(List.of(evidence));

    when(progress.findByUserIdAndReadingIds(eq(userId), any())).thenReturn(Map.of());
    when(readings.findAllPlatformReadingSummaries()).thenReturn(List.of(testSummary));
    when(collections.findActiveByKey("colombian-myths-legends"))
        .thenReturn(Optional.of(testCollection));
    when(collections.findReadings(eq("colombian-myths-legends"), eq("en"), any()))
        .thenReturn(new PageResult<>(List.of(testReading), 0, 10, 1));

    var pageRequest = new PageRequest(0, 10);
    var recItem = recommendUseCase.recommendPlatformReadings(pageRequest).content().getFirst();
    var colItem =
        collectionUseCase
            .listCollectionReadings("colombian-myths-legends", pageRequest)
            .content()
            .getFirst();

    assertThat(colItem.vocabularyFitPercentage()).isEqualTo(recItem.vocabularyFitPercentage());
    assertThat(colItem.classificationConfidencePercentage())
        .isEqualTo(recItem.classificationConfidencePercentage());
    assertThat(colItem.reasonCode()).isEqualTo(RecommendationReasonCode.DISCOVERY);
    assertThat(recItem.reasonCode()).isEqualTo(RecommendationReasonCode.DISCOVERY);
  }

  @Test
  @DisplayName(
      "Scenario D: Learning words present (>=10% ratio, confidence >= 40%) produces PRACTICE_VOCABULARY in both")
  void scenarioD_learningWordsPresentProducesPracticeVocabulary() {
    when(vocabulary.countClassifiedWordsByUserAndLanguage(userId, "en")).thenReturn(100L);

    // 50 unique: 30 known, 10 learning (20% learning ratio), 10 unclassified (80% confidence)
    var evidence = new ReadingLexicalEvidence(readingId, 250, 180, 50, 0, 50, 30, 10, 0, 0, 10);
    when(frequencyRepository.findLexicalEvidenceByUserAndLanguage(eq(userId), eq("en"), any()))
        .thenReturn(List.of(evidence));

    when(progress.findByUserIdAndReadingIds(eq(userId), any())).thenReturn(Map.of());
    when(readings.findAllPlatformReadingSummaries()).thenReturn(List.of(testSummary));
    when(collections.findActiveByKey("colombian-myths-legends"))
        .thenReturn(Optional.of(testCollection));
    when(collections.findReadings(eq("colombian-myths-legends"), eq("en"), any()))
        .thenReturn(new PageResult<>(List.of(testReading), 0, 10, 1));

    var pageRequest = new PageRequest(0, 10);
    var recItem = recommendUseCase.recommendPlatformReadings(pageRequest).content().getFirst();
    var colItem =
        collectionUseCase
            .listCollectionReadings("colombian-myths-legends", pageRequest)
            .content()
            .getFirst();

    assertThat(colItem.vocabularyFitPercentage()).isEqualTo(recItem.vocabularyFitPercentage());
    assertThat(colItem.classificationConfidencePercentage())
        .isEqualTo(recItem.classificationConfidencePercentage());
    assertThat(colItem.reasonCode()).isEqualTo(RecommendationReasonCode.PRACTICE_VOCABULARY);
    assertThat(recItem.reasonCode()).isEqualTo(RecommendationReasonCode.PRACTICE_VOCABULARY);
  }

  @Test
  @DisplayName(
      "Scenario E: Explicit NEW words present produce matching challenge metrics and BALANCED_CHALLENGE")
  void scenarioE_explicitNewWordsPresentProducesBalancedChallenge() {
    when(vocabulary.countClassifiedWordsByUserAndLanguage(userId, "en")).thenReturn(100L);

    // 50 unique: 35 known, 3 learning (6%), 10 explicit NEW (20% challenge), 2 unclassified
    var evidence = new ReadingLexicalEvidence(readingId, 300, 200, 20, 0, 50, 35, 3, 10, 0, 2);
    when(frequencyRepository.findLexicalEvidenceByUserAndLanguage(eq(userId), eq("en"), any()))
        .thenReturn(List.of(evidence));

    when(progress.findByUserIdAndReadingIds(eq(userId), any())).thenReturn(Map.of());
    when(readings.findAllPlatformReadingSummaries()).thenReturn(List.of(testSummary));
    when(collections.findActiveByKey("colombian-myths-legends"))
        .thenReturn(Optional.of(testCollection));
    when(collections.findReadings(eq("colombian-myths-legends"), eq("en"), any()))
        .thenReturn(new PageResult<>(List.of(testReading), 0, 10, 1));

    var pageRequest = new PageRequest(0, 10);
    var recItem = recommendUseCase.recommendPlatformReadings(pageRequest).content().getFirst();
    var colItem =
        collectionUseCase
            .listCollectionReadings("colombian-myths-legends", pageRequest)
            .content()
            .getFirst();

    assertThat(colItem.vocabularyFitPercentage()).isEqualTo(recItem.vocabularyFitPercentage());
    assertThat(colItem.classificationConfidencePercentage())
        .isEqualTo(recItem.classificationConfidencePercentage());
    assertThat(colItem.reasonCode()).isEqualTo(RecommendationReasonCode.BALANCED_CHALLENGE);
    assertThat(recItem.reasonCode()).isEqualTo(RecommendationReasonCode.BALANCED_CHALLENGE);
  }

  @Test
  @DisplayName(
      "Scenario F: IN_PROGRESS reading preserves V2 fit, confidence, and pedagogical reasonCode; excluded from Recommendations but kept in Collection")
  void scenarioF_inProgressPreservesPedagogicalReasonAndV2Fit() {
    when(vocabulary.countClassifiedWordsByUserAndLanguage(userId, "en")).thenReturn(100L);

    var evidence = new ReadingLexicalEvidence(readingId, 300, 250, 30, 0, 50, 40, 5, 2, 0, 3);
    when(frequencyRepository.findLexicalEvidenceByUserAndLanguage(eq(userId), eq("en"), any()))
        .thenReturn(List.of(evidence));

    // 1. BEFORE PROGRESS: unstarted candidate in Recommendations
    when(progress.findByUserIdAndReadingIds(eq(userId), any())).thenReturn(Map.of());
    when(readings.findAllPlatformReadingSummaries()).thenReturn(List.of(testSummary));

    var recPageBefore = recommendUseCase.recommendPlatformReadings(new PageRequest(0, 10));
    assertThat(recPageBefore.content()).hasSize(1);
    var unstartedRec = recPageBefore.content().getFirst();
    assertThat(unstartedRec.progressStatus()).isNull();
    var unstartedFit = unstartedRec.vocabularyFitPercentage();
    var unstartedConfidence = unstartedRec.classificationConfidencePercentage();
    var unstartedReason = unstartedRec.reasonCode();
    assertThat(unstartedReason).isEqualTo(RecommendationReasonCode.PRACTICE_VOCABULARY);

    // 2. AFTER PROGRESS: reading is marked IN_PROGRESS
    var inProgressEntity = ReadingProgress.inProgress(userId, readingId, LocalDateTime.now());
    when(progress.findByUserIdAndReadingIds(eq(userId), any()))
        .thenReturn(Map.of(readingId, inProgressEntity));

    // Recommendations filters it out because it has progress (unstarted only)
    var recPageAfter = recommendUseCase.recommendPlatformReadings(new PageRequest(0, 10));
    assertThat(recPageAfter.content()).isEmpty();

    // Collection PRESERVES it with IN_PROGRESS lifecycle status AND identical V2 personalization
    // metrics
    when(collections.findActiveByKey("colombian-myths-legends"))
        .thenReturn(Optional.of(testCollection));
    when(collections.findReadings(eq("colombian-myths-legends"), eq("en"), any()))
        .thenReturn(new PageResult<>(List.of(testReading), 0, 10, 1));

    var colPage =
        collectionUseCase.listCollectionReadings("colombian-myths-legends", new PageRequest(0, 10));

    assertThat(colPage.content()).hasSize(1);
    var colItem = colPage.content().getFirst();

    // Progress status reflects lifecycle
    assertThat(colItem.progressStatus()).isEqualTo(ReadingProgressStatus.IN_PROGRESS);

    // Fit, confidence, and pedagogical reasonCode are 100% IDENTICAL before and after progress
    assertThat(colItem.vocabularyFitPercentage()).isEqualTo(unstartedFit);
    assertThat(colItem.classificationConfidencePercentage()).isEqualTo(unstartedConfidence);
    assertThat(colItem.reasonCode()).isEqualTo(unstartedReason);
    assertThat(colItem.reasonCode()).isEqualTo(RecommendationReasonCode.PRACTICE_VOCABULARY);
  }

  @Test
  @DisplayName(
      "COMPLETED reading is preserved in Collection with evaluated canonical reason code (e.g. HIGH_VOCABULARY_MATCH)")
  void completedReadingPreservedInCollection() {
    when(vocabulary.countClassifiedWordsByUserAndLanguage(userId, "en")).thenReturn(100L);

    var evidence = new ReadingLexicalEvidence(readingId, 500, 480, 10, 0, 100, 90, 5, 0, 0, 5);
    when(frequencyRepository.findLexicalEvidenceByUserAndLanguage(eq(userId), eq("en"), any()))
        .thenReturn(List.of(evidence));

    var completedEntity =
        ReadingProgress.completed(userId, readingId, LocalDateTime.now(), LocalDateTime.now());
    when(progress.findByUserIdAndReadingIds(eq(userId), any()))
        .thenReturn(Map.of(readingId, completedEntity));

    when(collections.findActiveByKey("colombian-myths-legends"))
        .thenReturn(Optional.of(testCollection));
    when(collections.findReadings(eq("colombian-myths-legends"), eq("en"), any()))
        .thenReturn(new PageResult<>(List.of(testReading), 0, 10, 1));

    var colPage =
        collectionUseCase.listCollectionReadings("colombian-myths-legends", new PageRequest(0, 10));

    assertThat(colPage.content()).hasSize(1);
    var colItem = colPage.content().getFirst();
    assertThat(colItem.progressStatus()).isEqualTo(ReadingProgressStatus.COMPLETED);
    assertThat(colItem.reasonCode()).isEqualTo(RecommendationReasonCode.HIGH_VOCABULARY_MATCH);
  }
}
