package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.PedagogicalRecommendationScore;
import com.soap.soap.application.model.RecommendationShadowCandidate;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.port.in.RecommendPlatformReadingsPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.RecommendationShadowPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.PedagogicalRecommendationScorer;
import com.soap.soap.application.service.PlatformReadingRecommendationCalculator;
import com.soap.soap.application.service.RecommendationEvidenceV2Calculator;
import com.soap.soap.application.service.RecommendationReasonEvaluator;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.domain.model.VocabularyStatus;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RecommendPlatformReadingsUseCase implements RecommendPlatformReadingsPort {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(RecommendPlatformReadingsUseCase.class);
  private static final Comparator<ScoredReading> RANKING =
      Comparator.comparingInt(
              (ScoredReading scored) -> progressPriority(scored.reading().progressStatus()))
          .thenComparing(scored -> scored.score().score(), Comparator.reverseOrder())
          .thenComparing(
              scored -> scored.reading().classificationConfidencePercentage(),
              Comparator.reverseOrder())
          .thenComparing(scored -> scored.score().challengePercentage())
          .thenComparingInt(scored -> scored.reading().editorialLevel().ordinal())
          .thenComparing(
              scored -> scored.reading().createdAt(),
              Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(scored -> scored.reading().title())
          .thenComparing(scored -> scored.reading().readingId());

  private final UserRepositoryPort users;
  private final ReadingRepositoryPort readings;
  private final ReadingProgressRepositoryPort progress;
  private final UserVocabularyRepositoryPort vocabulary;
  private final TextWordProcessor wordProcessor;
  private final PlatformReadingRecommendationCalculator calculator;
  private final PedagogicalRecommendationScorer scorer;
  private final CurrentUserPort currentUser;
  private final RecommendationEvidenceV2Calculator v2EvidenceCalculator;
  private final RecommendationShadowPort shadow;
  private final RecommendationReasonEvaluator reasonEvaluator;

  @Autowired
  public RecommendPlatformReadingsUseCase(
      UserRepositoryPort users,
      ReadingRepositoryPort readings,
      ReadingProgressRepositoryPort progress,
      UserVocabularyRepositoryPort vocabulary,
      TextWordProcessor wordProcessor,
      PlatformReadingRecommendationCalculator calculator,
      PedagogicalRecommendationScorer scorer,
      CurrentUserPort currentUser,
      RecommendationEvidenceV2Calculator v2EvidenceCalculator,
      RecommendationShadowPort shadow,
      RecommendationReasonEvaluator reasonEvaluator) {
    this.users = users;
    this.readings = readings;
    this.progress = progress;
    this.vocabulary = vocabulary;
    this.wordProcessor = wordProcessor;
    this.calculator = calculator;
    this.scorer = scorer;
    this.currentUser = currentUser;
    this.v2EvidenceCalculator = v2EvidenceCalculator;
    this.shadow = shadow;
    this.reasonEvaluator = reasonEvaluator;
  }

  public RecommendPlatformReadingsUseCase(
      UserRepositoryPort users,
      ReadingRepositoryPort readings,
      ReadingProgressRepositoryPort progress,
      UserVocabularyRepositoryPort vocabulary,
      TextWordProcessor wordProcessor,
      PlatformReadingRecommendationCalculator calculator,
      PedagogicalRecommendationScorer scorer,
      CurrentUserPort currentUser,
      RecommendationEvidenceV2Calculator v2EvidenceCalculator,
      RecommendationShadowPort shadow) {
    this(
        users,
        readings,
        progress,
        vocabulary,
        wordProcessor,
        calculator,
        scorer,
        currentUser,
        v2EvidenceCalculator,
        shadow,
        new RecommendationReasonEvaluator());
  }

  public RecommendPlatformReadingsUseCase(
      UserRepositoryPort users,
      ReadingRepositoryPort readings,
      ReadingProgressRepositoryPort progress,
      UserVocabularyRepositoryPort vocabulary,
      TextWordProcessor wordProcessor,
      PlatformReadingRecommendationCalculator calculator,
      PedagogicalRecommendationScorer scorer,
      CurrentUserPort currentUser) {
    this(
        users,
        readings,
        progress,
        vocabulary,
        wordProcessor,
        calculator,
        scorer,
        currentUser,
        new RecommendationEvidenceV2Calculator(),
        ignored -> {},
        new RecommendationReasonEvaluator());
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<RecommendedPlatformReading> recommendPlatformReadings(PageRequest pageRequest) {
    var userId = currentUser.requireUserId();
    if (pageRequest == null) {
      throw new InvalidApplicationArgumentException("Page request must not be null");
    }
    if (!users.existsById(userId)) {
      throw new UserNotFoundException(userId);
    }

    var candidates =
        readings.findAllPlatformReadings().stream()
            .filter(
                reading -> reading.origin() == com.soap.soap.domain.model.ReadingOrigin.PLATFORM)
            .toList();
    var wordsByReading = new LinkedHashMap<UUID, Set<String>>();
    var tokensByReading = new LinkedHashMap<UUID, java.util.List<TextWordProcessor.Token>>();
    var wordsByLanguage = new HashMap<String, Set<String>>();
    for (var reading : candidates) {
      var tokens = wordProcessor.tokenize(reading.content());
      tokensByReading.put(reading.id(), tokens);
      var uniqueWords =
          tokens.stream()
              .map(TextWordProcessor.Token::normalizedValue)
              .collect(Collectors.toUnmodifiableSet());
      wordsByReading.put(reading.id(), uniqueWords);
      wordsByLanguage
          .computeIfAbsent(reading.language(), ignored -> new HashSet<>())
          .addAll(uniqueWords);
    }

    var statusesByLanguage = new HashMap<String, Map<String, VocabularyStatus>>();
    wordsByLanguage.forEach(
        (language, words) ->
            statusesByLanguage.put(
                language,
                words.isEmpty()
                    ? Map.of()
                    : vocabulary.findStatusesByNormalizedValues(userId, language, words)));

    var calculated =
        candidates.stream()
            .map(
                reading ->
                    calculator.calculate(
                        reading,
                        wordsByReading.get(reading.id()),
                        statusesByLanguage.getOrDefault(reading.language(), Map.of())))
            .toList();
    var progressByReading =
        progress.findByUserIdAndReadingIds(
            userId,
            calculated.stream()
                .map(RecommendedPlatformReading::readingId)
                .collect(Collectors.toSet()));
    var scored =
        calculated.stream()
            .map(
                reading -> {
                  var progressStatus =
                      progressByReading.containsKey(reading.readingId())
                          ? progressByReading.get(reading.readingId()).status()
                          : null;
                  var reasonCode =
                      reasonEvaluator.evaluate(
                          reading.vocabularyBreakdown(),
                          reading.classificationConfidencePercentage(),
                          progressStatus);
                  var enriched = withProgress(reading, progressStatus, reasonCode);
                  return new ScoredReading(
                      enriched,
                      scorer.score(
                          reading.vocabularyBreakdown(),
                          enriched.classificationConfidencePercentage(),
                          enriched.editorialLevel()));
                })
            .toList();
    var ranked = scored.stream().sorted(RANKING).toList();
    try {
      shadow.observe(
          () ->
              ranked.stream()
                  .map(
                      item ->
                          new RecommendationShadowCandidate(
                              item.reading(),
                              item.score(),
                              v2EvidenceCalculator.calculate(
                                  tokensByReading.get(item.reading().readingId()),
                                  statusesByLanguage.getOrDefault(
                                      item.reading().language(), Map.of()))))
                  .toList());
    } catch (RuntimeException exception) {
      LOGGER.warn(
          "recommendation.shadow.integration_failed errorType={}",
          exception.getClass().getSimpleName());
    }
    var from = Math.min((long) pageRequest.page() * pageRequest.size(), ranked.size());
    var to = Math.min(from + pageRequest.size(), ranked.size());
    var selected =
        ranked.subList((int) from, (int) to).stream().map(ScoredReading::reading).toList();
    return new PageResult<>(selected, pageRequest.page(), pageRequest.size(), ranked.size());
  }

  private static RecommendedPlatformReading withProgress(
      RecommendedPlatformReading reading,
      com.soap.soap.domain.model.ReadingProgressStatus progressStatus,
      com.soap.soap.domain.model.RecommendationReasonCode reasonCode) {
    return new RecommendedPlatformReading(
        reading.readingId(),
        reading.title(),
        reading.language(),
        reading.editorialLevel(),
        reading.category(),
        reading.createdAt(),
        reading.uniqueWords(),
        reading.knownWords(),
        reading.learningWords(),
        reading.explicitNewWords(),
        reading.ignoredWords(),
        reading.unclassifiedWords(),
        reading.vocabularyFitPercentage(),
        reading.classificationConfidencePercentage(),
        progressStatus,
        reading.coverKey(),
        reasonCode);
  }

  private static int progressPriority(com.soap.soap.domain.model.ReadingProgressStatus status) {
    if (status == null) {
      return 0;
    }
    return status == com.soap.soap.domain.model.ReadingProgressStatus.IN_PROGRESS ? 1 : 2;
  }

  private record ScoredReading(
      RecommendedPlatformReading reading, PedagogicalRecommendationScore score) {}
}
