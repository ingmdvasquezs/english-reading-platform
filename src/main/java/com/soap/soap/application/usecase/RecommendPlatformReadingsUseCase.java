package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.PlatformReadingSummary;
import com.soap.soap.application.model.ReadingLexicalEvidence;
import com.soap.soap.application.model.RecommendationScoreV2;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.port.in.RecommendPlatformReadingsPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.ReadingWordFrequencyRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.RecommendationReasonEvaluator;
import com.soap.soap.application.service.RecommendationScorerV2;
import com.soap.soap.domain.model.ReadingProgressStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RecommendPlatformReadingsUseCase implements RecommendPlatformReadingsPort {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(RecommendPlatformReadingsUseCase.class);

  private static final Comparator<ScoredReading> COLD_START_RANKING =
      Comparator.comparing(
              (ScoredReading scored) -> scored.score().finalScore(), Comparator.reverseOrder())
          .thenComparingInt(scored -> scored.reading().editorialLevel().ordinal())
          .thenComparing(
              scored -> scored.reading().createdAt(),
              Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(scored -> scored.reading().title())
          .thenComparing(scored -> scored.reading().readingId());

  private static final Comparator<ScoredReading> MATURE_RANKING =
      Comparator.comparing(
              (ScoredReading scored) -> scored.score().finalScore(), Comparator.reverseOrder())
          .thenComparing(
              scored -> scored.score().classificationConfidence(), Comparator.reverseOrder())
          .thenComparing(scored -> scored.score().uniqueChallenge())
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
  private final ReadingWordFrequencyRepositoryPort frequencyRepository;
  private final RecommendationScorerV2 scorer;
  private final RecommendationReasonEvaluator reasonEvaluator;
  private final CurrentUserPort currentUser;
  private final int minGlobalClassifiedWords;

  @Autowired
  public RecommendPlatformReadingsUseCase(
      UserRepositoryPort users,
      ReadingRepositoryPort readings,
      ReadingProgressRepositoryPort progress,
      UserVocabularyRepositoryPort vocabulary,
      ReadingWordFrequencyRepositoryPort frequencyRepository,
      RecommendationScorerV2 scorer,
      RecommendationReasonEvaluator reasonEvaluator,
      CurrentUserPort currentUser,
      @Value("${recommendation.v2.min-global-classified-words:30}") int minGlobalClassifiedWords) {
    this.users = users;
    this.readings = readings;
    this.progress = progress;
    this.vocabulary = vocabulary;
    this.frequencyRepository = frequencyRepository;
    this.scorer = scorer;
    this.reasonEvaluator = reasonEvaluator;
    this.currentUser = currentUser;
    this.minGlobalClassifiedWords = minGlobalClassifiedWords;
  }

  public RecommendPlatformReadingsUseCase(
      UserRepositoryPort users,
      ReadingRepositoryPort readings,
      ReadingProgressRepositoryPort progress,
      UserVocabularyRepositoryPort vocabulary,
      ReadingWordFrequencyRepositoryPort frequencyRepository,
      RecommendationScorerV2 scorer,
      RecommendationReasonEvaluator reasonEvaluator,
      CurrentUserPort currentUser) {
    this(
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

    // 1. Load candidate platform summaries without content TEXT
    List<PlatformReadingSummary> candidates = readings.findAllPlatformReadingSummaries();
    if (candidates.isEmpty()) {
      return new PageResult<>(List.of(), pageRequest.page(), pageRequest.size(), 0);
    }

    // 2. Batch query reading progress for candidate platform readings
    List<UUID> candidateIds = candidates.stream().map(PlatformReadingSummary::id).toList();
    var progressByReading = progress.findByUserIdAndReadingIds(userId, new HashSet<>(candidateIds));

    // 3. Exclude any candidate that has a ReadingProgress (only keep NOT_STARTED)
    List<PlatformReadingSummary> unstartedCandidates =
        candidates.stream().filter(c -> !progressByReading.containsKey(c.id())).toList();

    if (unstartedCandidates.isEmpty()) {
      return new PageResult<>(List.of(), pageRequest.page(), pageRequest.size(), 0);
    }

    // 4. Group NOT_STARTED candidates by language to batch query lexical evidence and cold-start
    // state
    Map<String, List<UUID>> unstartedByLanguage =
        unstartedCandidates.stream()
            .collect(
                Collectors.groupingBy(
                    PlatformReadingSummary::language,
                    LinkedHashMap::new,
                    Collectors.mapping(PlatformReadingSummary::id, Collectors.toList())));

    Map<UUID, ReadingLexicalEvidence> evidenceByReadingId = new HashMap<>();
    Map<String, Boolean> coldStartByLanguage = new HashMap<>();

    for (var entry : unstartedByLanguage.entrySet()) {
      String language = entry.getKey();
      List<UUID> ids = entry.getValue();

      long globalClassified = vocabulary.countClassifiedWordsByUserAndLanguage(userId, language);
      coldStartByLanguage.put(language, globalClassified < minGlobalClassifiedWords);

      List<ReadingLexicalEvidence> evidenceList =
          frequencyRepository.findLexicalEvidenceByUserAndLanguage(userId, language, ids);
      for (var evidence : evidenceList) {
        evidenceByReadingId.put(evidence.readingId(), evidence);
      }
    }

    // 5. Score and filter unstarted candidates
    boolean hasAnyColdStart = coldStartByLanguage.values().stream().anyMatch(Boolean::booleanValue);
    List<ScoredReading> scored = new ArrayList<>(unstartedCandidates.size());
    for (PlatformReadingSummary candidate : unstartedCandidates) {
      ReadingLexicalEvidence evidence = evidenceByReadingId.get(candidate.id());
      if (evidence == null) {
        LOGGER.warn("recommendation.missing_lexical_profile readingId={}", candidate.id());
        continue;
      }

      ReadingProgressStatus progressStatus = null;

      boolean candidateColdStart = coldStartByLanguage.getOrDefault(candidate.language(), true);
      RecommendationScoreV2 score =
          scorer.score(evidence, candidate.editorialLevel(), candidateColdStart);

      var reasonCode =
          reasonEvaluator.evaluate(
              progressStatus,
              candidateColdStart,
              score.insufficientEvidence(),
              score.classificationConfidence(),
              score.knownTokenCoverage(),
              score.learningUniqueRatio(),
              score.uniqueChallenge());

      RecommendedPlatformReading reading =
          new RecommendedPlatformReading(
              candidate.id(),
              candidate.title(),
              candidate.language(),
              candidate.editorialLevel(),
              candidate.category(),
              candidate.createdAt(),
              evidence.totalUnique(),
              evidence.knownUnique(),
              evidence.learningUnique(),
              evidence.explicitNewUnique(),
              evidence.ignoredUnique(),
              evidence.unclassifiedUnique(),
              score.knownComfort(),
              score.classificationConfidence(),
              progressStatus,
              candidate.coverKey(),
              reasonCode);

      scored.add(new ScoredReading(reading, score));
    }

    // 6. Rank deterministically (cold start vs mature)
    Comparator<ScoredReading> comparator = hasAnyColdStart ? COLD_START_RANKING : MATURE_RANKING;
    List<ScoredReading> ranked = scored.stream().sorted(comparator).toList();

    // 7. Paginate
    var from = Math.min((long) pageRequest.page() * pageRequest.size(), ranked.size());
    var to = Math.min(from + pageRequest.size(), ranked.size());
    var selected =
        ranked.subList((int) from, (int) to).stream().map(ScoredReading::reading).toList();
    return new PageResult<>(selected, pageRequest.page(), pageRequest.size(), ranked.size());
  }

  private record ScoredReading(RecommendedPlatformReading reading, RecommendationScoreV2 score) {}
}
